package gr.edusuite.invoicer.core.privacy

import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.BusinessProfile

enum class NoticeLanguage { EN, EL }

/**
 * The privacy notice for parents (GDPR Art. 13), filled in from the tutor's [details]. The text is fixed
 * (docs/superpowers/specs/2026-10-18-edusuite-home-settings-privacy-design.md, *Fixed copy*): the tutor is
 * the controller, the only legal basis is the lessons agreement (Art. 6(1)(b)) because statements are
 * informational, not tax documents (D20), and data is erased within 12 months of the last lesson once
 * everything is paid. Blank contact details are left out with their commas. A multi-line address
 * is joined into one line.
 */
fun privacyNotice(details: BusinessProfile, language: NoticeLanguage): String {
    val name = details.name.trim()
    val controller = listOf(
        name,
        details.address.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", "),
        details.phone.trim(),
        details.email.trim(),
    ).filter { it.isNotEmpty() }.joinToString(", ")
    return when (language) {
        NoticeLanguage.EN -> english(name, controller)
        NoticeLanguage.EL -> greek(name, controller)
    }
}

/**
 * The notice for the tutor's [settings], or null while their name is blank: a notice must say who the
 * controller is. The address is included only when it is printed on statements (R14), so a home address
 * the tutor keeps private stays out of it too.
 */
fun privacyNoticeFor(settings: AppSettings, language: NoticeLanguage): String? {
    val business = settings.business
    if (business.name.isBlank()) return null
    val details = business.copy(address = business.address.takeIf { settings.showAddressOnStatements }.orEmpty())
    return privacyNotice(details, language)
}

private fun english(name: String, controller: String) = """
    Privacy notice: lessons with $name

    Who is responsible
    $controller is the controller of the personal data below.

    What I keep
    The student's name and class, the guardian's name, the phone number and email you give me, the dates, length and fees of lessons, payments, and the statements I send you.

    Why, and on what legal basis
    To arrange lessons and to let you know what they cost, which is necessary for our agreement (GDPR Art. 6(1)(b)). The statements I send are for your information only; they are not tax documents.

    Who receives it
    No one else. The data stays on my own device and is not sent to any online service or outside the EU.

    How long
    While lessons continue. After the last lesson, until everything is paid and for no more than 12 months; then it is erased. Statements I have sent then keep only their number, dates and amounts, without your name.

    Your rights
    You may ask me for access to your data, its rectification or erasure, restriction of processing, to object to processing, and for a copy in a portable format (Art. 15–21), at the contact details above.

    Complaints
    You may lodge a complaint with the Hellenic Data Protection Authority, 1-3 Kifisias Ave., 115 23 Athens, tel. +30 210 6475600, www.dpa.gr.

    Names and lesson details are needed to arrange and bill lessons; phone, email and the guardian's name are optional. No decisions are made automatically and there is no profiling.
""".trimIndent()

private fun greek(name: String, controller: String) = """
    Ενημέρωση για την προστασία δεδομένων: μαθήματα με $name

    Υπεύθυνος επεξεργασίας
    $controller είναι ο υπεύθυνος επεξεργασίας των παρακάτω δεδομένων προσωπικού χαρακτήρα.

    Ποια δεδομένα τηρώ
    Το ονοματεπώνυμο και την τάξη του μαθητή, το όνομα του κηδεμόνα, το τηλέφωνο και το email που μου δίνετε, τις ημερομηνίες, τη διάρκεια και την αμοιβή των μαθημάτων, τις πληρωμές και τις καταστάσεις που σας στέλνω.

    Σκοπός και νομική βάση
    Η οργάνωση των μαθημάτων και η ενημέρωσή σας για το κόστος τους, που είναι απαραίτητη για την εκτέλεση της συμφωνίας μας (άρθρο 6 παρ. 1 στοιχ. β ΓΚΠΔ). Οι καταστάσεις που σας στέλνω είναι μόνο ενημερωτικές· δεν αποτελούν φορολογικά παραστατικά.

    Αποδέκτες
    Κανείς άλλος. Τα δεδομένα βρίσκονται μόνο στη δική μου συσκευή και δεν αποστέλλονται σε διαδικτυακή υπηρεσία ούτε εκτός ΕΕ.

    Χρονικό διάστημα αποθήκευσης
    Όσο συνεχίζονται τα μαθήματα. Μετά το τελευταίο μάθημα, έως ότου εξοφληθούν όλα και όχι περισσότερο από 12 μήνες· στη συνέχεια διαγράφονται. Οι καταστάσεις που σας έχω στείλει διατηρούν τότε μόνο τον αριθμό, τις ημερομηνίες και τα ποσά, χωρίς το όνομά σας.

    Τα δικαιώματά σας
    Έχετε δικαίωμα πρόσβασης στα δεδομένα σας, διόρθωσης ή διαγραφής τους, περιορισμού της επεξεργασίας, εναντίωσης στην επεξεργασία και φορητότητας των δεδομένων (άρθρα 15–21), στα παραπάνω στοιχεία επικοινωνίας.

    Καταγγελία
    Έχετε δικαίωμα υποβολής καταγγελίας στην Αρχή Προστασίας Δεδομένων Προσωπικού Χαρακτήρα, Λεωφ. Κηφισίας 1-3, 115 23 Αθήνα, τηλ. 210 6475600, www.dpa.gr.

    Το ονοματεπώνυμο και τα στοιχεία των μαθημάτων χρειάζονται για την οργάνωση και τη χρέωση των μαθημάτων· το τηλέφωνο, το email και το όνομα του κηδεμόνα είναι προαιρετικά. Δεν λαμβάνονται αυτοματοποιημένες αποφάσεις και δεν γίνεται κατάρτιση προφίλ.
""".trimIndent()
