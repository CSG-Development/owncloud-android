package com.owncloud.android.dependecyinjection

import com.owncloud.android.BuildConfig
import com.owncloud.android.data.appupdate.datasources.AppUpdateRepository
import com.owncloud.android.data.appupdate.datasources.AppUpdateService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Constants for Koin named qualifiers
 */
private object AppUpdateQualifiers {
    const val BASE_URL = "appUpdateBaseUrl"
    const val LOGGING_INTERCEPTOR = "appUpdateLoggingInterceptor"
    const val OKHTTP_CLIENT = "appUpdateOkHttpClient"
    const val RETROFIT = "appUpdateRetrofit"
}

/**
 * Dependency injection module for App Update Check API
 */
val appUpdateModule = module {

    // Base URL for App Update API
    single(named(AppUpdateQualifiers.BASE_URL)) {
        BuildConfig.APP_UPDATE_BASE_URL
    }

    // Logging interceptor for debugging
    single(named(AppUpdateQualifiers.LOGGING_INTERCEPTOR)) {
        HttpLoggingInterceptor { message ->
            Timber.tag("AppUpdateAPI").d(message)
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    // OkHttpClient for App Update API
    single(named(AppUpdateQualifiers.OKHTTP_CLIENT)) {
        OkHttpClient.Builder()
            .addInterceptor(get<HttpLoggingInterceptor>(named(AppUpdateQualifiers.LOGGING_INTERCEPTOR)))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // Retrofit instance for App Update API
    single(named(AppUpdateQualifiers.RETROFIT)) {
        Retrofit.Builder()
            .baseUrl(get<String>(named(AppUpdateQualifiers.BASE_URL)))
            .client(get(named(AppUpdateQualifiers.OKHTTP_CLIENT)))
            .addConverterFactory(MoshiConverterFactory.create(get()))
            .build()
    }

    // App Update Service
    single {
        get<Retrofit>(named(AppUpdateQualifiers.RETROFIT)).create(AppUpdateService::class.java)
    }

    single<AppUpdateRepository> { AppUpdateRepository(get()) }
}
