package com.owncloud.android.data.appupdate.datasources

import com.owncloud.android.domain.appupdate.AppUpdate
import com.owncloud.android.domain.exceptions.AppUpdateException

class AppUpdateRepository(
    private val appUpdateService: AppUpdateService
) {

    suspend fun checkForUpdate(): AppUpdate {
        try {
//            val appUpdateResponse = appUpdateService.checkForUpdate()
//            return AppUpdate(
//                latestVersionCode = appUpdateResponse.versionCode,
//                latestVersionName = appUpdateResponse.versionName.orEmpty(),
//                updateUrl = appUpdateResponse.updateUrl.orEmpty(),
//                releaseDate = appUpdateResponse.releaseDate.orEmpty()
//            )
            return  AppUpdate(
                latestVersionCode = 100190,
                latestVersionName = "100190",
                updateUrl = "https://softwareupdates.seagate.com/app/1/update/curator/prod/android/photos/apk/app-dev-sideload-universal-release_68f7523140cba.apk",
                releaseDate = "10/01/2026"
            )
        } catch (e: Throwable) {
            throw AppUpdateException(e)
        }
    }
}