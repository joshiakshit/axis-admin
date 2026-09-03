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
    @Singleton
    @Named("icloud")
    fun provideIcloudRetrofit(
        client: OkHttpClient,
        json: Json,
    ): Retrofit {
        val authenticated =
            client.newBuilder().addInterceptor { chain ->
                val request =
                    chain.request().newBuilder()
                        .header("authorization", BuildConfig.ICLOUD_API_TOKEN)
                        .header("accept", "application/json")
                        .header("referer", "api.icloudems.com")
                        .header("user-agent", "Axis Admin Android")
                        .build()
                chain.proceed(request)
            }.build()
        return Retrofit.Builder()
            .baseUrl("https://api.icloudems.com/")
            .client(authenticated)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    fun provideAdminApi(
        @Named("admin") retrofit: Retrofit,
    ): AdminApi = retrofit.create(AdminApi::class.java)

    @Provides
    fun provideIcloudApi(
        @Named("icloud") retrofit: Retrofit,
    ): IcloudAuthApi = retrofit.create(IcloudAuthApi::class.java)

    private fun normalized(url: String): String = if (url.endsWith('/')) url else "$url/"
}
