package com.vueltax.app.di

import android.content.Context
import com.vueltax.app.BuildConfig
import com.vueltax.app.data.remote.GetOnlyInterceptor
import com.vueltax.app.data.remote.PublicApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val HTTP_CACHE_DIR = "http_cache"
    private const val HTTP_CACHE_BYTES = 5L * 1024 * 1024
    private const val TIMEOUT_SECONDS = 15L

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    /**
     * La caché de OkHttp aprovecha el `Cache-Control` y el `ETag` de la API: si el dato no
     * cambió, el servidor responde 304 y no se vuelve a descargar. No se registran peticiones.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        getOnlyInterceptor: GetOnlyInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, HTTP_CACHE_DIR), HTTP_CACHE_BYTES))
        .addInterceptor(getOnlyInterceptor)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun providePublicApi(client: OkHttpClient, json: Json): PublicApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PublicApi::class.java)
}
