package com.owncloud.android.workers.unzip

import com.squareup.moshi.Moshi
import timber.log.Timber
import java.io.File
import java.util.UUID

class UnzipWorkerStateStore(
    private val temporalFolderPath: String,
    private val workerId: UUID,
) {

    private val stateFile: File
        get() = File(temporalFolderPath, "unzip_worker_state_$workerId.json")

    private val adapter by lazy {
        Moshi.Builder()
            .build()
            .adapter(UnzipWorkerPersistedState::class.java)
    }

    fun load(): UnzipWorkerPersistedState? {
        val file = stateFile
        if (!file.exists()) return null

        return try {
            val json = file.readText()
            adapter.fromJson(json).also { state ->
                if (state == null) {
                    Timber.w("Unzip worker state file is empty: ${file.absolutePath}")
                }
            }
        } catch (exception: Exception) {
            Timber.w(exception, "Failed to read unzip worker state: ${file.absolutePath}")
            null
        }
    }

    fun save(state: UnzipWorkerPersistedState) {
        val file = stateFile
        file.parentFile?.mkdirs()
        val tmpFile = File(file.parentFile, "${file.name}.tmp")
        try {
            tmpFile.writeText(adapter.toJson(state))
            if (!tmpFile.renameTo(file)) {
                tmpFile.copyTo(file, overwrite = true)
                tmpFile.delete()
            }
        } catch (exception: Exception) {
            Timber.w(exception, "Failed to save unzip worker state: ${file.absolutePath}")
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
        }
    }

    fun clear() {
        val file = stateFile
        if (file.exists() && !file.delete()) {
            Timber.w("Failed to delete unzip worker state: ${file.absolutePath}")
        }
        val tmpFile = File(file.parentFile, "${file.name}.tmp")
        if (tmpFile.exists()) {
            tmpFile.delete()
        }
    }
}
