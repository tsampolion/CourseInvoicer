package gr.eduinvoice.ui.lessons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import gr.eduinvoice.core.model.Lesson
import gr.eduinvoice.ui.common.ChipTone
import gr.eduinvoice.ui.common.LocalFormats
import gr.eduinvoice.ui.common.StatusChip

/** One lesson with its status and a quick "paid" toggle. */
@Composable
fun LessonRow(
    lesson: Lesson,
    title: String,
    onClick: () -> Unit,
    onPaidChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = LocalFormats.current
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(
                    "${formats.date(lesson.date)} · ${formats.time(lesson.startTime)} · " +
                        formats.duration(lesson.durationMinutes),
                )
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        lesson.absent -> StatusChip("Absent", ChipTone.Neutral)
                        lesson.isPaid -> StatusChip("Paid", ChipTone.Good)
                        lesson.charge.isPositive -> StatusChip("Unpaid", ChipTone.Warning)
                    }
                    if (lesson.isInvoiced) StatusChip("Invoiced", ChipTone.Neutral)
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(formats.money(lesson.charge), style = MaterialTheme.typography.titleSmall)
                if (!lesson.absent) {
                    Checkbox(
                        checked = lesson.isPaid,
                        onCheckedChange = onPaidChange,
                        modifier = Modifier.semantics { contentDescription = "Paid" },
                    )
                }
            }
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}
