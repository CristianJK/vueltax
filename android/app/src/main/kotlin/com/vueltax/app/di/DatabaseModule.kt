package com.vueltax.app.di

import android.content.Context
import androidx.room.Room
import com.vueltax.app.data.local.AppDatabase
import com.vueltax.app.data.local.DatabasePassphraseProvider
import com.vueltax.app.data.local.PurchaseDao
import com.vueltax.app.data.local.RateDao
import com.vueltax.app.data.local.RefundPointDao
import com.vueltax.app.data.local.RefundRequestDao
import com.vueltax.app.data.local.RuleSetDao
import com.vueltax.app.data.local.TravelerDao
import com.vueltax.app.data.local.TripDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider,
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, AppDatabase::class.java, DatabasePassphraseProvider.DATABASE_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphraseProvider.get()))
            .build()
    }

    @Provides
    fun provideTripDao(database: AppDatabase): TripDao = database.tripDao()

    @Provides
    fun provideTravelerDao(database: AppDatabase): TravelerDao = database.travelerDao()

    @Provides
    fun providePurchaseDao(database: AppDatabase): PurchaseDao = database.purchaseDao()

    @Provides
    fun provideRefundRequestDao(database: AppDatabase): RefundRequestDao =
        database.refundRequestDao()

    @Provides
    fun provideRuleSetDao(database: AppDatabase): RuleSetDao = database.ruleSetDao()

    @Provides
    fun provideRateDao(database: AppDatabase): RateDao = database.rateDao()

    @Provides
    fun provideRefundPointDao(database: AppDatabase): RefundPointDao = database.refundPointDao()
}
