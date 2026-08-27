package com.owncloud.android.presentation.authentication.homecloud

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.owncloud.android.R

class VerificationCodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val codeLength: Int
    private val borderColor: Int
    private val borderWidth: Float
    private val focusBorderColor: Int
    private val focusBorderWidth: Float

    private val errorTextColor: Int
    private val cornerRadius: Float
    private val digitTextSize: Float = 20f
    private val cursorColor: Int

    private val focusedBorder: GradientDrawable

    private val defaultBorder: GradientDrawable

    private val errorBorder: GradientDrawable

    private val editTexts = mutableListOf<PasteAwareEditText>()
    private val digitsContainer: LinearLayout
    private val errorTextView: TextView
    private val paint = Paint().apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            digitTextSize,
            context.resources.displayMetrics
        )
    }

    var onCodeCompleteListener: ((String) -> Unit)? = null

    var onCodeChangedListener: ((String) -> Unit)? = null

    private var isUpdatingProgrammatically = false

    init {
        orientation = VERTICAL

        digitsContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        addView(digitsContainer)

        errorTextView = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8
                leftMargin = 16
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            isVisible = false
        }
        addView(errorTextView)

        val attrs = context.obtainStyledAttributes(attrs, R.styleable.VerificationCodeView)

        try {
            codeLength = attrs.getInt(R.styleable.VerificationCodeView_codeLength, 6)
            borderColor = attrs.getColor(
                R.styleable.VerificationCodeView_borderColor,
                ContextCompat.getColor(context, R.color.homecloud_input_stroke_nonfocused)
            )
            focusBorderColor = attrs.getColor(
                R.styleable.VerificationCodeView_focusBorderColor,
                ContextCompat.getColor(context, R.color.homecloud_input_stroke_focused)
            )
            borderWidth = attrs.getDimension(R.styleable.VerificationCodeView_borderWidth, 2f)
            focusBorderWidth = attrs.getDimension(R.styleable.VerificationCodeView_focusBorderWidth, 3f)
            cornerRadius = attrs.getDimension(R.styleable.VerificationCodeView_cornerRadius, 12f)
            errorTextColor = attrs.getColor(
                R.styleable.VerificationCodeView_errorTextColor,
                ContextCompat.getColor(context, R.color.homecloud_error)
            )
            cursorColor = attrs.getColor(R.styleable.VerificationCodeView_cursorColor, focusBorderColor)
            defaultBorder = createBorderDrawable(borderColor, borderWidth)
            focusedBorder = createBorderDrawable(focusBorderColor, focusBorderWidth)
            errorBorder = createBorderDrawable(errorTextColor, borderWidth)
        } finally {
            attrs.recycle()
        }

        errorTextView.setTextColor(errorTextColor)
        createEditTexts()
    }

    private fun createEditTexts() {
        digitsContainer.removeAllViews()
        editTexts.clear()

        for (i in 0 until codeLength) {
            val et = createEditText(i)
            digitsContainer.addView(et)
            if (i < codeLength - 1) {
                digitsContainer.addView(createSpace())
            }
            editTexts.add(et)
        }
    }

    private fun createSpace(): Space {
        val space = Space(context).apply {
            layoutParams = LayoutParams(0, 56.dpToPx(), 1f)
        }
        return space
    }

    private fun createEditText(index: Int): PasteAwareEditText {
        val size = resources.getDimensionPixelSize(R.dimen.homecloud_code_edit_size)
        val et = PasteAwareEditText(context).apply {
            layoutParams = LayoutParams(size, LayoutParams.MATCH_PARENT).apply {
                marginEnd = if (index < codeLength - 1) 8 else 0
            }

            minWidth = calculateMinDigitWidth()
            minHeight = 56.dpToPx()

            filters = arrayOf(InputFilter.LengthFilter(1))
            gravity = Gravity.CENTER
            textAlignment = TEXT_ALIGNMENT_CENTER
            isCursorVisible = true
            imeOptions = EditorInfo.IME_ACTION_NEXT or
                    EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                    EditorInfo.IME_FLAG_NO_FULLSCREEN
            background = createBorderDrawable(borderColor, borderWidth)
            inputType = EditorInfo.TYPE_CLASS_NUMBER
            textSize = digitTextSize
            setPadding(0, 24, 0, 24)
            setTextCursorDrawableCompat(cursorColor)
        }

        et.setOnFocusChangeListener { v, hasFocus ->
            if (!errorTextView.isVisible) {
                v.background = if (hasFocus)
                    focusedBorder
                else
                    defaultBorder
            }
        }

        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingProgrammatically) return

                if (errorTextView.isVisible) {
                    clearError()
                }

                if (!s.isNullOrEmpty()) {
                    if (index < codeLength - 1) {
                        editTexts[index + 1].requestFocus()
                    } else {
                        val code = getCode()
                        if (code.length == codeLength) {
                            onCodeCompleteListener?.invoke(code)
                        }
                    }
                }
                onCodeChangedListener?.invoke(getDigitSlots())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        et.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                if (et.text.isNullOrEmpty() && index > 0) {
                    editTexts[index - 1].apply {
                        text?.clear()
                        requestFocus()
                    }
                }
            }
            false
        }

        et.setOnPasteListener { pasted ->
            val clean = pasted.filter { it.isDigit() }.take(codeLength)
            for (i in clean.indices) {
                editTexts[i].setText(clean[i].toString())
            }
            if (clean.length == codeLength) {
                onCodeCompleteListener?.invoke(clean)
            }
        }

        return et
    }

    private fun createBorderDrawable(color: Int, width: Float): GradientDrawable {
        return GradientDrawable().apply {
            setStroke(width.toInt(), color)
            cornerRadius = this@VerificationCodeView.cornerRadius
        }
    }

    fun getCode(): String = editTexts.joinToString("") { it.text.toString() }

    /**
     * Digit per box, same length as [codeLength]. Empty boxes are a non-digit placeholder
     * so a theme recreation can restore gaps in the middle (e.g. 1 2 _ _ 3 4).
     */
    fun getDigitSlots(): String = buildString(codeLength) {
        editTexts.forEach { et ->
            val digit = et.text?.singleOrNull()
            append(if (digit != null && digit.isDigit()) digit else EMPTY_DIGIT)
        }
    }

    fun isCodeComplete(): Boolean = getCode().length == codeLength

    fun setCode(code: String) {
        isUpdatingProgrammatically = true
        try {
            editTexts.forEachIndexed { index, et ->
                val char = code.getOrNull(index)
                val digit = if (char != null && char.isDigit()) char.toString() else ""
                if (et.text?.toString() != digit) {
                    et.setText(digit)
                }
            }
        } finally {
            isUpdatingProgrammatically = false
        }
    }

    fun clearCode() {
        isUpdatingProgrammatically = true
        try {
            editTexts.forEach { it.text?.clear() }
        } finally {
            isUpdatingProgrammatically = false
        }
        editTexts.firstOrNull()?.requestFocus()
        onCodeChangedListener?.invoke(getDigitSlots())
    }

    fun setError(errorMessage: String) {
        errorTextView.isVisible = true
        errorTextView.text = errorMessage
        setErrorBorder()
    }

    private fun resetBorder() {
        editTexts.forEach { it.background = if (it.hasFocus()) focusedBorder else defaultBorder }
    }

    private fun setErrorBorder() {
        editTexts.forEach { it.background = errorBorder }
    }

    fun clearError() {
        errorTextView.text = null
        errorTextView.isVisible = false
        resetBorder()
    }

    private fun calculateMinDigitWidth(): Int {
        // Measure width of "0" which is typically one of the widest digits
        val textWidth = paint.measureText("0")

        // Add padding and ensure minimum size
        val minWidth = (textWidth + 32.dpToPx()).toInt().coerceAtLeast(40.dpToPx())

        return minWidth
    }

    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val EMPTY_DIGIT = ' '
    }
}
