package com.owncloud.android.data.appupdate.datasources

import com.owncloud.android.domain.appupdate.AppUpdate
import com.owncloud.android.domain.exceptions.AppUpdateException

class AppUpdateRepository(
    private val appUpdateService: AppUpdateService
) {

    suspend fun checkForUpdate(): AppUpdate {
        try {
            val appUpdateResponse = appUpdateService.checkForUpdate()
            return AppUpdate(
                latestVersionCode = appUpdateResponse.versionCode,
                latestVersionName = appUpdateResponse.versionName.orEmpty(),
                updateUrl = appUpdateResponse.updateUrl.orEmpty(),
                releaseDate = appUpdateResponse.releaseDate.orEmpty()
            )
        } catch (e: Throwable) {
            throw AppUpdateException(e)
        }
    }
}