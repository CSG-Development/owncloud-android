package com.owncloud.android.presentation.authentication.homecloud

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.core.content.withStyledAttributes
import com.owncloud.android.R

class VerificationCodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var codeLength: Int = 6
    private var borderColor: Int = Color.GRAY
    private var borderWidth: Float = 2f
    private var focusBorderColor: Int = Color.BLUE
    private var focusBorderWidth: Float = 3f
    private var cornerRadius: Float = 12f

    private val editTexts = mutableListOf<PasteAwareEditText>()

    var onCodeCompleteListener: ((String) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        context.withStyledAttributes(attrs, R.styleable.VerificationCodeView) {
            codeLength = getInt(R.styleable.VerificationCodeView_codeLength, 6)
            borderColor = getColor(R.styleable.VerificationCodeView_borderColor, Color.GRAY)
            focusBorderColor = getColor(R.styleable.VerificationCodeView_focusBorderColor, Color.BLUE)
            borderWidth = getDimension(R.styleable.VerificationCodeView_borderWidth, 2f)
            focusBorderWidth = getDimension(R.styleable.VerificationCodeView_focusBorderWidth, 3f)
            cornerRadius = getDimension(R.styleable.VerificationCodeView_cornerRadius, 12f)
        }
        createEditTexts()
    }

    private fun createEditTexts() {
        removeAllViews()
        editTexts.clear()

        for (i in 0 until codeLength) {
            val et = createEditText(i)
            addView(et)
            editTexts.add(et)
        }
    }

    private fun createEditText(index: Int): PasteAwareEditText {
        val et = PasteAwareEditText(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
            filters = arrayOf(InputFilter.LengthFilter(1))
            gravity = Gravity.CENTER   // ✅ Center text and cursor
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            isCursorVisible = true
            imeOptions = EditorInfo.IME_ACTION_NEXT
            setBackground(createBorderDrawable(borderColor, borderWidth))
            inputType = EditorInfo.TYPE_CLASS_NUMBER
            textSize = 20f
            setPadding(0, 24, 0, 24)
        }

        et.setOnFocusChangeListener { v, hasFocus ->
            v.background = if (hasFocus)
                createBorderDrawable(focusBorderColor, focusBorderWidth)
            else
                createBorderDrawable(borderColor, borderWidth)
        }

        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
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
            cornerRadius = this@VerificationCodeView.cornerRadius  // ✅ Rounded corners
        }
    }

    fun getCode(): String = editTexts.joinToString("") { it.text.toString() }

    fun setCodeLength(length: Int) {
        codeLength = length
        createEditTexts()
    }

    fun clearCode() {
        editTexts.forEach { it.text?.clear() }
        editTexts.firstOrNull()?.requestFocus()
    }
}
