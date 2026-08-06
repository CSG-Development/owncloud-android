/**
 * ownCloud Android client application
 *
 * @author David González Verdugo
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2023 ownCloud GmbH.
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

package com.owncloud.android.utils

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.resources.files.CheckPathExistenceRemoteOperation

object RemoteFileUtils {
    /**
     * Checks if remotePath does not exist in the server and returns it, or adds
     * a suffix to it in order to avoid the server file is overwritten.
     *
     * @param ownCloudClient
     * @param remotePath
     * @param excludedRemotePaths paths already reserved locally (e.g. pending uploads) that must
     * also be treated as taken even if they do not exist on the server yet
     * @return
     */
    fun getAvailableRemotePath(
        ownCloudClient: OwnCloudClient,
        remotePath: String,
        spaceWebDavUrl: String? = null,
        isUserLogged: Boolean,
        excludedRemotePaths: Collection<String> = emptyList(),
    ): String {
        val excluded = excludedRemotePaths.toHashSet()
        fun isTaken(path: String): Boolean =
            path in excluded ||
                existsFile(
                    ownCloudClient = ownCloudClient,
                    remotePath = path,
                    spaceWebDavUrl = spaceWebDavUrl,
                    isUserLogged = isUserLogged,
                )

        if (!isTaken(remotePath)) {
            return remotePath
        }
        val pos = remotePath.lastIndexOf(".")
        var suffix: String
        var extension = ""
        if (pos >= 0) {
            extension = remotePath.substring(pos + 1)
            remotePath.apply {
                substring(0, pos)
            }
        }
        var count = 1
        var candidate: String
        do {
            suffix = " ($count)"
            candidate = if (pos >= 0) {
                "${remotePath.substringBeforeLast('.', "")}$suffix.$extension"
            } else {
                remotePath + suffix
            }
            count++
        } while (isTaken(candidate))
        return candidate
    }

    private fun existsFile(
        ownCloudClient: OwnCloudClient,
        remotePath: String,
        spaceWebDavUrl: String?,
        isUserLogged: Boolean,
    ): Boolean {
        val existsOperation =
            CheckPathExistenceRemoteOperation(
                remotePath = remotePath,
                isUserLoggedIn = isUserLogged,
                spaceWebDavUrl = spaceWebDavUrl,
            )
        return existsOperation.execute(ownCloudClient).isSuccess
    }
}
