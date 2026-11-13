package com.vueltax.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import retrofit2.HttpException

@HiltWorker
class ReferenceDataSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val synchronizer: ReferenceDataSynchronizer,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        synchronizer.sync()
        Result.success()
    } catch (e: IOException) {
        Log.w(TAG, "Sin conexión con la API; se reintentará", e)
        Result.retry()
    } catch (e: HttpException) {
        Log.w(TAG, "La API respondió ${e.code()}", e)
        if (e.code() >= HTTP_SERVER_ERROR) Result.retry() else Result.failure()
    }

    private companion object {
        const val TAG = "ReferenceDataSync"
        const val HTTP_SERVER_ERROR = 500
    }
}
