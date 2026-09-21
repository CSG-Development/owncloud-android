package com.owncloud.android.presentation.editphoto

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import ja.burhanrashid52.photoeditor.OnPhotoEditorListener
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import ja.burhanrashid52.photoeditor.PhotoFilter
import ja.burhanrashid52.photoeditor.SaveSettings
import ja.burhanrashid52.photoeditor.ViewType
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import ja.burhanrashid52.photoeditor.shape.ShapeType
import timber.log.Timber
import java.io.File
import kotlin.math.roundToInt

class PhotoEditorHandle {
    var photoEditor: PhotoEditor? = null
        internal set
    var photoEditorView: PhotoEditorView? = null
        internal set

    fun applyShape(
        shapeType: ShapeType,
        color: Int,
        size: Float,
        opacity: Int,
    ) {
        val editor = photoEditor ?: return
        editor.setBrushDrawingMode(true)
        editor.setShape(
            ShapeBuilder()
                .withShapeType(shapeType)
                .withShapeColor(color)
                .withShapeSize(size)
                .withShapeOpacity(opacity),
        )
    }

    fun enableEraser(size: Float) {
        val editor = photoEditor ?: return
        editor.setBrushEraserSize(size)
        editor.brushEraser()
    }

    fun disableDrawing() {
        photoEditor?.setBrushDrawingMode(false)
    }

    fun addText(text: String, color: Int) {
        photoEditor?.addText(text, color)
    }

    fun editText(view: View, text: String, color: Int) {
        photoEditor?.editText(view, text, color)
    }

    fun addEmoji(emoji: String) {
        photoEditor?.addEmoji(emoji)
    }

    fun setFilter(filter: PhotoFilter) {
        photoEditor?.setFilterEffect(filter)
    }

    fun undo(): Boolean = photoEditor?.undo() ?: true

    fun redo(): Boolean = photoEditor?.redo() ?: true

    @SuppressLint("MissingPermission")
    suspend fun saveAsFile(
        file: File,
        compressFormat: Bitmap.CompressFormat,
    ): Exception? {
        val editor = photoEditor ?: return IllegalStateException("PhotoEditor is not ready")
        val editorView = photoEditorView ?: return IllegalStateException("PhotoEditorView is not ready")
        val saveSettings = SaveSettings.Builder()
            .setCompressFormat(compressFormat)
            .setCompressQuality(SAVE_COMPRESS_QUALITY)
            .setClearViewsEnabled(false)
            .build()
        return try {
            val captured = editor.saveAsBitmap(saveSettings)
            val cropped = cropToDisplayedSourceImage(captured, editorView)
            try {
                file.outputStream().use { stream ->
                    if (!cropped.compress(compressFormat, SAVE_COMPRESS_QUALITY, stream)) {
                        error("Failed to compress edited photo")
                    }
                }
                null
            } finally {
                recycleIfMutableAndUnused(captured, cropped)
                recycleIfMutableAndUnused(cropped, keep = null)
            }
        } catch (error: Exception) {
            error
        }
    }

    private fun cropToDisplayedSourceImage(
        bitmap: Bitmap,
        photoEditorView: PhotoEditorView,
    ): Bitmap {
        val imageView = photoEditorView.source
        val drawable = imageView.drawable ?: return bitmap
        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return bitmap

        val displayedRect = RectF()
        imageView.imageMatrix.mapRect(
            displayedRect,
            RectF(0f, 0f, intrinsicWidth.toFloat(), intrinsicHeight.toFloat()),
        )
        displayedRect.offset(imageView.left.toFloat(), imageView.top.toFloat())

        val left = displayedRect.left.roundToInt().coerceIn(0, bitmap.width)
        val top = displayedRect.top.roundToInt().coerceIn(0, bitmap.height)
        val right = displayedRect.right.roundToInt().coerceIn(0, bitmap.width)
        val bottom = displayedRect.bottom.roundToInt().coerceIn(0, bitmap.height)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return bitmap
        if (left == 0 && top == 0 && width == bitmap.width && height == bitmap.height) {
            return bitmap
        }
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    private fun recycleIfMutableAndUnused(bitmap: Bitmap, keep: Bitmap?) {
        if (bitmap === keep || bitmap.isRecycled || !bitmap.isMutable) return
        bitmap.recycle()
    }

    private companion object {
        const val SAVE_COMPRESS_QUALITY = 100
    }
}

@Composable
fun EditPhotoEditorHost(
    localFilePath: String,
    handle: PhotoEditorHandle,
    onImageLoaded: (success: Boolean) -> Unit,
    onEditTextRequested: (view: View, text: String, color: Int) -> Unit,
    onHistoryChanged: (canUndo: Boolean, canRedo: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onImageLoadedState = rememberUpdatedState(onImageLoaded)
    val onEditTextRequestedState = rememberUpdatedState(onEditTextRequested)
    val onHistoryChangedState = rememberUpdatedState(onHistoryChanged)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PhotoEditorView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(Color.BLACK)
                val editor = PhotoEditor.Builder(context, this)
                    .setPinchTextScalable(true)
                    .setClipSourceImage(true)
                    .build()
                handle.photoEditorView = this
                handle.photoEditor = editor
                editor.setOnPhotoEditorListener(
                    object : OnPhotoEditorListener {
                        override fun onEditTextChangeListener(rootView: View, text: String, colorCode: Int) {
                            onEditTextRequestedState.value(rootView, text, colorCode)
                        }

                        override fun onAddViewListener(viewType: ViewType, numberOfAddedViews: Int) {
                            onHistoryChangedState.value(true, false)
                        }

                        override fun onRemoveViewListener(viewType: ViewType, numberOfAddedViews: Int) {
                            onHistoryChangedState.value(numberOfAddedViews > 0, true)
                        }

                        override fun onStartViewChangeListener(viewType: ViewType) = Unit

                        override fun onStopViewChangeListener(viewType: ViewType) = Unit

                        override fun onTouchSourceImage(event: MotionEvent) = Unit
                    },
                )
            }
        },
        update = { photoEditorView ->
            if (localFilePath != photoEditorView.tag) {
                photoEditorView.tag = localFilePath
                Glide.with(photoEditorView)
                    .load(File(localFilePath))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .listener(
                        object : RequestListener<Drawable?> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any,
                                target: Target<Drawable?>,
                                isFirstResource: Boolean,
                            ): Boolean {
                                Timber.e(e, "Failed to load photo for editing: %s", localFilePath)
                                onImageLoadedState.value(false)
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable?,
                                model: Any,
                                target: Target<Drawable?>,
                                dataSource: DataSource,
                                isFirstResource: Boolean,
                            ): Boolean {
                                onImageLoadedState.value(true)
                                return false
                            }
                        },
                    )
                    .into(photoEditorView.source)
            }
        },
        onRelease = { photoEditorView ->
            Glide.with(photoEditorView.context).clear(photoEditorView.source)
            handle.photoEditor = null
            handle.photoEditorView = null
        },
    )
}
