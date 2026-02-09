package com.owncloud.android.data.appupdate.datasources

import com.owncloud.android.data.appupdate.remote.AppUpdateResponse
import retrofit2.http.GET

/**
 * Retrofit service interface for App Update Check API
 */
interface AppUpdateService {

    /**
     * Check for available app updates
     *
     * @return Response containing update information
     */
    @GET(APP_UPDATE_CHECK_PATH)
    suspend fun checkForUpdate(): AppUpdateResponse
}

internal const val APP_UPDATE_CHECK_PATH = "curator/app/android/files/files_android_app_update.json"
