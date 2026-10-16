package gr.eduinvoice.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonTest {
    @Test
    fun `parses nested values`() {
        val parsed = Json.parse(""" { "a": [1, -2, 3.5, true, false, null], "b": {"c": "é\n\"x\"\/"} } """)
        assertEquals(
            mapOf("a" to listOf(1L, -2L, 3.5, true, false, null), "b" to mapOf("c" to "é\n\"x\"/")),
            parsed,
        )
    }

    @Test
    fun `writes and reads back strings with special characters`() {
        val value = mapOf("text" to "quote \" backslash \\ newline \n tab \t bell \u0007 Ελληνικά", "empty" to emptyList<Any>())
        assertEquals(value, Json.parse(Json.write(value)))
    }

    @Test
    fun `rejects malformed input`() {
        listOf("", "{", "[1,]", "{\"a\" 1}", "\"open", "tru", "1 2", "{\"a\":01x}").forEach { text ->
            assertThrows("'$text'", BackupFormatException::class.java) { Json.parse(text) }
        }
    }

    @Test
    fun `limits nesting so a hostile file cannot overflow the stack`() {
        val deep = "[".repeat(100_000) + "]".repeat(100_000)
        val error = assertThrows(BackupFormatException::class.java) { Json.parse(deep) }
        assertEquals(true, error.message!!.contains("levels deep"))

        val allowed = "[".repeat(Json.MAX_DEPTH) + "]".repeat(Json.MAX_DEPTH)
        Json.parse(allowed)
    }
}
