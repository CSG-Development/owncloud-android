package com.owncloud.android.workers

import androidx.work.Data
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.round

object ZipPhase {
    const val COLLECT = "collect"
    const val DOWNLOAD = "download"
    const val BUILD = "build"
    const val UPLOAD = "upload"

    val DEFAULT_WEIGHTS = mapOf(
        COLLECT to 10,
        DOWNLOAD to 30,
        BUILD to 30,
        UPLOAD to 30,
    )

    val NO_DOWNLOAD_WEIGHTS = mapOf(
        COLLECT to 10,
        BUILD to 30,
        UPLOAD to 60,
    )
}

object UnzipPhase {
    const val DOWNLOAD = "download"
    const val EXTRACT = "extract"
    const val CREATE_FOLDER = "create_folder"
    const val UPLOAD = "upload"

    val DEFAULT_WEIGHTS = mapOf(
        DOWNLOAD to 25,
        EXTRACT to 25,
        CREATE_FOLDER to 5,
        UPLOAD to 45,
    )

    val NO_DOWNLOAD_WEIGHTS = mapOf(
        EXTRACT to 35,
        CREATE_FOLDER to 5,
        UPLOAD to 60,
    )
}

class ArchiveOperationProgress(
    private val scope: CoroutineScope,
    private val reportProgress: suspend (Data) -> Unit,
) {
    private val phases = mutableMapOf<String, PhaseRange>()
    private val lastReportedPercent = AtomicInteger(-1)

    fun configurePhases(phaseWeights: Map<String, Int>) {
        phases.clear()
        val activePhases = phaseWeights.filter { it.value > 0 }
        var currentStart = 0
        activePhases.forEach { (phase, weight) ->
            val end = currentStart + weight
            phases[phase] = PhaseRange(start = currentStart, end = end)
            currentStart = end
        }
    }

    fun reportStart() {
        reportPercent(0)
    }

    fun reportPhaseProgress(phase: String, fraction: Double) {
        val range = phases[phase] ?: return
        val clampedFraction = fraction.coerceIn(0.0, 1.0)
        val percent = range.start + round((range.end - range.start) * clampedFraction).toInt()
        reportPercent(percent.coerceIn(0, 99))
    }

    fun completePhase(phase: String) {
        val range = phases[phase] ?: return
        reportPercent(range.end.coerceIn(0, 99))
    }

    fun reportComplete() {
        reportPercent(100)
    }

    private fun reportPercent(percent: Int) {
        var previous: Int
        do {
            previous = lastReportedPercent.get()
            if (percent <= previous) return
        } while (!lastReportedPercent.compareAndSet(previous, percent))

        scope.launch {
            if (percent < lastReportedPercent.get()) return@launch
            reportProgress(workDataOf(DownloadFileWorker.WORKER_KEY_PROGRESS to percent))
        }
    }

    private data class PhaseRange(
        val start: Int,
        val end: Int,
    )

    companion object {
        fun forWorker(
            scope: CoroutineScope,
            setProgress: suspend (Data) -> Unit,
        ): ArchiveOperationProgress =
            ArchiveOperationProgress(scope = scope, reportProgress = setProgress)
    }
}
