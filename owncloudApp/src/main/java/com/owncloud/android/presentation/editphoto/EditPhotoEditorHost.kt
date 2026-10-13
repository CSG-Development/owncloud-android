package com.owncloud.android.presentation.editphoto

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.media.ExifInterface
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import ja.burhanrashid52.photoeditor.DrawingView
import ja.burhanrashid52.photoeditor.OnPhotoEditorListener
import ja.burhanrashid52.photoeditor.PhotoEditor
import ja.burhanrashid52.photoeditor.PhotoEditorView
import ja.burhanrashid52.photoeditor.PhotoFilter
import ja.burhanrashid52.photoeditor.SaveSettings
import ja.burhanrashid52.photoeditor.TextStyleBuilder
import ja.burhanrashid52.photoeditor.ViewType
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import ja.burhanrashid52.photoeditor.shape.ShapeType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

class PhotoEditorHandle {
    var photoEditor: PhotoEditor? = null
        internal set
    var photoEditorView: PhotoEditorView? = null
        internal set
    var localFilePath: String? = null
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
        // PhotoEditor 3.0.2 createEraserPaint() uses ShapeBuilder.shapeSize, not eraserSize.
        editor.setShape(
            ShapeBuilder()
                .withShapeType(ShapeType.Brush)
                .withShapeSize(size),
        )
        editor.setBrushEraserSize(size)
        // setShape enables drawing and clears isErasing; brushEraser() must run last.
        editor.brushEraser()
    }

    fun disableDrawing() {
        photoEditor?.setBrushDrawingMode(false)
    }

    fun addText(text: String, color: Int) {
        photoEditor?.addText(text, TextStyleBuilder().apply {
            withTextSize(48f)
            withTextColor(color)
        })
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


    fun undo(): Boolean = photoEditor?.undo() ?: false
    fun redo(): Boolean = photoEditor?.redo() ?: false

    @SuppressLint("MissingPermission")
    suspend fun saveAsFile(
        file: File,
        compressFormat: Bitmap.CompressFormat,
    ): Exception? {
        val editor = photoEditor ?: return IllegalStateException("PhotoEditor is not ready")
        val editorView = photoEditorView ?: return IllegalStateException("PhotoEditorView is not ready")
        val originalPath = localFilePath
        var overlayCropped: Bitmap? = null
        var original: Bitmap? = null
        return try {
            overlayCropped = withContext(Dispatchers.Main.immediate) {
                captureMarkupOverlayCropped(editor, editorView)
            }
            original = originalPath?.let { decodeOriginalBitmap(editorView.context, it) }
            val decoded = original
            if (decoded != null) {
                withContext(Dispatchers.IO) {
                    flattenOntoOriginal(decoded, overlayCropped, file, compressFormat)
                }
            } else {
                recycleIfMutableAndUnused(overlayCropped, keep = null)
                overlayCropped = null
                saveFallbackCapture(editor, editorView, file, compressFormat, originalPath)
            }
            null
        } catch (error: CancellationException) {
            recycleIfMutableAndUnused(overlayCropped, keep = null)
            recycleIfMutableAndUnused(original, keep = null)
            throw error
        } catch (error: OutOfMemoryError) {
            Timber.e(error, "OOM while saving edited photo, falling back to view capture")
            recycleIfMutableAndUnused(overlayCropped, keep = null)
            recycleIfMutableAndUnused(original, keep = null)
            try {
                saveFallbackCapture(editor, editorView, file, compressFormat, originalPath)
                null
            } catch (fallback: CancellationException) {
                throw fallback
            } catch (fallback: Throwable) {
                Exception("Failed to save edited photo", fallback)
            }
        } catch (error: Exception) {
            recycleIfMutableAndUnused(overlayCropped, keep = null)
            recycleIfMutableAndUnused(original, keep = null)
            error
        }
    }

    private suspend fun captureMarkupOverlayCropped(
        editor: PhotoEditor,
        editorView: PhotoEditorView,
    ): Bitmap? {
        editor.clearHelperBox()
        val source = editorView.source
        val previousBackground = editorView.background
        val previousVisibility = source.visibility
        editorView.setBackgroundColor(Color.TRANSPARENT)
        source.visibility = View.INVISIBLE
        val overlayFull = try {
            captureView(editorView)
        } finally {
            editorView.background = previousBackground
            source.visibility = previousVisibility
        }
        val overlayCropped = cropToDisplayedSourceImage(overlayFull, editorView)
        recycleIfMutableAndUnused(overlayFull, keep = overlayCropped)
        return overlayCropped
    }

    private suspend fun captureView(view: View): Bitmap {
        return withLiveDrawingLayer(view) {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap
        }
    }

    // DrawingView uses a hardware layer whose software draw replays a cached display list.
    private suspend fun <T> withLiveDrawingLayer(view: View, block: suspend () -> T): T {
        val drawingViews = ArrayList<Pair<DrawingView, Int>>(1)
        collectDrawingViews(view, drawingViews)
        drawingViews.forEach { (drawingView, _) ->
            drawingView.destroyDrawingCache()
            drawingView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        return try {
            block()
        } finally {
            drawingViews.forEach { (drawingView, previousLayerType) ->
                drawingView.setLayerType(previousLayerType, null)
            }
        }
    }

    private fun collectDrawingViews(view: View, drawingViews: MutableList<Pair<DrawingView, Int>>) {
        if (view is DrawingView) {
            drawingViews.add(view to view.layerType)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectDrawingViews(view.getChildAt(index), drawingViews)
            }
        }
    }

    private suspend fun decodeOriginalBitmap(context: Context, path: String): Bitmap? {
        return try {
            withContext(Dispatchers.IO) {
                runInterruptible {
                    Glide.with(context.applicationContext)
                        .asBitmap()
                        .load(File(path))
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .override(Target.SIZE_ORIGINAL)
                        .submit()
                        .get()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Timber.e(error, "Failed to decode original photo for edit save: %s", path)
            null
        }
    }

    private fun flattenOntoOriginal(
        original: Bitmap,
        overlayCropped: Bitmap?,
        file: File,
        compressFormat: Bitmap.CompressFormat,
    ) {
        var output: Bitmap? = null
        try {
            val flattened = if (overlayCropped == null) {
                original
            } else {
                val dest = original.copy(Bitmap.Config.ARGB_8888, true)
                    ?: Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888).also {
                        Canvas(it).drawBitmap(original, 0f, 0f, null)
                    }
                Canvas(dest).drawBitmap(
                    overlayCropped,
                    null,
                    Rect(0, 0, dest.width, dest.height),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
                dest
            }
            output = flattened
            compressToFile(flattened, file, compressFormat)
        } finally {
            recycleIfMutableAndUnused(overlayCropped, keep = output)
            recycleIfMutableAndUnused(original, keep = output)
            recycleIfMutableAndUnused(output, keep = null)
        }
    }

    private suspend fun saveFallbackCapture(
        editor: PhotoEditor,
        editorView: PhotoEditorView,
        file: File,
        compressFormat: Bitmap.CompressFormat,
        originalPath: String?,
    ) {
        val saveSettings = SaveSettings.Builder()
            .setCompressFormat(compressFormat)
            .setCompressQuality(SAVE_COMPRESS_QUALITY)
            .setClearViewsEnabled(false)
            .build()
        val captured = withLiveDrawingLayer(editorView) {
            editor.saveAsBitmap(saveSettings)
        }
        val cropped = cropToDisplayedSourceImage(captured, editorView) ?: captured
        try {
            withContext(Dispatchers.IO) {
                val output = scaleToOriginalBoundsIfNeeded(cropped, originalPath)
                try {
                    compressToFile(output, file, compressFormat)
                } finally {
                    recycleIfMutableAndUnused(captured, keep = output)
                    recycleIfMutableAndUnused(cropped, keep = output)
                    recycleIfMutableAndUnused(output, keep = null)
                }
            }
        } catch (error: Throwable) {
            recycleIfMutableAndUnused(captured, keep = cropped)
            recycleIfMutableAndUnused(cropped, keep = null)
            throw error
        }
    }

    private fun scaleToOriginalBoundsIfNeeded(bitmap: Bitmap, originalPath: String?): Bitmap {
        val (origWidth, origHeight) = decodeOrientedBounds(originalPath) ?: return bitmap
        if (bitmap.width == origWidth && bitmap.height == origHeight) return bitmap
        return Bitmap.createScaledBitmap(bitmap, origWidth, origHeight, true)
    }

    private fun decodeOrientedBounds(path: String?): Pair<Int, Int>? {
        if (path.isNullOrEmpty()) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        var width = options.outWidth
        var height = options.outHeight
        if (width <= 0 || height <= 0) return null
        val orientation = try {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        if (
            orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270
        ) {
            val swapped = width
            width = height
            height = swapped
        }
        return width to height
    }

    private fun compressToFile(
        bitmap: Bitmap,
        file: File,
        compressFormat: Bitmap.CompressFormat,
    ) {
        file.outputStream().use { stream ->
            if (!bitmap.compress(compressFormat, SAVE_COMPRESS_QUALITY, stream)) {
                error("Failed to compress edited photo")
            }
        }
    }

    private fun cropToDisplayedSourceImage(
        bitmap: Bitmap,
        photoEditorView: PhotoEditorView,
    ): Bitmap? {
        val imageView = photoEditorView.source
        val drawable = imageView.drawable ?: return null
        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return null

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
        if (width <= 0 || height <= 0) return null
        if (left == 0 && top == 0 && width == bitmap.width && height == bitmap.height) {
            return bitmap
        }
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    private fun recycleIfMutableAndUnused(bitmap: Bitmap?, keep: Bitmap?) {
        if (bitmap == null || bitmap === keep || bitmap.isRecycled || !bitmap.isMutable) return
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
            val editorView = PhotoEditorView(context).apply {
                setBackgroundColor(Color.BLACK)
                val editor = PhotoEditor.Builder(context, this)
                    .setPinchTextScalable(true)
                    .setClipSourceImage(true)
                    .build()
                handle.photoEditorView = this
                handle.photoEditor = editor
                handle.localFilePath = localFilePath
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
            EditorCanvasHost(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                addView(editorView)
            }
        },
        update = { _ ->
            val photoEditorView = handle.photoEditorView ?: return@AndroidView
            handle.localFilePath = localFilePath
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
                                photoEditorView.post { photoEditorView.requestLayout() }
                                return false
                            }
                        },
                    )
                    .into(photoEditorView.source)
            }
        },
        onRelease = {
            handle.photoEditorView?.let { photoEditorView ->
                Glide.with(photoEditorView).clear(photoEditorView.source)
            }
            handle.photoEditor = null
            handle.photoEditorView = null
            handle.localFilePath = null
        },
    )
}

