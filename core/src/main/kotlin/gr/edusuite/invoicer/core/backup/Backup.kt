package gr.edusuite.invoicer.core.backup

import gr.edusuite.invoicer.core.invoice.InvoiceDocumentCodec
import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.BusinessProfile
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.model.Lesson
import gr.edusuite.invoicer.core.model.Money
import gr.edusuite.invoicer.core.model.Rate
import gr.edusuite.invoicer.core.model.RateType
import gr.edusuite.invoicer.core.model.ReminderSettings
import gr.edusuite.invoicer.core.model.Student
import gr.edusuite.invoicer.core.model.StudentGroup
import gr.edusuite.invoicer.core.model.ThemeMode
import gr.edusuite.invoicer.core.usecase.MAX_LESSON_MINUTES
import gr.edusuite.invoicer.core.usecase.SettingsRules
import gr.edusuite.invoicer.core.usecase.isValidDuration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

class BackupFormatException(message: String) : Exception(message)

/** Everything needed to restore the app on another device. */
data class BackupSnapshot(
    val exportedAt: String,
    val settings: AppSettings,
    val students: List<Student>,
    val groups: List<StudentGroup>,
    val lessons: List<Lesson>,
    val invoices: List<Invoice>,
)

/** Reads and replaces all stored data. Implementations restore atomically. */
interface BackupRepository {
    suspend fun readAll(): BackupSnapshot

    suspend fun replaceAll(snapshot: BackupSnapshot)
}

/** Versioned JSON format for [BackupSnapshot]. */
object BackupCodec {
    const val FORMAT = "course-invoicer-backup"

    /** Written by the app before it was renamed Course Invoicer (D17); still imported. */
    val LEGACY_FORMATS = setOf("eduinvoice-backup")

    /**
     * 2: invoices gained `voidedOn`. 3: invoices gained `document`, the content as issued.
     * Older files are still accepted.
     */
    const val VERSION = 3L

    fun encode(snapshot: BackupSnapshot): String = Json.write(
        mapOf(
            "format" to FORMAT,
            "version" to VERSION,
            "exportedAt" to snapshot.exportedAt,
            "settings" to snapshot.settings.toJson(),
            "students" to snapshot.students.map { it.toJson() },
            "groups" to snapshot.groups.map { it.toJson() },
            "lessons" to snapshot.lessons.map { it.toJson() },
            "invoices" to snapshot.invoices.map { it.toJson() },
        ),
    )

    /** Parses and validates a backup. A file that breaks a business rule is rejected as a whole (R11). */
    fun decode(text: String): BackupSnapshot {
        val root = Json.parse(text) as? Map<*, *> ?: throw BackupFormatException("Not a Course Invoicer backup")
        val format = root["format"]
        if (format != FORMAT && format !in LEGACY_FORMATS) throw BackupFormatException("Not a Course Invoicer backup")
        val version = root["version"] as? Long ?: throw BackupFormatException("Missing backup version")
        if (version > VERSION) throw BackupFormatException("This backup was made by a newer app version")
        return BackupSnapshot(
            exportedAt = root.string("exportedAt"),
            settings = root.obj("settings").toSettings(),
            students = root.objects("students").map { it.toStudent() },
            groups = root.objects("groups").map { it.toGroup() },
            lessons = root.objects("lessons").map { it.toLesson() },
            invoices = root.objects("invoices").map { it.toInvoice() },
        ).also { it.validate() }
    }

