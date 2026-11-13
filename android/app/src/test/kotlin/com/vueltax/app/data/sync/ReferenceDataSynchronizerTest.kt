package com.vueltax.app.data.sync

import com.vueltax.app.data.local.CachedRateEntity
import com.vueltax.app.data.local.CachedRefundPointEntity
import com.vueltax.app.data.local.CachedRuleSetEntity
import com.vueltax.app.data.local.RateDao
import com.vueltax.app.data.local.RefundPointDao
import com.vueltax.app.data.local.RuleSetDao
import com.vueltax.app.data.remote.CountryDto
import com.vueltax.app.data.remote.PublicApi
import com.vueltax.app.data.remote.RateDto
import com.vueltax.app.data.remote.RatesDto
import com.vueltax.app.data.remote.RefundPointDto
import com.vueltax.app.data.remote.RulesVersionDto
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceDataSynchronizerTest {
    private val api = FakeApi()
    private val ruleSetDao = FakeRuleSetDao()
    private val rateDao = FakeRateDao()
    private val refundPointDao = FakeRefundPointDao()

    private fun synchronizerAt(instant: String) = ReferenceDataSynchronizer(
        api = api,
        ruleSetDao = ruleSetDao,
        rateDao = rateDao,
        refundPointDao = refundPointDao,
        clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
    )

    @Test
    fun `la primera vez descarga las reglas de los paises que tienen`() = runTest {
        api.countries = listOf(country("CO", rulesVersion = 1), country("PE", rulesVersion = null))

        val summary = synchronizerAt(DAY_ONE).sync()

        assertEquals(listOf("CO"), api.rulesRequests)
        assertEquals(listOf("CO"), summary.updatedRuleSets)
        assertEquals(1, ruleSetDao.version("CO"))
    }

    @Test
    fun `no vuelve a descargar las reglas si la version no cambio`() = runTest {
        api.countries = listOf(country("CO", rulesVersion = 1))
        synchronizerAt(DAY_ONE).sync()

        val summary = synchronizerAt(DAY_TWO).sync()

        assertEquals(listOf("CO"), api.rulesRequests)
        assertTrue(summary.updatedRuleSets.isEmpty())
    }

    @Test
    fun `descarga las reglas cuando el servidor publica otra version`() = runTest {
        api.countries = listOf(country("CO", rulesVersion = 1))
        synchronizerAt(DAY_ONE).sync()
        api.countries = listOf(country("CO", rulesVersion = 2))

        synchronizerAt(DAY_TWO).sync()

        assertEquals(listOf("CO", "CO"), api.rulesRequests)
        assertEquals(2, ruleSetDao.version("CO"))
    }

    @Test
    fun `descarga las tasas una sola vez al dia`() = runTest {
        assertTrue(synchronizerAt(DAY_ONE).sync().ratesUpdated)
        assertFalse(synchronizerAt(DAY_ONE_LATER).sync().ratesUpdated)
        assertEquals(1, api.ratesRequests)

        assertTrue(synchronizerAt(DAY_TWO).sync().ratesUpdated)
        assertEquals(2, api.ratesRequests)
    }

    @Test
    fun `sustituye los puntos de devolucion por los del servidor`() = runTest {
        api.refundPoints = listOf(refundPoint(id = 1), refundPoint(id = 2))
        synchronizerAt(DAY_ONE).sync()
        api.refundPoints = listOf(refundPoint(id = 2))

        synchronizerAt(DAY_TWO).sync()

        assertEquals(listOf(2L), refundPointDao.points.map { it.id })
    }

    private fun country(code: String, rulesVersion: Int?) = CountryDto(
        code = code,
        name = code,
        currency = "COP",
        taxName = "IVA",
        rulesVersion = rulesVersion,
    )

    private fun refundPoint(id: Long) = RefundPointDto(
        id = id,
        country = "CO",
        airportIata = "BOG",
        terminal = null,
        description = "Punto de ejemplo",
        lat = 4.7,
        lon = -74.1,
        openingHours = "24 h",
    )

    private companion object {
        const val DAY_ONE = "2026-11-12T08:00:00Z"
        const val DAY_ONE_LATER = "2026-11-12T20:00:00Z"
        const val DAY_TWO = "2026-11-13T08:00:00Z"
    }
}

private class FakeApi : PublicApi {
    var countries: List<CountryDto> = emptyList()
    var refundPoints: List<RefundPointDto> = emptyList()
    val rulesRequests = mutableListOf<String>()
    var ratesRequests = 0

    override suspend fun countries(): List<CountryDto> = countries

    override suspend fun rules(country: String): ResponseBody {
        rulesRequests += country
        return """{"country":"$country"}""".toResponseBody()
    }

    override suspend fun rulesVersion(country: String): RulesVersionDto =
        RulesVersionDto(country, countries.first { it.code == country }.rulesVersion ?: 0)

    override suspend fun refundPoints(country: String?): List<RefundPointDto> = refundPoints

    override suspend fun rates(base: String, date: String?): RatesDto {
        ratesRequests++
        return RatesDto(
            base = base,
            date = "2026-11-12",
            rates = listOf(RateDto("COP", 3300.0, "2026-11-12", "trm")),
        )
    }
}

private class FakeRuleSetDao : RuleSetDao {
    private val ruleSets = mutableMapOf<String, CachedRuleSetEntity>()

    override suspend fun version(countryCode: String): Int? = ruleSets[countryCode]?.version

    override suspend fun get(countryCode: String): CachedRuleSetEntity? = ruleSets[countryCode]

    override suspend fun upsert(ruleSet: CachedRuleSetEntity) {
        ruleSets[ruleSet.countryCode] = ruleSet
    }
}

private class FakeRateDao : RateDao {
    private val rates = mutableMapOf<Pair<String, String>, CachedRateEntity>()

    override suspend fun lastFetchedAt(base: String): Long? =
        rates.values.filter { it.base == base }.maxOfOrNull { it.fetchedAt }

    override fun observe(base: String): Flow<List<CachedRateEntity>> =
        flowOf(rates.values.filter { it.base == base })

    override suspend fun upsertAll(rates: List<CachedRateEntity>) {
        rates.forEach { this.rates[it.base to it.quote] = it }
    }
}

private class FakeRefundPointDao : RefundPointDao() {
    val points = mutableListOf<CachedRefundPointEntity>()

    override fun observeByCountry(countryCode: String): Flow<List<CachedRefundPointEntity>> =
        flowOf(points.filter { it.countryCode == countryCode })

    override suspend fun deleteAll() {
        points.clear()
    }

    override suspend fun insertAll(points: List<CachedRefundPointEntity>) {
        this.points += points
    }
}
