/*
 * ownCloud Android client application
 *
 * @author Fernando Sanz Velasco
 * Copyright (C) 2021 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.owncloud.android.extensions

import android.content.Context
import android.os.Build
import com.owncloud.android.R
import com.owncloud.android.utils.DisplayUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun File.toLegibleStringSize(context: Context): String {
    val bytes = if (!exists()) 0L else length()
    return DisplayUtils.bytesToHumanReadable(bytes, context, true)
}

fun File.humanReadableModificationDateTime(context: Context): String {
    val dateTime = Date(this.lastModified())
    val dateString = SimpleDateFormat("yyyy-MMM-dd", Locale.getDefault()).format(dateTime)
    val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(dateTime)
    return context.getString(R.string.homecloud_settings_logs_datetime_pattern, dateString, timeString)
}

fun File.replaceContentsOf(destinationPath: String?) {
    val storagePath = destinationPath?.takeIf { it.isNotBlank() }
        ?: error("Cannot replace local file, missing storage path")
    val destination = File(storagePath)
    if (destination.absolutePath == absolutePath) return

    val parent = destination.parentFile
        ?: error("Cannot replace local file, missing parent directory")
    val temp = File(parent, "${destination.name}.edit-tmp")
    try {
        copyTo(temp, overwrite = true)
        moveReplacing(source = temp, destination = destination)
    } catch (throwable: Throwable) {
        temp.delete()
        throw throwable
    }
}

private fun moveReplacing(source: File, destination: File) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Files.move(
            source.toPath(),
            destination.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } else if (!source.renameTo(destination)) {
        error("Cannot replace local file at ${destination.absolutePath}")
    }
}