    /** The rules the app enforces when data is entered, checked again for data that comes from a file. */
    private fun BackupSnapshot.validate() {
        fun reject(message: String): Nothing = throw BackupFormatException(message)

        if (!SettingsRules.isValidCurrency(settings.currencyCode)) {
            reject("Unknown currency '${settings.currencyCode}'")
        }
        if (!SettingsRules.isValidLessonMinutes(settings.defaultLessonMinutes)) {
            reject("Default lesson length must be between 1 and $MAX_LESSON_MINUTES minutes")
        }

        val studentIds = students.map { it.id }.toSet()
        if (studentIds.size != students.size) reject("Two students share an id")
        students.forEach {
            if (it.id <= 0 || it.firstName.isBlank() || it.rate.amount.cents < 0) reject("Student ${it.id} is invalid")
        }

        val groupIds = groups.map { it.id }.toSet()
        if (groupIds.size != groups.size) reject("Two groups share an id")
        groups.forEach {
            if (it.id <= 0 || it.name.isBlank() || it.rate.amount.cents < 0 || !studentIds.containsAll(it.memberIds)) {
                reject("Group ${it.id} is invalid")
            }
        }

        val invoicesById = invoices.associateBy { it.id }
        if (invoicesById.size != invoices.size) reject("Two invoices share an id")
        if (invoices.map { it.number }.toSet().size != invoices.size) reject("Two invoices share a number")
        invoices.forEach {
            if (it.id <= 0 || it.studentId !in studentIds) reject("Invoice ${it.number} is invalid")
        }

        if (lessons.map { it.id }.toSet().size != lessons.size) reject("Two lessons share an id")
        lessons.forEach { lesson ->
            val valid = lesson.id > 0 &&
                lesson.studentId in studentIds &&
                (lesson.groupId == null || lesson.groupId in groupIds) &&
                isValidDuration(lesson.durationMinutes) &&
                lesson.fee.cents >= 0
            if (!valid) reject("Lesson ${lesson.id} is invalid")
            val invoice = lesson.invoiceId?.let {
                invoicesById[it] ?: reject("Lesson ${lesson.id} is on a missing invoice")
            }
            // Mirrors CreateInvoice and VoidInvoice: one student per invoice, no absences, void invoices hold nothing.
            if (invoice != null && (invoice.isVoid || invoice.studentId != lesson.studentId || lesson.absent)) {
                reject("Lesson ${lesson.id} cannot be on invoice ${invoice.number}")
            }
        }
    }

    private fun AppSettings.toJson() = mapOf(
        "businessName" to business.name,
        "businessAddress" to business.address,
        "phone" to business.phone,
        "email" to business.email,
        "iban" to business.iban,
        "currencyCode" to currencyCode,
        "defaultLessonMinutes" to defaultLessonMinutes.toLong(),
        "themeMode" to themeMode.name,
        "invoiceFooter" to invoiceFooter,
        "remindersEnabled" to reminders.enabled,
        "remindEarlyDecember" to reminders.earlyDecember,
        "remindEarlyJune" to reminders.earlyJune,
        "remindEarlyEaster" to reminders.earlyEaster,
    )

    // Backups made before 2.0.0 also hold "taxId"; the field was removed and is ignored on purpose.
    private fun Map<*, *>.toSettings() = AppSettings(
        business = BusinessProfile(
            name = string("businessName"),
            address = string("businessAddress"),
            phone = string("phone"),
            email = string("email"),
            iban = string("iban"),
        ),
        currencyCode = string("currencyCode"),
        defaultLessonMinutes = int("defaultLessonMinutes"),
        themeMode = enum<ThemeMode>("themeMode"),
        invoiceFooter = string("invoiceFooter"),
        reminders = toReminderSettings(),
    )

    // The reminder switches arrived in 2.0.0 without a format version of their own: files without them
    // restore the defaults.
    private fun Map<*, *>.toReminderSettings(): ReminderSettings {
        val defaults = ReminderSettings()
        fun flag(key: String, default: Boolean) = if (containsKey(key)) boolean(key) else default
        return ReminderSettings(
            enabled = flag("remindersEnabled", defaults.enabled),
            earlyDecember = flag("remindEarlyDecember", defaults.earlyDecember),
            earlyJune = flag("remindEarlyJune", defaults.earlyJune),
            earlyEaster = flag("remindEarlyEaster", defaults.earlyEaster),
        )
    }

    private fun Student.toJson() = mapOf(
        "id" to id,
        "firstName" to firstName,
        "lastName" to lastName,
        "className" to className,
        "guardianName" to guardianName,
        "phone" to phone,
        "email" to email,
        "rateCents" to rate.amount.cents,
        "rateType" to rate.type.name,
        "notes" to notes,
        "archived" to archived,
    )

    private fun Map<*, *>.toStudent() = Student(
        id = long("id"),
        firstName = string("firstName"),
        lastName = string("lastName"),
        className = string("className"),
        guardianName = string("guardianName"),
        phone = string("phone"),
        email = string("email"),
        rate = Rate(Money(long("rateCents")), enum("rateType")),
        notes = string("notes"),
        archived = boolean("archived"),
    )

