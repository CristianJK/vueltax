package com.vueltax.domain.rules

import com.vueltax.domain.model.ExchangeRate
import com.vueltax.domain.model.ExitMode
import com.vueltax.domain.model.Purchase
import com.vueltax.domain.model.PurchaseKind
import com.vueltax.domain.model.Traveler
import com.vueltax.domain.model.Trip
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals

// Reglas de prueba con los mismos valores de ejemplo que rules/CO.json, PE.json y MX.json.
// Se definen aquí para que las pruebas del motor no cambien cuando se verifiquen esos datos.

internal val NOV_10: LocalDate = LocalDate.of(2026, 11, 10)
internal val NOV_12: LocalDate = LocalDate.of(2026, 11, 12)

private fun baseRuleSet(country: String, currency: String, taxName: String, rate: String) = RuleSet(
    country = country,
    currency = currency,
    taxName = taxName,
    standardRate = BigDecimal(rate),
    refundAuthority = RefundAuthority(AuthorityType.TAX_AUTHORITY, "Autoridad de prueba"),
    amountUnit = AmountUnit(AmountUnitType.LOCAL_CURRENCY),
    minPurchase = null,
    maxRefund = null,
    operatorFeePct = null,
    stayDays = StayDays(min = null, max = null),
    exitModes = listOf(ExitMode.AIR, ExitMode.SEA, ExitMode.LAND),
    requiresAuthorizedStore = false,
    requiresIntentBeforeInvoice = false,
    eligibleGoods = listOf("taxed_goods"),
    excludedGoods = emptyList(),
    excludedServices = listOf(RuleSet.ALL_SERVICES),
    requiredDocuments = listOf("passport"),
    refundPoints = emptyList(),
    sources = emptyList(),
    lastVerified = null,
    version = 1,
    validFrom = LocalDate.of(2026, 1, 1),
    needsVerification = true,
)

/** Colombia: montos en UVT (52 374 COP), mínimo 3 UVT por factura, tope 200 UVT. */
internal val colombia: RuleSet = baseRuleSet("CO", "COP", "IVA", "19").copy(
    amountUnit = AmountUnit(
        type = AmountUnitType.TAX_UNIT,
        code = "UVT",
        value = BigDecimal("52374"),
        year = 2026,
    ),
    minPurchase = MinPurchase(BigDecimal("3"), MinPurchaseScope.PER_INVOICE),
    maxRefund = MaxRefund(BigDecimal("200"), MaxRefundScope.PER_REQUEST),
    excludedGoods = listOf("exempt_goods", "excluded_goods"),
)

/** Perú: mínimo 100 PEN por factura, tienda inscrita, intención previa, estadía de 2 a 60 días. */
internal val peru: RuleSet = baseRuleSet("PE", "PEN", "IGV", "18").copy(
    minPurchase = MinPurchase(BigDecimal("100"), MinPurchaseScope.PER_INVOICE),
    stayDays = StayDays(min = 2, max = 60),
    exitModes = listOf(ExitMode.AIR, ExitMode.SEA),
    requiresAuthorizedStore = true,
    requiresIntentBeforeInvoice = true,
)

/** México: mínimo 1 200 MXN por tienda con pago electrónico, comisión del 35 %. */
internal val mexico: RuleSet = baseRuleSet("MX", "MXN", "IVA", "16").copy(
    minPurchase = MinPurchase(
        amount = BigDecimal("1200"),
        scope = MinPurchaseScope.PER_STORE,
        electronicPaymentOnly = true,
    ),
    operatorFeePct = BigDecimal("35"),
    exitModes = listOf(ExitMode.AIR, ExitMode.SEA),
    requiresAuthorizedStore = true,
)

/** Factura que cumple todo lo que no dependa del monto; cada prueba cambia lo que le interesa. */
internal fun purchase(
    total: String,
    currency: String,
    tax: String? = null,
    storeName: String = "Tienda Uno",
    date: LocalDate = NOV_10,
) = Purchase(
    tripId = 1,
    storeName = storeName,
    invoiceNumber = "F-$total",
    purchaseDate = date,
    total = BigDecimal(total),
    tax = tax?.let { BigDecimal(it) },
    currency = currency,
    kind = PurchaseKind.GOODS,
    category = "taxed_goods",
    paidElectronically = true,
    authorizedStore = true,
    intentDeclaredBeforeInvoice = true,
)

internal fun traveler(nationality: String = "US", residenceCountry: String = "US") =
    Traveler(nationality = nationality, residenceCountry = residenceCountry)

internal fun trip(
    country: String,
    arrival: LocalDate = NOV_10,
    departure: LocalDate = NOV_12,
    exitMode: ExitMode = ExitMode.AIR,
) = Trip(
    countryCode = country,
    arrivalDate = arrival,
    departureDate = departure,
    exitMode = exitMode,
)

internal fun usdRate(quote: String, rate: String, date: LocalDate) =
    ExchangeRate(base = "USD", quote = quote, rate = BigDecimal(rate), date = date, source = "test")

/** Compara dinero por valor, sin importar la escala (38000 es igual a 38000.00). */
internal fun assertMoney(expected: String, actual: BigDecimal?) {
    val matches = actual != null && BigDecimal(expected).compareTo(actual) == 0
    assertEquals("Se esperaba $expected y fue $actual", true, matches)
}

internal fun issuesOf(evaluation: PurchaseEvaluation): List<PurchaseIssue> =
    (evaluation as PurchaseEvaluation.NotQualified).reasons

internal fun issuesOf(eligibility: TravelerEligibility): List<TravelerIssue> =
    (eligibility as TravelerEligibility.NotEligible).reasons