/**
 * Locks the editor to the photo aspect ratio once the image is loaded, then scales
 * that canvas uniformly when the available space changes. Strokes and text stay on
 * the same parts of the photo, and the photo uses the largest size that fits.
 */
private class EditorCanvasHost(context: Context) : FrameLayout(context) {
    private var canvasWidth = 0
    private var canvasHeight = 0

    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val height = View.MeasureSpec.getSize(heightMeasureSpec)
        val child = getChildAt(0) as? PhotoEditorView
        if (child != null && width > 0 && height > 0) {
            ensureCanvasSize(child, width, height)
            val childWidth = if (canvasWidth > 0) canvasWidth else width
            val childHeight = if (canvasHeight > 0) canvasHeight else height
            child.measure(
                View.MeasureSpec.makeMeasureSpec(childWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(childHeight, View.MeasureSpec.EXACTLY),
            )
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0) ?: return
        val childWidth = child.measuredWidth
        val childHeight = child.measuredHeight
        if (childWidth == 0 || childHeight == 0) return
        val parentWidth = right - left
        val parentHeight = bottom - top
        val childLeft = (parentWidth - childWidth) / 2
        val childTop = (parentHeight - childHeight) / 2
        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
        if (parentWidth == 0 || parentHeight == 0) return
        val scale = min(
            parentWidth / childWidth.toFloat(),
            parentHeight / childHeight.toFloat(),
        )
        child.pivotX = childWidth / 2f
        child.pivotY = childHeight / 2f
        child.scaleX = scale
        child.scaleY = scale
    }

    private fun ensureCanvasSize(
        editor: PhotoEditorView,
        parentWidth: Int,
        parentHeight: Int,
    ) {
        if (canvasWidth > 0 && canvasHeight > 0) return
        val drawable = editor.source.drawable ?: return
        val imageWidth = drawable.intrinsicWidth
        val imageHeight = drawable.intrinsicHeight
        if (imageWidth <= 0 || imageHeight <= 0) return
        val scale = min(
            parentWidth / imageWidth.toFloat(),
            parentHeight / imageHeight.toFloat(),
        )
        if (scale <= 0f) return
        canvasWidth = (imageWidth * scale).roundToInt().coerceAtLeast(1)
        canvasHeight = (imageHeight * scale).roundToInt().coerceAtLeast(1)
    }
}
