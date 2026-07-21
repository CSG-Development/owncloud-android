/**
 * ownCloud Android client application
 *
 * @author Fernando Sanz Velasco
 * Copyright (C) 2022 ownCloud GmbH.
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

package com.owncloud.android.presentation.files.filelist

import android.content.Context
import android.util.DisplayMetrics
import android.view.View
import com.owncloud.android.R

/**
 * Dynamically calculates the number of columns for grid mode from grid cell dimens.
 */
class ColumnQuantity(context: Context) {

    private var width: Int = 0
    private var height: Int = 0
    private var remaining: Int = 0
    private var displayMetrics: DisplayMetrics

    init {
        val res = context.resources
        val cell = res.getDimensionPixelSize(R.dimen.item_file_grid_width)
        val margin = res.getDimensionPixelSize(R.dimen.item_file_grid_margin)
        width = cell + margin * 2
        height = cell + margin * 2
        displayMetrics = res.displayMetrics
    }

    fun calculateNoOfColumns(parentView: View): Int {
        val totalWidth = parentView.measuredWidth.takeIf { it > 0 } ?: displayMetrics.widthPixels
        var numberOfColumns = totalWidth.div(width)
        remaining = totalWidth.minus(numberOfColumns.times(width))
        if (remaining.div(numberOfColumns.times(2)) < 15) {
            numberOfColumns.minus(1)
            remaining = displayMetrics.widthPixels.minus(numberOfColumns.times(width))
        }
        return numberOfColumns
    }
}
