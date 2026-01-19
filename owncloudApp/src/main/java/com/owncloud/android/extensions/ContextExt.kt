/**
 * ownCloud Android client application
 *
 * @author Abel García de Prada
 * Copyright (C) 2020 ownCloud GmbH.
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
 */
package com.owncloud.android.extensions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import androidx.annotation.RequiresApi
import com.owncloud.android.R

@RequiresApi(Build.VERSION_CODES.O)
fun Context.createNotificationChannel(
    id: String,
    name: String,
    description: String,
    importance: Int
) {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val notificationChannel = NotificationChannel(id, name, importance).apply {
        setDescription(description)
    }

    notificationManager.createNotificationChannel(notificationChannel)
}

fun Context.getAppName(): CharSequence  {
    val appName1 = getString(R.string.homecloud_app_name_1)
    val appName2 = getString(R.string.homecloud_app_name_2)
    val stringBuilder = SpannableStringBuilder()
    stringBuilder.append(appName1)
    stringBuilder.setSpan(
        ForegroundColorSpan(getColor(R.color.homecloud_green)), 0, stringBuilder.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    val startPart2 = stringBuilder.length
    stringBuilder.append(" ")
    stringBuilder.append(appName2)
    stringBuilder.setSpan(
        ForegroundColorSpan(getColor(R.color.homecloud_primary)), startPart2, stringBuilder.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    return stringBuilder
}

val Context.isLandscapeMode: Boolean
    get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

val Context.isTablet: Boolean
    get() = resources.configuration.smallestScreenWidthDp >= 600

val Context.isBigTablet: Boolean
    get() = resources.configuration.smallestScreenWidthDp >= 720