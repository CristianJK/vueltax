package com.vueltax.app.capture.review

import com.vueltax.domain.model.Purchase
import com.vueltax.domain.rules.PurchaseEvaluation
import com.vueltax.domain.rules.RateTable
import com.vueltax.domain.rules.RuleSet
import com.vueltax.domain.rules.estimateRefund
import java.math.BigDecimal
import java.time.LocalDate

/** Lo que la pantalla de revisión muestra sobre la factura mientras el usuario la edita. */
sealed interface ReviewResult {
    /** Aún no se han descargado las reglas del país. */
    data object NoRules : ReviewResult

    /** Falta el total o la fecha, o no son válidos: todavía no se puede evaluar. */
    data object Incomplete : ReviewResult

    data class Evaluated(
        val purchase: Purchase,
        val evaluation: PurchaseEvaluation,
        /** Impuesto recuperable de esta factura, antes de topes y comisiones; 0 si no califica. */
        val recoverableTax: BigDecimal,
        val currency: String,
    ) : ReviewResult
}

object InvoiceReviewer {
    /**
     * Pasa el borrador por el motor de reglas. [otherPurchases] son las facturas ya
     * guardadas del viaje, que cuentan cuando el mínimo es por establecimiento.
     */
    fun review(
        draft: InvoiceDraft,
        ruleSet: RuleSet?,
        otherPurchases: List<Purchase>,
        tripId: Long,
        today: LocalDate,
    ): ReviewResult {
        val purchase = ruleSet?.let { draft.toPurchase(tripId, it.currency) }
        return when {
            ruleSet == null -> ReviewResult.NoRules
            purchase == null -> ReviewResult.Incomplete
            else -> evaluate(purchase, ruleSet, otherPurchases, today)
        }
    }

    private fun evaluate(
        purchase: Purchase,
        ruleSet: RuleSet,
        otherPurchases: List<Purchase>,
        today: LocalDate,
    ): ReviewResult {
        val estimate = estimateRefund(
            purchases = otherPurchases + purchase,
            ruleSet = ruleSet,
            rates = RateTable(emptyList()),
            travelerCurrency = ruleSet.currency,
            today = today,
        )
        val line = estimate.purchases.last()
        return ReviewResult.Evaluated(purchase, line.evaluation, line.recoverableTax, ruleSet.currency)
    }
}
