package gr.eduinvoice.ui.invoices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.eduinvoice.core.model.Invoice
import gr.eduinvoice.core.repository.InvoiceRepository
import gr.eduinvoice.core.repository.LessonRepository
import gr.eduinvoice.core.repository.StudentRepository
import gr.eduinvoice.core.usecase.VoidInvoice
import gr.eduinvoice.ui.common.launchCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class InvoiceItem(val invoice: Invoice, val studentName: String, val paid: Boolean)

data class InvoicesUiState(
    val loading: Boolean = true,
    val items: List<InvoiceItem> = emptyList(),
    val message: String? = null,
)

class InvoicesViewModel(
    invoices: InvoiceRepository,
    students: StudentRepository,
    lessons: LessonRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val voidInvoice = VoidInvoice(invoices)

    val state: StateFlow<InvoicesUiState> = combine(
        invoices.observeInvoices(),
        students.observeStudents(),
        lessons.observeLessons(),
        message,
    ) { invoiceList, studentList, lessonList, currentMessage ->
        val names = studentList.associate { it.id to it.fullName }
        val lessonsByInvoice = lessonList.filter { it.invoiceId != null }.groupBy { it.invoiceId }
        InvoicesUiState(
            loading = false,
            items = invoiceList.map { invoice ->
                val linked = lessonsByInvoice[invoice.id].orEmpty()
                InvoiceItem(
                    invoice = invoice,
                    studentName = names[invoice.studentId].orEmpty(),
                    paid = !invoice.isVoid && linked.isNotEmpty() && linked.all { it.isPaid },
                )
            },
            message = currentMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InvoicesUiState())

    fun markVoid(invoice: Invoice) = launchCatching({ message.value = it }) {
        voidInvoice(invoice.id, today())
        message.value = "Invoice ${invoice.number} voided. Its lessons can be invoiced again."
    }

    fun showMessage(text: String) {
        message.value = text
    }

    fun messageShown() {
        message.value = null
    }
}
