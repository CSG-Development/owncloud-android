package com.owncloud.android.extensions

import android.widget.TextView

fun TextView.updateTextIfDiffers(text: String) {
    if (this.text?.toString() != text) {
        this.text = text
    }
}