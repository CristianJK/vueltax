package com.vueltax.domain.rules

import com.vueltax.domain.model.Purchase
import com.vueltax.domain.model.PurchaseKind
import java.math.BigDecimal

sealed interface PurchaseEvaluation {
    data object Qualified : PurchaseEvaluation

    data class NotQualified(val reasons: List<PurchaseIssue>) : PurchaseEvaluation

    /** Aún no se puede decidir: faltan datos de la factura que el usuario debe completar. */
    data class NeedsInfo(val missingFields: List<PurchaseField>) : PurchaseEvaluation
}

/** Dato de la factura que hace falta para evaluarla; [label] es texto para mostrar. */
enum class PurchaseField(val label: String) {
    KIND("si es un bien o un servicio"),
    CATEGORY("la categoría del bien o servicio"),
    PAYMENT_METHOD("si se pagó por un medio electrónico"),
    AUTHORIZED_STORE("si el establecimiento está autorizado"),
    INTENT_BEFORE_INVOICE("si pediste la devolución antes de que emitieran la factura"),
}

/** Motivo por el que una factura no da derecho a devolución; [message] es texto para mostrar. */
sealed interface PurchaseIssue {
    val message: String

    data class WrongCurrency(val currency: String, val expected: String) : PurchaseIssue {
        override val message = "La factura está en $currency y debe estar en $expected."
    }

    /** No se llega a la compra mínima; [missing] es lo que falta, en moneda local. */
    data class BelowMinimum(
        val missing: BigDecimal,
        val minimum: BigDecimal,
        val currency: String,
        val scope: MinPurchaseScope,
    ) : PurchaseIssue {
        override val message = "Te faltan ${formatMoney(missing, currency)} para el mínimo de " +
            "${formatMoney(minimum, currency)} por ${scope.label()}."
    }

    data object NotPaidElectronically : PurchaseIssue {
        override val message = "Solo cuentan las compras pagadas por medios electrónicos."
    }

    data object StoreNotAuthorized : PurchaseIssue {
        override val message = "El establecimiento no está autorizado para la devolución."
    }

    data object IntentNotDeclared : PurchaseIssue {
        override val message =
            "Había que pedir la devolución antes de que el comercio emitiera la factura."
    }

    data object ServiceNotRefundable : PurchaseIssue {
        override val message = "Los servicios no dan derecho a devolución."
    }

    data class CategoryExcluded(val category: String) : PurchaseIssue {
        override val message = "Este tipo de compra está excluido de la devolución."
    }

    data class CategoryNotEligible(val category: String) : PurchaseIssue {
        override val message = "Este tipo de bien no está entre los que dan derecho a devolución."
    }
}

private fun MinPurchaseScope.label(): String = when (this) {
    MinPurchaseScope.PER_INVOICE -> "factura"
    MinPurchaseScope.PER_STORE -> "establecimiento"
}

/**
 * ¿Da esta factura derecho a devolución? Comprueba moneda, compra mínima, medio de pago,
 * tienda autorizada, intención previa y tipo de bien.
 *
 * [allPurchases] son las facturas del viaje: solo se usan cuando el mínimo es por
 * establecimiento, para sumar las compras hechas en la misma tienda.
 *
 * Si algún requisito se incumple el resultado es [PurchaseEvaluation.NotQualified], aunque
 * falten otros datos; solo si nada se incumple pero faltan datos es [PurchaseEvaluation.NeedsInfo].
 */
fun evaluatePurchase(
    purchase: Purchase,
    ruleSet: RuleSet,
    allPurchases: List<Purchase> = listOf(purchase),
): PurchaseEvaluation {
    if (purchase.currency != ruleSet.currency) {
        val issue = PurchaseIssue.WrongCurrency(purchase.currency, ruleSet.currency)
        return PurchaseEvaluation.NotQualified(listOf(issue))
    }

    val checks = listOf(
        checkMinimum(purchase, ruleSet, allPurchases),
        checkPaymentMethod(purchase, ruleSet),
        checkStore(purchase, ruleSet),
        checkIntent(purchase, ruleSet),
        checkCategory(purchase, ruleSet),
    )
    val issues = checks.mapNotNull { (it as? Check.Failed)?.issue }
    val missing = checks.mapNotNull { (it as? Check.Missing)?.field }
    return when {
        issues.isNotEmpty() -> PurchaseEvaluation.NotQualified(issues)
        missing.isNotEmpty() -> PurchaseEvaluation.NeedsInfo(missing)
        else -> PurchaseEvaluation.Qualified
    }
}

