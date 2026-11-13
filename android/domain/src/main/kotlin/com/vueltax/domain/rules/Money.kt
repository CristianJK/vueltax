package com.vueltax.domain.rules

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Decimales con los que se redondea todo monto de dinero. */
internal const val MONEY_SCALE = 2

internal val HUNDRED: BigDecimal = BigDecimal("100")

/** Precisión de los cálculos intermedios (tasas de cambio cruzadas y proporciones). */
internal val CALCULATION_CONTEXT: MathContext = MathContext.DECIMAL64

internal fun BigDecimal.toMoney(): BigDecimal = setScale(MONEY_SCALE, RoundingMode.HALF_UP)

internal fun formatMoney(amount: BigDecimal, currency: String): String =
    "${amount.toMoney().toPlainString()} $currency"
