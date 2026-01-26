package com.owncloud.android.ui.custom

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.owncloud.android.R
import com.owncloud.android.databinding.CustomAutocompleteDropdownBinding
import com.owncloud.android.databinding.CustomAutocompleteItemBinding

class CustomAutoCompleteTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.autoCompleteTextViewStyle
) : AppCompatAutoCompleteTextView(context, attrs, defStyleAttr) {

    private var items: List<DropdownItem<*>> = emptyList()
    private var onItemSelectedListener: OnItemSelectedListener? = null
    var onDropdownDismissListener: OnDropdownDismissListener? = null
    private var anchorView: View? = null
    private var isInputEnabled: Boolean = true

    private val dropdownBackground: Drawable?
    private val dropdownElevation: Float
    private val dropdownMaxHeight: Int
    private var dropdownWidth: Int = ViewGroup.LayoutParams.MATCH_PARENT

    private val dropdownBinding by lazy {
        CustomAutocompleteDropdownBinding.inflate(LayoutInflater.from(context))
    }

    private val dropdownAdapter by lazy {
        DropdownAdapter(
            onItemClick = { item, position ->
                onItemSelectedListener?.onItemSelected(item, position)
                dismissDropdown()
            },
            selectedColorRes = R.color.homecloud_server_selected_background
        )
    }

    private val popupWindow by lazy {
        PopupWindow(
            dropdownBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setOnDismissListener { onDropdownDismissListener?.onDismiss() }
        }
    }

    data class DropdownItem<T>(
        val id: String,
        val text: String,
        val isSelected: Boolean = false,
        val data: T? = null
    )

    fun interface OnItemSelectedListener {
        fun onItemSelected(item: DropdownItem<*>, position: Int)
    }

    fun interface OnDropdownDismissListener {
        fun onDismiss()
    }

    init {
        val typed = context.obtainStyledAttributes(attrs, R.styleable.CustomAutoCompleteTextView)
        try {
            dropdownBackground = typed.getDrawable(R.styleable.CustomAutoCompleteTextView_dropdownBackground)
                ?: ContextCompat.getDrawable(context, R.drawable.bg_custom_autocomplete_dropdown)
            dropdownElevation = typed.getDimension(R.styleable.CustomAutoCompleteTextView_dropdownElevation, 8f)
            dropdownMaxHeight = typed.getDimensionPixelSize(
                R.styleable.CustomAutoCompleteTextView_dropdownMaxHeight,
                resources.getDimensionPixelSize(R.dimen.custom_autocomplete_max_height)
            )
        } finally {
            typed.recycle()
        }
        threshold = Int.MAX_VALUE
        setInputEnabled(false)
        setOnClickListener {
            showDropDown()
        }
    }

    fun <T> setDropdownItems(items: List<DropdownItem<T>>) {
        this.items = items
        dropdownAdapter.updateItems(items)
    }

    fun setOnItemSelectedListener(listener: OnItemSelectedListener?) {
        this.onItemSelectedListener = listener
    }

    /**
     * Enables or disables text input.
     * When disabled, the field acts as a dropdown-only selector (no keyboard input).
     * Click to show dropdown still works.
     */
    private fun setInputEnabled(enabled: Boolean) {
        isInputEnabled = enabled
        if (enabled) {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isFocusable = true
            isFocusableInTouchMode = true
        } else {
            inputType = InputType.TYPE_NULL
            isFocusable = false
            isFocusableInTouchMode = false
            // Ensure click still works for dropdown
            isClickable = true
        }
    }

    override fun showDropDown() {
        if (items.isEmpty()) return

        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }

        dropdownAdapter.updateItems(items)

        if (dropdownBinding.recyclerViewDropdown.adapter == null) {
            dropdownBinding.recyclerViewDropdown.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = dropdownAdapter
            }
        }

        val anchor = anchorView ?: this
        val calculatedWidth = when (dropdownWidth) {
            ViewGroup.LayoutParams.MATCH_PARENT -> anchor.width
            ViewGroup.LayoutParams.WRAP_CONTENT -> ViewGroup.LayoutParams.WRAP_CONTENT
            else -> dropdownWidth
        }

        popupWindow.width = calculatedWidth

        dropdownBinding.root.measure(
            MeasureSpec.makeMeasureSpec(calculatedWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        val measuredHeight = dropdownBinding.root.measuredHeight
        popupWindow.height = if (dropdownMaxHeight != ViewGroup.LayoutParams.WRAP_CONTENT && measuredHeight > dropdownMaxHeight) {
            dropdownMaxHeight
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT
        }
        popupWindow.elevation = dropdownElevation
        popupWindow.setBackgroundDrawable(dropdownBackground)
        popupWindow.showAsDropDown(anchor, 0, 0, Gravity.START or Gravity.TOP)
    }

    fun dismissDropdown() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    fun isDropdownShowing(): Boolean = popupWindow.isShowing

    fun toggleDropdown() {
        if (isDropdownShowing()) {
            dismissDropdown()
        } else {
            showDropDown()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dismissDropdown()
    }

    private class DropdownAdapter(
        private val onItemClick: (DropdownItem<*>, Int) -> Unit,
        private val selectedColorRes: Int
    ) : RecyclerView.Adapter<DropdownAdapter.ViewHolder>() {

        private var items: List<DropdownItem<*>> = emptyList()

        fun updateItems(newItems: List<DropdownItem<*>>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = CustomAutocompleteItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(
            private val binding: CustomAutocompleteItemBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            private val defaultBackgroundColor = android.graphics.Color.TRANSPARENT
            private val selectedBackgroundColor by lazy {
                ContextCompat.getColor(binding.root.context, selectedColorRes)
            }

            init {
                binding.root.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onItemClick(items[position], position)
                    }
                }
            }

            fun bind(item: DropdownItem<*>) {
                binding.textItem.text = item.text
                binding.root.setBackgroundColor(
                    if (item.isSelected) selectedBackgroundColor else defaultBackgroundColor
                )
            }
        }
    }
}

inline fun <reified T> CustomAutoCompleteTextView.DropdownItem<*>.getTypedData(): T? {
    return data as? T
}
