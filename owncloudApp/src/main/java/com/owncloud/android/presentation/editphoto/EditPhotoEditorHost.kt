package com.owncloud.android.presentation.editphoto

import android.graphics.Color
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
import ja.burhanrashid52.photoeditor.ViewType
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import ja.burhanrashid52.photoeditor.shape.ShapeType
import timber.log.Timber
import java.io.File

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
