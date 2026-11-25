package com.sahyog.core.data.di

import com.sahyog.core.data.BuildConfig
import com.sahyog.core.data.network.AuthInterceptor
import com.sahyog.core.data.network.FirebaseIdTokenProvider
import com.sahyog.core.data.network.IdTokenProvider
import com.sahyog.core.data.network.SahyogApi
import com.sahyog.core.data.network.TokenRefreshAuthenticator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** The HTTP client for the Sahyog API. No request logging: bodies hold addresses and tokens. */
@Module
@InstallIn(SingletonComponent::class)
internal interface NetworkModule {

    @Binds
    fun bindsIdTokenProvider(impl: FirebaseIdTokenProvider): IdTokenProvider

    companion object {
        @Provides
        @Singleton
        fun providesOkHttpClient(auth: AuthInterceptor, refresh: TokenRefreshAuthenticator): OkHttpClient =
            OkHttpClient.Builder()
                .addInterceptor(auth)
                .authenticator(refresh)
                .connectTimeout(15, TimeUnit.SECONDS)
                // ID photos (up to 10 MB) on a slow connection.
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

        @Provides
        @Singleton
        fun providesSahyogApi(client: OkHttpClient): SahyogApi = createSahyogApi(BuildConfig.API_BASE_URL, client)
    }
}

/** The API client; also used by tests against a local mock server. */
internal fun createSahyogApi(baseUrl: String, client: OkHttpClient): SahyogApi {
    val json = Json {
        // Newer servers may add fields; older apps must keep working.
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SahyogApi::class.java)
}
