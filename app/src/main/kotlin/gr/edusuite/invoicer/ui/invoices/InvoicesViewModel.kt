package gr.edusuite.invoicer.ui.invoices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gr.edusuite.invoicer.core.model.Invoice
import gr.edusuite.invoicer.core.repository.InvoiceRepository
import gr.edusuite.invoicer.core.repository.LessonRepository
import gr.edusuite.invoicer.core.repository.StudentRepository
import gr.edusuite.invoicer.core.usecase.VoidInvoice
import gr.edusuite.invoicer.ui.common.launchCatching
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
        lessons.observeInvoicePayments(),
        message,
    ) { invoiceList, studentList, payments, currentMessage ->
        val names = studentList.associate { it.id to it.fullName }
        InvoicesUiState(
            loading = false,
            items = invoiceList.map { invoice ->
                InvoiceItem(
                    invoice = invoice,
                    studentName = names[invoice.studentId].orEmpty(),
                    // A void invoice has released its lessons, so it is never paid.
                    paid = payments[invoice.id]?.isPaid == true,
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
