package com.owncloud.android.ui.custom

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.owncloud.android.R
import com.owncloud.android.databinding.FilterableAutocompleteDropdownBinding
import com.owncloud.android.databinding.FilterableAutocompleteItemBinding

class FilterableAutoCompleteTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.autoCompleteTextViewStyle
) : AppCompatAutoCompleteTextView(context, attrs, defStyleAttr) {

    private var allItems: List<DropdownItem<*>> = emptyList()
    private var displayItems: List<DropdownItem<*>> = emptyList()
    private var startDrawable: Drawable? = null
    private var onItemSelectedListener: OnItemSelectedListener? = null
    private var onAddItemClickListener: ((query: String) -> Unit)? = null
    var onDropdownDismissListener: OnDropdownDismissListener? = null
    private var anchorView: View? = null
    private var emptyStateText: String = ""
    private var addItemFormatText: String = DEFAULT_ADD_FORMAT
    private var suppressDropdown = false

    private val dropdownBackground: Drawable?
    private val dropdownElevation: Float
    private val dropdownMaxHeight: Int
    private var dropdownWidth: Int = ViewGroup.LayoutParams.MATCH_PARENT

    private val dropdownBinding by lazy {
        FilterableAutocompleteDropdownBinding.inflate(LayoutInflater.from(context))
    }

    private val dropdownAdapter by lazy {
        DropdownAdapter(
            onItemClick = { item, _ ->
                when (item.id) {
                    ITEM_ID_EMPTY_STATE -> return@DropdownAdapter
                    ITEM_ID_ADD -> {
                        suppressDropdown = true
                        val query = text?.toString()?.trim().orEmpty()
                        onAddItemClickListener?.invoke(query)
                        dismissDropdown()
                        suppressDropdown = false
                    }
                    else -> {
                        suppressDropdown = true
                        onItemSelectedListener?.onItemSelected(item)
                        dismissDropdown()
                        suppressDropdown = false
                    }
                }
            }
        )
    }

    private val popupWindow by lazy {
        PopupWindow(
            dropdownBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = true
            isFocusable = false
            setOnDismissListener { onDropdownDismissListener?.onDismiss() }
        }
    }

    data class DropdownItem<T>(
        val id: String,
        val text: String,
        val data: T? = null
    )

    fun interface OnItemSelectedListener {
        fun onItemSelected(item: DropdownItem<*>)
    }

    fun interface OnDropdownDismissListener {
        fun onDismiss()
    }

    init {
        val typed = context.obtainStyledAttributes(attrs, R.styleable.FilterableAutoCompleteTextView)
        try {
            dropdownBackground = typed.getDrawable(R.styleable.FilterableAutoCompleteTextView_filterableDropdownBackground)
                ?: ContextCompat.getDrawable(context, R.drawable.bg_filterable_autocomplete_dropdown)
            dropdownElevation = typed.getDimension(
                R.styleable.FilterableAutoCompleteTextView_filterableDropdownElevation, 8f
            )
            dropdownMaxHeight = typed.getDimensionPixelSize(
                R.styleable.FilterableAutoCompleteTextView_filterableDropdownMaxHeight,
                resources.getDimensionPixelSize(R.dimen.filterable_autocomplete_max_height)
            )
        } finally {
            typed.recycle()
        }

        threshold = Int.MAX_VALUE
        inputType = InputType.TYPE_CLASS_TEXT
        isFocusable = true
        isFocusableInTouchMode = true

        setOnClickListener { showDropDown() }

        setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) dismissDropdown()
        }

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateClearButton()
                updateFilteredItems()
                if (hasFocus() && !suppressDropdown) {
                    showDropDown()
                }
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startDrawable = compoundDrawablesRelative[0]
    }

    private fun updateClearButton() {
        val clearDrawable = if (text?.isNotEmpty() == true) {
            ContextCompat.getDrawable(context, R.drawable.ic_close_accent)
        } else {
            null
        }
        setCompoundDrawablesRelativeWithIntrinsicBounds(startDrawable, null, clearDrawable, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val clearDrawable = compoundDrawablesRelative[2]
            if (clearDrawable != null) {
                val touchX = event.x.toInt()
                val clearStart = width - paddingEnd - clearDrawable.intrinsicWidth
                if (touchX >= clearStart) {
                    text?.clear()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_UP && isDropdownShowing()) {
            dismissDropdown()
            return true
        }
        return super.onKeyPreIme(keyCode, event)
    }

    /**
     * Sets the full list of selectable items.
     * The component filters this list internally as the user types.
     */
    fun <T> setDropdownItems(items: List<DropdownItem<T>>) {
        this.allItems = items
        updateFilteredItems()
    }

    /**
     * Text shown in the dropdown when [setDropdownItems] was called with an empty list.
     */
    fun setEmptyStateText(text: String) {
        this.emptyStateText = text
        updateFilteredItems()
    }

    /**
     * Format string for the "Add" option shown when no items match the query.
     * Must contain a single `%s` placeholder for the query text.
     * Example: `Add "%s"`
     */
    fun setAddItemFormatText(format: String) {
        this.addItemFormatText = format
    }

    /**
     * Called when the user taps the "Add" option in the dropdown.
     * The lambda receives the current query text.
     * Setting this listener enables the "Add" option; pass `null` to disable it.
     */
    fun setOnAddItemClickListener(listener: ((query: String) -> Unit)?) {
        this.onAddItemClickListener = listener
    }

    fun setOnItemSelectedListener(listener: OnItemSelectedListener?) {
        this.onItemSelectedListener = listener
    }

    fun setAnchorView(view: View?) {
        this.anchorView = view
    }

    private fun updateFilteredItems() {
        val query = text?.toString()?.trim().orEmpty()
        displayItems = when {
            allItems.isEmpty() -> {
                if (emptyStateText.isNotEmpty()) {
                    listOf(DropdownItem<Nothing>(id = ITEM_ID_EMPTY_STATE, text = emptyStateText))
                } else {
                    emptyList()
                }
            }
            query.isEmpty() -> allItems
            else -> {
                val filtered = allItems.filter { it.text.contains(query, ignoreCase = true) }
                if (filtered.isEmpty() && onAddItemClickListener != null) {
                    listOf(
                        DropdownItem<Nothing>(
                            id = ITEM_ID_ADD,
                            text = String.format(addItemFormatText, query)
                        )
                    )
                } else {
                    filtered
                }
            }
        }
        dropdownAdapter.updateItems(displayItems)
    }

    override fun showDropDown() {
        if (displayItems.isEmpty()) {
            dismissDropdown()
            return
        }

        dropdownAdapter.updateItems(displayItems)

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

        dropdownBinding.root.measure(
            MeasureSpec.makeMeasureSpec(calculatedWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        val measuredHeight = dropdownBinding.root.measuredHeight
        val targetHeight = if (dropdownMaxHeight != ViewGroup.LayoutParams.WRAP_CONTENT && measuredHeight > dropdownMaxHeight) {
            dropdownMaxHeight
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT
        }

        if (popupWindow.isShowing) {
            popupWindow.update(anchor, calculatedWidth, targetHeight)
        } else {
            popupWindow.width = calculatedWidth
            popupWindow.height = targetHeight
            popupWindow.elevation = dropdownElevation
            popupWindow.setBackgroundDrawable(dropdownBackground)
            popupWindow.showAsDropDown(anchor, 0, 0, Gravity.START or Gravity.TOP)
        }
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
    ) : RecyclerView.Adapter<DropdownAdapter.ViewHolder>() {

        private var items: List<DropdownItem<*>> = emptyList()

        fun updateItems(newItems: List<DropdownItem<*>>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = FilterableAutocompleteItemBinding.inflate(
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
            private val binding: FilterableAutocompleteItemBinding
        ) : RecyclerView.ViewHolder(binding.root) {

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
            }
        }
    }

    companion object {
        internal const val ITEM_ID_ADD = "__filterable_autocomplete_add__"
        internal const val ITEM_ID_EMPTY_STATE = "__filterable_autocomplete_empty__"
        private const val DEFAULT_ADD_FORMAT = "Add \"%s\""
    }
}

inline fun <reified T> FilterableAutoCompleteTextView.DropdownItem<*>.getTypedData(): T? {
    return data as? T
}