    private fun StudentGroup.toJson() = mapOf(
        "id" to id,
        "name" to name,
        "className" to className,
        "rateCents" to rate.amount.cents,
        "rateType" to rate.type.name,
        "memberIds" to memberIds.sorted(),
        "archived" to archived,
    )

    private fun Map<*, *>.toGroup() = StudentGroup(
        id = long("id"),
        name = string("name"),
        className = string("className"),
        rate = Rate(Money(long("rateCents")), enum<RateType>("rateType")),
        memberIds = list("memberIds").map { it as? Long ?: throw BackupFormatException("Bad member id") }.toSet(),
        archived = boolean("archived"),
    )

    private fun Lesson.toJson() = mapOf(
        "id" to id,
        "studentId" to studentId,
        "groupId" to groupId,
        "date" to date.toString(),
        "startTime" to startTime.toString(),
        "durationMinutes" to durationMinutes.toLong(),
        "feeCents" to fee.cents,
        "absent" to absent,
        "notes" to notes,
        "paidOn" to paidOn?.toString(),
        "invoiceId" to invoiceId,
    )

    private fun Map<*, *>.toLesson() = Lesson(
        id = long("id"),
        studentId = long("studentId"),
        groupId = longOrNull("groupId"),
        date = date("date"),
        startTime = parse("startTime") { LocalTime.parse(it) },
        durationMinutes = int("durationMinutes"),
        fee = Money(long("feeCents")),
        absent = boolean("absent"),
        notes = string("notes"),
        paidOn = dateOrNull("paidOn"),
        invoiceId = longOrNull("invoiceId"),
    )

    private fun Invoice.toJson() = mapOf(
        "id" to id,
        "number" to number,
        "studentId" to studentId,
        "issueDate" to issueDate.toString(),
        "totalCents" to total.cents,
        "notes" to notes,
        "voidedOn" to voidedOn?.toString(),
        "document" to document?.let(InvoiceDocumentCodec::toJson),
    )

    private fun Map<*, *>.toInvoice() = Invoice(
        id = long("id"),
        number = string("number"),
        studentId = long("studentId"),
        issueDate = date("issueDate"),
        total = Money(long("totalCents")),
        notes = string("notes"),
        voidedOn = if (containsKey("voidedOn")) dateOrNull("voidedOn") else null,
        document = if (containsKey("document")) documentOrNull("document") else null,
    )

    private fun Map<*, *>.field(key: String): Any? =
        if (containsKey(key)) get(key) else throw BackupFormatException("Missing field '$key'")

    private fun Map<*, *>.string(key: String) = field(key) as? String ?: invalid(key)

    private fun Map<*, *>.stringOrNull(key: String) = field(key)?.let { it as? String ?: invalid(key) }

    private fun Map<*, *>.long(key: String) = field(key) as? Long ?: invalid(key)

    private fun Map<*, *>.longOrNull(key: String) = field(key)?.let { it as? Long ?: invalid(key) }

    private fun Map<*, *>.int(key: String): Int {
        val value = long(key)
        return if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else invalid(key)
    }

    private fun Map<*, *>.boolean(key: String) = field(key) as? Boolean ?: invalid(key)

    private fun Map<*, *>.obj(key: String) = field(key) as? Map<*, *> ?: invalid(key)

    private fun Map<*, *>.list(key: String) = field(key) as? List<*> ?: invalid(key)

    private fun Map<*, *>.objects(key: String) = list(key).map { it as? Map<*, *> ?: invalid(key) }

    private fun Map<*, *>.documentOrNull(key: String) =
        field(key)?.let { InvoiceDocumentCodec.fromJson(it as? Map<*, *> ?: invalid(key)) }

    private fun Map<*, *>.date(key: String) = parse(key) { LocalDate.parse(it) }

    private fun Map<*, *>.dateOrNull(key: String) = if (field(key) == null) null else date(key)

    private inline fun <reified E : Enum<E>> Map<*, *>.enum(key: String): E {
        val name = string(key)
        return enumValues<E>().firstOrNull { it.name == name } ?: invalid(key)
    }

    private fun <T> Map<*, *>.parse(key: String, parser: (String) -> T): T = try {
        parser(string(key))
    } catch (_: DateTimeParseException) {
        invalid(key)
    }

    private fun invalid(key: String): Nothing = throw BackupFormatException("Invalid value for '$key'")
}
