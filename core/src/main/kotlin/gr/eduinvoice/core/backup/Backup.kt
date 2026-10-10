package gr.eduinvoice.core.backup

import gr.eduinvoice.core.model.AppSettings
import gr.eduinvoice.core.model.BusinessProfile
import gr.eduinvoice.core.model.Invoice
import gr.eduinvoice.core.model.Lesson
import gr.eduinvoice.core.model.Money
import gr.eduinvoice.core.model.Rate
import gr.eduinvoice.core.model.RateType
import gr.eduinvoice.core.model.Student
import gr.eduinvoice.core.model.StudentGroup
import gr.eduinvoice.core.model.ThemeMode
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
    const val FORMAT = "eduinvoice-backup"
    const val VERSION = 1L

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

    fun decode(text: String): BackupSnapshot {
        val root = Json.parse(text) as? Map<*, *> ?: throw BackupFormatException("Not an EduInvoice backup")
        if (root["format"] != FORMAT) throw BackupFormatException("Not an EduInvoice backup")
        val version = root["version"] as? Long ?: throw BackupFormatException("Missing backup version")
        if (version > VERSION) throw BackupFormatException("This backup was made by a newer app version")
        return BackupSnapshot(
            exportedAt = root.string("exportedAt"),
            settings = root.obj("settings").toSettings(),
            students = root.objects("students").map { it.toStudent() },
            groups = root.objects("groups").map { it.toGroup() },
            lessons = root.objects("lessons").map { it.toLesson() },
            invoices = root.objects("invoices").map { it.toInvoice() },
        )
    }

    private fun AppSettings.toJson() = mapOf(
        "businessName" to business.name,
        "businessAddress" to business.address,
        "taxId" to business.taxId,
        "phone" to business.phone,
        "email" to business.email,
        "iban" to business.iban,
        "currencyCode" to currencyCode,
        "defaultLessonMinutes" to defaultLessonMinutes.toLong(),
        "themeMode" to themeMode.name,
        "invoiceFooter" to invoiceFooter,
    )

    private fun Map<*, *>.toSettings() = AppSettings(
        business = BusinessProfile(
            name = string("businessName"),
            address = string("businessAddress"),
            taxId = string("taxId"),
            phone = string("phone"),
            email = string("email"),
            iban = string("iban"),
        ),
        currencyCode = string("currencyCode"),
        defaultLessonMinutes = int("defaultLessonMinutes"),
        themeMode = enum<ThemeMode>("themeMode"),
        invoiceFooter = string("invoiceFooter"),
    )

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
    )

    private fun Map<*, *>.toInvoice() = Invoice(
        id = long("id"),
        number = string("number"),
        studentId = long("studentId"),
        issueDate = date("issueDate"),
        total = Money(long("totalCents")),
        notes = string("notes"),
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
