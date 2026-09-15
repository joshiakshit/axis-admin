package com.ash.axis.admin.data

import com.ash.axis.admin.BuildConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {
    @Binds
    abstract fun bindSessionStore(store: EncryptedSessionStore): SessionStore
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

    @Provides
    @Singleton
    fun provideClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    @Named("admin")
    fun provideAdminRetrofit(
        client: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(normalized(BuildConfig.AXIS_BACKEND_URL.ifBlank { "https://axis.invalid/" }))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    fun provideAdminApi(
        @Named("admin") retrofit: Retrofit,
    ): AdminApi = retrofit.create(AdminApi::class.java)

    private fun normalized(url: String): String = if (url.endsWith('/')) url else "$url/"
}
