@file:UseSerializers(BigDecimalSerializer::class, LocalDateSerializer::class)

package com.vueltax.domain.rules

import com.vueltax.domain.model.ExitMode
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json

/**
 * Reglas de devolución de impuestos de un país. Es el mismo modelo que `rules/schema.json`;
 * los porcentajes van de 0 a 100 y los montos de [minPurchase] y [maxRefund] en [amountUnit].
 */
@Serializable
data class RuleSet(
    val country: String,
    val currency: String,
    val taxName: String,
    val standardRate: BigDecimal,
    val refundAuthority: RefundAuthority,
    val amountUnit: AmountUnit,
    val minPurchase: MinPurchase?,
    val maxRefund: MaxRefund?,
    val operatorFeePct: BigDecimal?,
    val stayDays: StayDays,
    val exitModes: List<ExitMode>,
    val requiresAuthorizedStore: Boolean,
    val requiresIntentBeforeInvoice: Boolean,
    val eligibleGoods: List<String>,
    val excludedGoods: List<String>,
    val excludedServices: List<String>,
    val requiredDocuments: List<String>,
    val refundPoints: List<RuleRefundPoint>,
    val notes: List<String> = emptyList(),
    val sources: List<RuleSource>,
    val lastVerified: LocalDate?,
    val version: Int,
    val validFrom: LocalDate,
    val needsVerification: Boolean,
    val verificationNotes: List<String> = emptyList(),
) {
    /** Convierte un monto expresado en [amountUnit] a moneda local. */
    fun toLocalCurrency(amount: BigDecimal): BigDecimal {
        val inCurrency = when (amountUnit.type) {
            AmountUnitType.LOCAL_CURRENCY -> amount
            AmountUnitType.TAX_UNIT -> amount.multiply(
                requireNotNull(amountUnit.value) { "La unidad tributaria no tiene valor" },
            )
        }
        return inCurrency.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    }

    companion object {
        /** Identificador que en `excludedServices` significa "ningún servicio da derecho". */
        const val ALL_SERVICES = "all_services"

        private val json = Json { ignoreUnknownKeys = true }

        /** Interpreta el JSON de un rule set tal como lo publica la API. */
        fun parse(text: String): RuleSet = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class RefundAuthority(val type: AuthorityType, val name: String)

@Serializable
enum class AuthorityType {
    @SerialName("tax_authority")
    TAX_AUTHORITY,

    @SerialName("partner_entity")
    PARTNER_ENTITY,

    @SerialName("concessionaire")
    CONCESSIONAIRE,
}

@Serializable
data class AmountUnit(
    val type: AmountUnitType,
    val code: String? = null,
    val name: String? = null,
    val value: BigDecimal? = null,
    val year: Int? = null,
)

@Serializable
enum class AmountUnitType {
    @SerialName("local_currency")
    LOCAL_CURRENCY,

    @SerialName("tax_unit")
    TAX_UNIT,
}

@Serializable
data class MinPurchase(
    val amount: BigDecimal,
    val scope: MinPurchaseScope,
    val electronicPaymentOnly: Boolean = false,
)

@Serializable
enum class MinPurchaseScope {
    @SerialName("per_invoice")
    PER_INVOICE,

    @SerialName("per_store")
    PER_STORE,
}

@Serializable
data class MaxRefund(val amount: BigDecimal, val scope: MaxRefundScope)

@Serializable
enum class MaxRefundScope {
    @SerialName("per_request")
    PER_REQUEST,
}

@Serializable
data class StayDays(val min: Int?, val max: Int?)

@Serializable
data class RuleRefundPoint(val type: RefundPointType, val iata: String? = null, val name: String)

@Serializable
enum class RefundPointType {
    @SerialName("airport")
    AIRPORT,

    @SerialName("seaport")
    SEAPORT,

    @SerialName("land_border")
    LAND_BORDER,
}

@Serializable
data class RuleSource(val title: String, val url: String, val retrievedAt: LocalDate?)
