package com.owncloud.android.ui.helpers

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentScannerUploadHelper {

    private val formatter = SimpleDateFormat("'Scan_'yyyy-MM-dd_HHmmss", Locale.US)

    data class ScanUploadResult(
        val filePaths: List<Pair<String, String>>,
        val contentUris: List<Pair<Uri, String>>,
    ) {
        fun isEmpty(): Boolean = filePaths.isEmpty() && contentUris.isEmpty()
    }

    private val scannerOptions: GmsDocumentScannerOptions = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(PAGE_LIMIT)
        .setResultFormats(RESULT_FORMAT_PDF)
        //.setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
        .setScannerMode(SCANNER_MODE_FULL)
        .build()

    fun startScan(
        activity: Activity,
        onReady: (IntentSender) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        GmsDocumentScanning.getClient(scannerOptions)
            .getStartScanIntent(activity)
            .addOnSuccessListener(onReady)
            .addOnFailureListener(onError)
    }

    fun parseResult(data: Intent?): ScanUploadResult {
        val result = GmsDocumentScanningResult.fromActivityResultIntent(data)
            ?: return ScanUploadResult(emptyList(), emptyList())
        val filePaths = mutableListOf<Pair<String, String >>()
        val contentUris = mutableListOf<Pair<Uri, String>>()
        result.pages?.forEach { page ->
            page.imageUri?.let { addUri(it, filePaths, contentUris) }
        }
        result.pdf?.uri?.let { addUri(it, filePaths, contentUris) }
        return ScanUploadResult(filePaths, contentUris)
    }

    private fun addUri(uri: Uri, filePaths: MutableList<Pair<String, String>>, contentUris: MutableList<Pair<Uri, String>>) {
        val extension = uri.path?.substringAfterLast(".") ?: ""
        val remoteFileName = formatter.format(Date()) + "." + extension
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { filePaths.add(it to remoteFileName) }
            ContentResolver.SCHEME_CONTENT -> contentUris.add(uri to remoteFileName)
        }
    }

    companion object {
        private const val PAGE_LIMIT = 20
    }
}
