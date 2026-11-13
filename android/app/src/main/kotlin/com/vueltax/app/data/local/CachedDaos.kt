package com.vueltax.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleSetDao {
    @Query("SELECT version FROM cached_rule_set WHERE countryCode = :countryCode")
    suspend fun version(countryCode: String): Int?

    @Query("SELECT * FROM cached_rule_set WHERE countryCode = :countryCode")
    suspend fun get(countryCode: String): CachedRuleSetEntity?

    @Upsert
    suspend fun upsert(ruleSet: CachedRuleSetEntity)
}

@Dao
interface RateDao {
    @Query("SELECT MAX(fetchedAt) FROM cached_rate WHERE base = :base")
    suspend fun lastFetchedAt(base: String): Long?

    @Query("SELECT * FROM cached_rate WHERE base = :base ORDER BY quote")
    fun observe(base: String): Flow<List<CachedRateEntity>>

    @Upsert
    suspend fun upsertAll(rates: List<CachedRateEntity>)
}

@Dao
abstract class RefundPointDao {
    @Query("SELECT * FROM cached_refund_point WHERE countryCode = :countryCode ORDER BY airportIata")
    abstract fun observeByCountry(countryCode: String): Flow<List<CachedRefundPointEntity>>

    @Query("DELETE FROM cached_refund_point")
    abstract suspend fun deleteAll()

    @Insert
    abstract suspend fun insertAll(points: List<CachedRefundPointEntity>)

    /** Sustituye todos los puntos por la lista recién descargada. */
    @Transaction
    open suspend fun replaceAll(points: List<CachedRefundPointEntity>) {
        deleteAll()
        insertAll(points)
    }
}
