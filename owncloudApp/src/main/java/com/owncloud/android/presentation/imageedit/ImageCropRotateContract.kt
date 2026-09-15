package com.owncloud.android.presentation.imageedit

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import java.io.File

class ImageCropRotateContract : ActivityResultContract<File, File?>() {

    override fun createIntent(context: Context, input: File): Intent =
        ImageCropRotateActivity.createIntent(context, input)

    override fun parseResult(resultCode: Int, intent: Intent?): File? {
        if (resultCode != Activity.RESULT_OK) return null
        val path = intent?.getStringExtra(ImageCropRotateActivity.EXTRA_OUTPUT_PATH) ?: return null
        val file = File(path)
        return file.takeIf { it.exists() }
    }
}
