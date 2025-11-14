package com.owncloud.android.presentation.authentication.homecloud

import android.os.Build
import android.widget.EditText
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.DrawableCompat

/**
 * Extension function to set the text cursor color for an EditText.
 * @param color The color to apply to the cursor
 */
fun EditText.setTextCursorDrawableCompat(@ColorInt color: Int) {
    try {
        // For API 29+ we can use textCursorDrawable directly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textCursorDrawable?.let { cursorDrawable ->
                val wrappedDrawable = DrawableCompat.wrap(cursorDrawable)
                DrawableCompat.setTint(wrappedDrawable, color)
                textCursorDrawable = wrappedDrawable
            }
        } else {
            // For older versions, use reflection
            val field = android.widget.TextView::class.java.getDeclaredField("mCursorDrawableRes")
            field.isAccessible = true
            val drawableResId = field.getInt(this)
            
            val editorField = android.widget.TextView::class.java.getDeclaredField("mEditor")
            editorField.isAccessible = true
            val editor = editorField.get(this)
            
            val drawables = arrayOfNulls<android.graphics.drawable.Drawable>(2)
            drawables[0] = context.getDrawable(drawableResId)
            drawables[1] = context.getDrawable(drawableResId)
            
            drawables.forEach { drawable ->
                drawable?.let {
                    DrawableCompat.setTint(it, color)
                }
            }
            
            val cursorDrawableField = editor.javaClass.getDeclaredField("mCursorDrawable")
            cursorDrawableField.isAccessible = true
            cursorDrawableField.set(editor, drawables)
        }
    } catch (e: Exception) {
        // If reflection fails, log the error but don't crash
        e.printStackTrace()
    }
}

