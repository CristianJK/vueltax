package com.vueltax.app.ui.captura

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vueltax.app.R
import com.vueltax.app.capture.review.InvoiceDraft
import com.vueltax.app.capture.review.ReviewResult
import com.vueltax.domain.model.PurchaseKind
import com.vueltax.domain.rules.PurchaseEvaluation

private val itemSpacing = 12.dp
private val chipSpacing = 8.dp
private val cardPadding = 16.dp

/** Formulario de revisión: cada cambio vuelve a pasar la factura por el motor de reglas. */
@Composable
fun RevisionForm(
    state: CapturaUiState,
    onDraftChanged: ((InvoiceDraft) -> InvoiceDraft) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val draft = state.draft
    Text(
        text = stringResource(R.string.review_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    if (draft.imagePath != null) Text(text = stringResource(R.string.review_photo_saved))

    ResultCard(result = state.result)

    DraftTextFields(draft = draft, onDraftChanged = onDraftChanged)
    KindAndCategory(draft = draft, categories = state.categories, onDraftChanged = onDraftChanged)
    TriStateRow(R.string.review_paid_electronically, draft.paidElectronically) { answer ->
        onDraftChanged { it.copy(paidElectronically = answer) }
    }
    TriStateRow(R.string.review_authorized_store, draft.authorizedStore) { answer ->
        onDraftChanged { it.copy(authorizedStore = answer) }
    }
    TriStateRow(R.string.review_intent, draft.intentDeclaredBeforeInvoice) { answer ->
        onDraftChanged { it.copy(intentDeclaredBeforeInvoice = answer) }
    }

    if (!state.hasTrip) {
        Text(
            text = stringResource(R.string.review_no_trip),
            color = MaterialTheme.colorScheme.error,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(chipSpacing)) {
        Button(
            onClick = onSave,
            enabled = state.hasTrip && state.result is ReviewResult.Evaluated && !state.busy,
        ) {
            Text(text = stringResource(R.string.review_save))
        }
        TextButton(onClick = onDiscard) {
            Text(text = stringResource(R.string.review_discard))
        }
    }
}

@Composable
private fun DraftTextFields(
    draft: InvoiceDraft,
    onDraftChanged: ((InvoiceDraft) -> InvoiceDraft) -> Unit,
) {
    DraftField(R.string.review_store, draft.storeName) { text ->
        onDraftChanged { it.copy(storeName = text) }
    }
    DraftField(R.string.review_number, draft.invoiceNumber) { text ->
        onDraftChanged { it.copy(invoiceNumber = text) }
    }
    DraftField(R.string.review_date, draft.issueDate, isError = draft.parsedDate == null) { text ->
        onDraftChanged { it.copy(issueDate = text) }
    }
    DraftField(R.string.review_total, draft.total, isError = draft.parsedTotal == null) { text ->
        onDraftChanged { it.copy(total = text) }
    }
    DraftField(R.string.review_tax, draft.tax) { text ->
        onDraftChanged { it.copy(tax = text) }
    }
}

@Composable
private fun DraftField(
    label: Int,
    value: String,
    isError: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = stringResource(label)) },
        isError = isError,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindAndCategory(
    draft: InvoiceDraft,
    categories: List<String>,
    onDraftChanged: ((InvoiceDraft) -> InvoiceDraft) -> Unit,
) {
    Text(text = stringResource(R.string.review_kind))
    Row(horizontalArrangement = Arrangement.spacedBy(chipSpacing)) {
        FilterChip(
            selected = draft.kind == PurchaseKind.GOODS,
            onClick = { onDraftChanged { it.copy(kind = PurchaseKind.GOODS) } },
            label = { Text(text = stringResource(R.string.review_kind_goods)) },
        )
        FilterChip(
            selected = draft.kind == PurchaseKind.SERVICE,
            onClick = { onDraftChanged { it.copy(kind = PurchaseKind.SERVICE, category = null) } },
            label = { Text(text = stringResource(R.string.review_kind_service)) },
        )
    }
    if (draft.kind == PurchaseKind.GOODS && categories.isNotEmpty()) {
        Text(text = stringResource(R.string.review_category))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(chipSpacing)) {
            categories.forEach { category ->
                FilterChip(
                    selected = draft.category == category,
                    onClick = { onDraftChanged { it.copy(category = category) } },
                    label = { Text(text = category.replace('_', ' ')) },
                )
            }
        }
    }
}

/** Pregunta de sí / no / no sé; «no sé» deja el dato en null para que el motor lo pida. */
@Composable
private fun TriStateRow(label: Int, value: Boolean?, onChange: (Boolean?) -> Unit) {
    Text(text = stringResource(label))
    Row(horizontalArrangement = Arrangement.spacedBy(chipSpacing)) {
        val options = listOf(
            true to R.string.option_yes,
            false to R.string.option_no,
            null to R.string.option_unknown,
        )
        options.forEach { (answer, text) ->
            FilterChip(
                selected = value == answer,
                onClick = { onChange(answer) },
                label = { Text(text = stringResource(text)) },
            )
        }
    }
}

/** Resultado en vivo del motor de reglas para la factura tal como está escrita. */
@Composable
private fun ResultCard(result: ReviewResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(cardPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            Text(
                text = stringResource(R.string.review_result_title),
                style = MaterialTheme.typography.titleMedium,
            )
            when (result) {
                ReviewResult.NoRules -> Text(text = stringResource(R.string.review_result_no_rules))
                ReviewResult.Incomplete -> Text(text = stringResource(R.string.review_result_incomplete))
                is ReviewResult.Evaluated -> EvaluationText(result)
            }
        }
    }
}

@Composable
private fun EvaluationText(result: ReviewResult.Evaluated) {
    when (val evaluation = result.evaluation) {
        PurchaseEvaluation.Qualified -> {
            val amount = "${result.recoverableTax.toPlainString()} ${result.currency}"
            Text(text = stringResource(R.string.review_result_qualified, amount))
        }
        is PurchaseEvaluation.NotQualified -> {
            Text(text = stringResource(R.string.review_result_not_qualified))
            evaluation.reasons.forEach { Text(text = "• ${it.message}") }
        }
        is PurchaseEvaluation.NeedsInfo -> {
            Text(text = stringResource(R.string.review_result_needs_info))
            evaluation.missingFields.forEach { Text(text = "• ${it.label}") }
        }
    }
}
