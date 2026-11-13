package com.vueltax.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TripEntity::class,
        TravelerEntity::class,
        PurchaseEntity::class,
        RefundRequestEntity::class,
        CachedRuleSetEntity::class,
        CachedRateEntity::class,
        CachedRefundPointEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    abstract fun travelerDao(): TravelerDao

    abstract fun purchaseDao(): PurchaseDao

    abstract fun refundRequestDao(): RefundRequestDao

    abstract fun ruleSetDao(): RuleSetDao

    abstract fun rateDao(): RateDao

    abstract fun refundPointDao(): RefundPointDao
}
