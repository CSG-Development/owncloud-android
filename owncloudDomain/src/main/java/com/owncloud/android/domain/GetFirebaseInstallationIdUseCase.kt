package com.owncloud.android.domain

import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.tasks.await

class GetFirebaseInstallationIdUseCase {

    suspend fun getInstallationId(): String {
        return FirebaseInstallations.getInstance().id.await()
    }
}