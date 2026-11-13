package com.vueltax.domain.rules

import com.vueltax.domain.model.Purchase
import java.math.BigDecimal
import java.time.LocalDate

/** Resultado de una factura dentro de la estimación; [recoverableTax] es 0 si no califica. */
data class PurchaseEstimate(
    val purchase: Purchase,
    val evaluation: PurchaseEvaluation,
    val recoverableTax: BigDecimal,
)

/**
 * Estimación de la devolución. Los montos van en la moneda local ([currency]) salvo los
 * equivalentes, que van en [travelerCurrency] y son null si no hay tasa de cambio.
 */
data class RefundEstimate(
    val currency: String,
    val purchases: List<PurchaseEstimate>,
    /** Suma del impuesto recuperable de las facturas que califican, antes del tope. */
    val recoverableTax: BigDecimal,
    /** Tope por solicitud en moneda local; null si las reglas no fijan ninguno. */
    val cap: BigDecimal?,
    /** Monto bruto: el impuesto recuperable una vez aplicado el tope. */
    val gross: BigDecimal,
    val operatorFee: BigDecimal,
    /** Monto neto: bruto menos la comisión del operador. */
    val net: BigDecimal,
    val travelerCurrency: String,
    /** Neto convertido con la tasa del día de cada compra. */
    val netAtPurchaseDateRates: BigDecimal?,
    /** Neto convertido con la tasa de hoy. */
    val netAtTodayRate: BigDecimal?,
) {
    val capApplied: Boolean
        get() = cap != null && recoverableTax > cap
}

/**
 * Estima la devolución de un conjunto de facturas: impuesto recuperable por factura, tope
 * por solicitud (convertido desde la unidad tributaria si aplica), comisión del operador y
 * equivalente en la moneda del viajero.
 *
 * Solo suman las facturas que [evaluatePurchase] da por calificadas. La comisión se calcula
 * con el porcentaje máximo de las reglas, así que el neto es una estimación conservadora.
 */
fun estimateRefund(
    purchases: List<Purchase>,
    ruleSet: RuleSet,
    rates: RateTable,
    travelerCurrency: String,
    today: LocalDate,
): RefundEstimate {
    val lines = purchases.map { purchase ->
        val evaluation = evaluatePurchase(purchase, ruleSet, purchases)
        val tax = if (evaluation is PurchaseEvaluation.Qualified) {
            recoverableTax(purchase, ruleSet)
        } else {
            BigDecimal.ZERO.toMoney()
        }
        PurchaseEstimate(purchase, evaluation, tax)
    }

    val recoverable = lines.fold(BigDecimal.ZERO) { sum, line -> sum + line.recoverableTax }.toMoney()
    val cap = ruleSet.maxRefund?.let { ruleSet.toLocalCurrency(it.amount) }
    val gross = if (cap != null && recoverable > cap) cap else recoverable
    val fee = operatorFee(gross, ruleSet)
    val net = (gross - fee).toMoney()

    val toTraveler = { date: LocalDate -> rates.rate(ruleSet.currency, travelerCurrency, date) }
    return RefundEstimate(
        currency = ruleSet.currency,
        purchases = lines,
        recoverableTax = recoverable,
        cap = cap,
        gross = gross,
        operatorFee = fee,
        net = net,
        travelerCurrency = travelerCurrency,
        netAtPurchaseDateRates = netAtPurchaseDateRates(lines, recoverable, net, toTraveler),
        netAtTodayRate = toTraveler(today)?.let { net.multiply(it).toMoney() },
    )
}

/** Impuesto de la factura; si no figura, se deduce del total con la tasa general. */
private fun recoverableTax(purchase: Purchase, ruleSet: RuleSet): BigDecimal {
    val tax = purchase.tax
        ?: purchase.total
            .multiply(ruleSet.standardRate)
            .divide(HUNDRED + ruleSet.standardRate, CALCULATION_CONTEXT)
    return tax.toMoney()
}

private fun operatorFee(gross: BigDecimal, ruleSet: RuleSet): BigDecimal {
    val feePct = ruleSet.operatorFeePct ?: BigDecimal.ZERO
    return gross.multiply(feePct).divide(HUNDRED, CALCULATION_CONTEXT).toMoney()
}

/**
 * Convierte el impuesto de cada factura con la tasa de su día de compra y reparte el tope y
 * la comisión en proporción (neto / impuesto recuperable). Null si falta alguna tasa.
 */
private fun netAtPurchaseDateRates(
    lines: List<PurchaseEstimate>,
    recoverable: BigDecimal,
    net: BigDecimal,
    toTraveler: (LocalDate) -> BigDecimal?,
): BigDecimal? {
    val converted = lines
        .filter { it.recoverableTax.signum() > 0 }
        .map { line ->
            toTraveler(line.purchase.purchaseDate)?.let { line.recoverableTax.multiply(it) }
        }
    return when {
        recoverable.signum() == 0 -> BigDecimal.ZERO.toMoney()
        converted.any { it == null } -> null
        else -> converted
            .filterNotNull()
            .fold(BigDecimal.ZERO) { sum, amount -> sum + amount }
            .multiply(net.divide(recoverable, CALCULATION_CONTEXT))
            .toMoney()
    }
}
