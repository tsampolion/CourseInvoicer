package gr.edusuite.invoicer.core.privacy

import gr.edusuite.invoicer.core.model.AppSettings
import gr.edusuite.invoicer.core.model.BusinessProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyNoticeTest {
    private val full = BusinessProfile(
        name = "Dimitris Tutoring",
        address = "1 Main St\nAthens",
        phone = "2100000000",
        email = "d@example.com",
        iban = "GR16 0110",
    )

    @Test fun noticeNamesController() {
        val en = privacyNotice(full, NoticeLanguage.EN)
        assertTrue(en.startsWith("Privacy notice: lessons with Dimitris Tutoring"))
        assertTrue(en.contains("Dimitris Tutoring, 1 Main St, Athens, 2100000000, d@example.com is the controller"))
        val el = privacyNotice(full, NoticeLanguage.EL)
        assertTrue(el.contains("Dimitris Tutoring, 1 Main St, Athens, 2100000000, d@example.com είναι ο υπεύθυνος επεξεργασίας"))
    }

    @Test fun noticeSkipsBlankFields() {
        NoticeLanguage.entries.forEach { language ->
            val text = privacyNotice(BusinessProfile(name = "Maria"), language)
            assertFalse(text, text.contains(", ,"))
            assertFalse(text, text.contains("{"))
            assertFalse(text, text.contains("Maria,"))
            assertFalse("no blank line run", text.contains("\n\n\n"))
        }
    }

    @Test fun noticeListsAllArt13Items() {
        val en = privacyNotice(full, NoticeLanguage.EN)
        listOf("Art. 6(1)(b)", "Art. 15–21", "Hellenic Data Protection Authority", "12 months", "not tax documents")
            .forEach { assertTrue(it, en.contains(it)) }
        val el = privacyNotice(full, NoticeLanguage.EL)
        listOf("άρθρο 6 παρ. 1 στοιχ. β", "Αρχή Προστασίας Δεδομένων Προσωπικού Χαρακτήρα", "12 μήνες", "άρθρα 15–21")
            .forEach { assertTrue(it, el.contains(it)) }
    }

    @Test fun noticeClaimsNoLegalObligation() {
        val en = privacyNotice(full, NoticeLanguage.EN)
        assertFalse(en.contains("6(1)(c)"))
        assertFalse(en.contains("tax authority", ignoreCase = true))
        assertFalse(privacyNotice(full, NoticeLanguage.EL).contains("στοιχ. γ"))
    }

    @Test fun noticeNeedsANameAndHidesAPrivateAddress() {
        assertNull(privacyNoticeFor(AppSettings(), NoticeLanguage.EN))
        val private = privacyNoticeFor(AppSettings(business = full), NoticeLanguage.EN)!!
        assertFalse("address off statements, off the notice", private.contains("Main St"))
        val shown = privacyNoticeFor(AppSettings(business = full, showAddressOnStatements = true), NoticeLanguage.EN)!!
        assertTrue(shown.contains("1 Main St, Athens"))
    }

    @Test fun noticeLeavesOutTheIban() {
        assertFalse(privacyNotice(full, NoticeLanguage.EN).contains("GR16"))
    }
}
