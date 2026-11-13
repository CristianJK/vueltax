package com.vueltax.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Upsert
    suspend fun upsert(trip: TripEntity): Long

    @Query("SELECT * FROM trip ORDER BY arrivalDate DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("DELETE FROM trip WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TravelerDao {
    @Upsert
    suspend fun upsert(traveler: TravelerEntity): Long

    @Query("SELECT * FROM traveler LIMIT 1")
    fun observe(): Flow<TravelerEntity?>
}

@Dao
interface PurchaseDao {
    @Upsert
    suspend fun upsert(purchase: PurchaseEntity): Long

    @Query("SELECT * FROM purchase WHERE tripId = :tripId ORDER BY purchaseDate DESC")
    fun observeByTrip(tripId: Long): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchase WHERE id = :id")
    suspend fun get(id: Long): PurchaseEntity?

    @Query("DELETE FROM purchase WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RefundRequestDao {
    @Upsert
    suspend fun upsert(request: RefundRequestEntity): Long

    @Query("SELECT * FROM refund_request WHERE tripId = :tripId")
    fun observeByTrip(tripId: Long): Flow<List<RefundRequestEntity>>
}
