package com.vueltax.app.data.sync

import com.vueltax.app.data.local.CachedRuleSetEntity
import com.vueltax.app.data.local.RateDao
import com.vueltax.app.data.local.RefundPointDao
import com.vueltax.app.data.local.RuleSetDao
import com.vueltax.app.data.remote.PublicApi
import com.vueltax.app.data.remote.toEntity
import com.vueltax.domain.AppConfig
import com.vueltax.domain.sync.SyncPolicy
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

data class SyncSummary(val updatedRuleSets: List<String>, val ratesUpdated: Boolean)

/** Trae los datos públicos de la API a la caché local, descargando solo lo que cambió. */
class ReferenceDataSynchronizer @Inject constructor(
    private val api: PublicApi,
    private val ruleSetDao: RuleSetDao,
    private val rateDao: RateDao,
    private val refundPointDao: RefundPointDao,
    private val clock: Clock,
) {
    suspend fun sync(): SyncSummary {
        val now = clock.millis()
        val updatedRuleSets = syncRules(now)
        refundPointDao.replaceAll(api.refundPoints().map { it.toEntity(now) })
        val ratesUpdated = syncRates(now)
        return SyncSummary(updatedRuleSets, ratesUpdated)
    }

    /** `/v1/countries` trae la versión de cada país; solo se bajan las reglas que cambiaron. */
    private suspend fun syncRules(now: Long): List<String> {
        val updated = mutableListOf<String>()
        for (country in api.countries()) {
            val remoteVersion = country.rulesVersion ?: continue
            val cachedVersion = ruleSetDao.version(country.code)
            if (!SyncPolicy.shouldDownloadRules(cachedVersion, remoteVersion)) continue

            val content = api.rules(country.code).use { it.string() }
            ruleSetDao.upsert(
                CachedRuleSetEntity(
                    countryCode = country.code,
                    version = remoteVersion,
                    contentJson = content,
                    fetchedAt = now,
                ),
            )
            updated += country.code
        }
        return updated
    }

    private suspend fun syncRates(now: Long): Boolean {
        val base = AppConfig.RATES_BASE_CURRENCY
        val lastDownload = rateDao.lastFetchedAt(base)?.let {
            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
        }
        if (!SyncPolicy.shouldDownloadRates(lastDownload, LocalDate.now(clock))) return false

        val rates = api.rates(base).rates.map { it.toEntity(base, now) }
        rateDao.upsertAll(rates)
        return true
    }
}
