package gr.eduinvoice.core.backup

import java.util.Locale

/**
 * Minimal JSON reader/writer for backups, so :core needs no serialization library.
 *
 * Values map to Kotlin types: object → Map<String, Any?>, array → List<Any?>, string → String,
 * integer → Long, other numbers → Double, true/false → Boolean, null → null. Nesting is limited to
 * [MAX_DEPTH] levels, so a hostile file fails with a [BackupFormatException] instead of a stack overflow.
 */
internal object Json {
    /** Real backups nest about six levels deep. */
    const val MAX_DEPTH = 64

    fun write(value: Any?): String = StringBuilder().also { it.appendValue(value, indent = 0) }.toString()

    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw parser.error("Unexpected trailing content")
        return value
    }

    private fun StringBuilder.appendValue(value: Any?, indent: Int) {
        when (value) {
            null -> append("null")
            is String -> appendString(value)
            is Boolean, is Int, is Long -> append(value.toString())
            is Double -> {
                require(value.isFinite()) { "JSON cannot represent $value" }
                append(value.toString())
            }
            is Map<*, *> -> appendContainer('{', '}', value.entries, indent) { entry, level ->
                appendString(entry.key as String)
                append(": ")
                appendValue(entry.value, level)
            }
            is List<*> -> appendContainer('[', ']', value, indent) { item, level -> appendValue(item, level) }
            else -> throw IllegalArgumentException("Unsupported JSON value: ${value::class}")
        }
    }

    private inline fun <T> StringBuilder.appendContainer(
        open: Char,
        close: Char,
        items: Collection<T>,
        indent: Int,
        appendItem: StringBuilder.(T, Int) -> Unit,
    ) {
        append(open)
        if (items.isEmpty()) {
            append(close)
            return
        }
        items.forEachIndexed { index, item ->
            if (index > 0) append(',')
            append('\n').append("  ".repeat(indent + 1))
            appendItem(item, indent + 1)
        }
        append('\n').append("  ".repeat(indent)).append(close)
    }

    private fun StringBuilder.appendString(value: String) {
        append('"')
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append(String.format(Locale.ROOT, "\\u%04x", char.code)) else append(char)
            }
        }
        append('"')
    }

    private class Parser(private val text: String) {
        private var pos = 0
        private var depth = 0

        fun atEnd() = pos >= text.length

        fun error(message: String) = BackupFormatException("$message at position $pos")

        fun skipWhitespace() {
            while (!atEnd() && text[pos] in " \t\r\n") pos++
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw error("Unexpected end of input")
            return when (val char = text[pos]) {
                '{' -> nested { readObject() }
                '[' -> nested { readArray() }
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (char == '-' || char.isDigit()) readNumber() else throw error("Unexpected '$char'")
            }
        }

        private inline fun <T> nested(read: () -> T): T {
            if (++depth > MAX_DEPTH) throw error("Nested more than $MAX_DEPTH levels deep")
            try {
                return read()
            } finally {
                depth--
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw error("Expected a field name")
                val key = readString()
                skipWhitespace()
                expect(':')
                result[key] = readValue()
                skipWhitespace()
                when (next()) {
                    ',' -> continue
                    '}' -> return result
                    else -> throw error("Expected ',' or '}'")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return result
            }
            while (true) {
                result += readValue()
                skipWhitespace()
                when (next()) {
                    ',' -> continue
                    ']' -> return result
                    else -> throw error("Expected ',' or ']'")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val result = StringBuilder()
            while (true) {
                val char = next()
                when {
                    char == '"' -> return result.toString()
                    char == '\\' -> when (val escape = next()) {
                        '"', '\\', '/' -> result.append(escape)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) throw error("Incomplete unicode escape")
                            val code = text.substring(pos, pos + 4).toIntOrNull(16) ?: throw error("Bad unicode escape")
                            result.append(code.toChar())
                            pos += 4
                        }
                        else -> throw error("Bad escape '\\$escape'")
                    }
                    char < ' ' -> throw error("Control character in string")
                    else -> result.append(char)
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            if (peek() == '-') pos++
            while (!atEnd() && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
            val token = text.substring(start, pos)
            val isInteger = token.none { it in ".eE" }
            return (if (isInteger) token.toLongOrNull() else token.toDoubleOrNull())
                ?: throw error("Bad number '$token'")
        }

        private fun readLiteral(word: String, value: Any?): Any? {
            if (!text.startsWith(word, pos)) throw error("Unexpected token")
            pos += word.length
            return value
        }

        private fun peek(): Char? = if (atEnd()) null else text[pos]

        private fun next(): Char {
            if (atEnd()) throw error("Unexpected end of input")
            return text[pos++]
        }

        private fun expect(char: Char) {
            if (next() != char) {
                pos--
                throw error("Expected '$char'")
            }
        }
    }
}