private sealed interface Check {
    data object Passed : Check

    data class Failed(val issue: PurchaseIssue) : Check

    data class Missing(val field: PurchaseField) : Check
}

private fun checkMinimum(purchase: Purchase, ruleSet: RuleSet, allPurchases: List<Purchase>): Check {
    val rule = ruleSet.minPurchase
    // Si el pago no fue electrónico (o no se sabe), lo decide checkPaymentMethod.
    val countsForMinimum = rule != null &&
        (!rule.electronicPaymentOnly || purchase.paidElectronically == true)
    if (rule == null || !countsForMinimum) return Check.Passed

    val minimum = ruleSet.toLocalCurrency(rule.amount)
    val reached = when (rule.scope) {
        MinPurchaseScope.PER_INVOICE -> purchase.total
        MinPurchaseScope.PER_STORE -> storeTotal(purchase, rule, allPurchases)
    }
    return if (reached < minimum) {
        val missing = (minimum - reached).toMoney()
        Check.Failed(PurchaseIssue.BelowMinimum(missing, minimum, ruleSet.currency, rule.scope))
    } else {
        Check.Passed
    }
}

/** Suma de las compras del mismo establecimiento que cuentan para el mínimo. */
private fun storeTotal(purchase: Purchase, rule: MinPurchase, allPurchases: List<Purchase>): BigDecimal {
    val storeKey = { item: Purchase -> item.storeName.trim().lowercase() }
    val store = storeKey(purchase)
    val purchases = if (purchase in allPurchases) allPurchases else allPurchases + purchase
    return purchases
        .filter { storeKey(it) == store && it.currency == purchase.currency }
        .filter { !rule.electronicPaymentOnly || it.paidElectronically == true }
        .fold(BigDecimal.ZERO) { sum, item -> sum + item.total }
}

private fun checkPaymentMethod(purchase: Purchase, ruleSet: RuleSet): Check = when {
    ruleSet.minPurchase?.electronicPaymentOnly != true -> Check.Passed
    purchase.paidElectronically == null -> Check.Missing(PurchaseField.PAYMENT_METHOD)
    purchase.paidElectronically == true -> Check.Passed
    else -> Check.Failed(PurchaseIssue.NotPaidElectronically)
}

private fun checkStore(purchase: Purchase, ruleSet: RuleSet): Check = when {
    !ruleSet.requiresAuthorizedStore -> Check.Passed
    purchase.authorizedStore == null -> Check.Missing(PurchaseField.AUTHORIZED_STORE)
    purchase.authorizedStore == true -> Check.Passed
    else -> Check.Failed(PurchaseIssue.StoreNotAuthorized)
}

private fun checkIntent(purchase: Purchase, ruleSet: RuleSet): Check = when {
    !ruleSet.requiresIntentBeforeInvoice -> Check.Passed
    purchase.intentDeclaredBeforeInvoice == null -> Check.Missing(PurchaseField.INTENT_BEFORE_INVOICE)
    purchase.intentDeclaredBeforeInvoice == true -> Check.Passed
    else -> Check.Failed(PurchaseIssue.IntentNotDeclared)
}

private fun checkCategory(purchase: Purchase, ruleSet: RuleSet): Check = when (purchase.kind) {
    null -> Check.Missing(PurchaseField.KIND)
    PurchaseKind.SERVICE -> checkService(purchase.category, ruleSet)
    PurchaseKind.GOODS -> checkGoods(purchase.category, ruleSet)
}

private fun checkService(category: String?, ruleSet: RuleSet): Check = when {
    RuleSet.ALL_SERVICES in ruleSet.excludedServices -> Check.Failed(PurchaseIssue.ServiceNotRefundable)
    ruleSet.excludedServices.isEmpty() -> Check.Passed
    category == null -> Check.Missing(PurchaseField.CATEGORY)
    category in ruleSet.excludedServices -> Check.Failed(PurchaseIssue.CategoryExcluded(category))
    else -> Check.Passed
}

private fun checkGoods(category: String?, ruleSet: RuleSet): Check = when {
    ruleSet.eligibleGoods.isEmpty() && ruleSet.excludedGoods.isEmpty() -> Check.Passed
    category == null -> Check.Missing(PurchaseField.CATEGORY)
    category in ruleSet.excludedGoods -> Check.Failed(PurchaseIssue.CategoryExcluded(category))
    ruleSet.eligibleGoods.isNotEmpty() && category !in ruleSet.eligibleGoods ->
        Check.Failed(PurchaseIssue.CategoryNotEligible(category))
    else -> Check.Passed
}
