package com.flexa.identity.create_id.date_text

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.Log
import android.widget.TextView.OnEditorActionListener
import androidx.core.content.ContextCompat
import com.flexa.R


private val TAG = MaskedDateText::class.java.simpleName


open class MaskedDateText : BaseMaskedEditText, TextWatcher {


    internal var onTextChanged: ((String?) -> Unit)? = null
    internal var keepHint = false
        set(value) {
            field = value
            setText(getRawText())
        }
    internal var hintIfUnfocused = true
    internal var hintColor: Int? = null
    protected var onChanged: (() -> Unit)? = null
    protected var allowedChars: String? = null
    protected var mask: String = "##-##-####"
        set(value) {
            field = value
            if (maskToRaw.isNotEmpty())
                cleanUp()
        }
    protected val rawText: RawText = RawText()
    protected var mySelection = 0
    protected var isDeleting = false
    private var charRepresentation = '#'
        set(value) {
            field = value
            cleanUp()
        }
    private var ignore = false
    private val hasHint get() = hint != null
    private val onEditorActionListener = OnEditorActionListener { _, _, _ ->
        true
    }
    private var rawToMask: IntArray = IntArray(0)
    private var maskToRaw: IntArray = IntArray(0)
    private var editingBefore = false
    private var editingOnChanged = false
    private var editingAfter = false
    private var initialized = false
    private var maxRawLength = 0
    private var lastValidMaskPosition = 0
    private var inputCount = 0
    private var selectionChanged = false
    private var focusChangeListener: OnFocusChangeListener? = null
    private var deniedChars: String? = null
    private var isKeepingText = false

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int
    ) : super(context, attrs, defStyle) {
        initialize()
        initAttrs(context, attrs)
    }

    constructor(context: Context) : super(context) {
        initialize()
        cleanUp()
    }

    constructor(
        context: Context,
        attrs: AttributeSet?
    ) : super(context, attrs) {
        initialize()
        initAttrs(context, attrs)
    }

    private fun initAttrs(
        context: Context,
        attrs: AttributeSet?
    ) {
        val attributes =
            context.obtainStyledAttributes(attrs, R.styleable.MaskedDateText)
        mask = attributes.getString(R.styleable.MaskedDateText_mask) ?: mask
        allowedChars = attributes.getString(R.styleable.MaskedDateText_allowed_chars)
        deniedChars = attributes.getString(R.styleable.MaskedDateText_denied_chars)
        val enableImeAction =
            attributes.getBoolean(R.styleable.MaskedDateText_enable_ime_action, false)
        val representation =
            attributes.getString(R.styleable.MaskedDateText_char_representation)
        charRepresentation = if (representation == null) {
            '#'
        } else {
            representation[0]
        }
        keepHint = attributes.getBoolean(R.styleable.MaskedDateText_keep_hint, false)
        hintIfUnfocused = attributes.getBoolean(R.styleable.MaskedDateText_hint_if_unfocused, true)
        cleanUp()
        // Ignoring enter key presses if needed
        if (!enableImeAction) {
            setOnEditorActionListener(onEditorActionListener)
        } else {
            setOnEditorActionListener(null)
        }
        attributes.recycle()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superParcellable = super.onSaveInstanceState()
        val state = Bundle()
        state.putParcelable("super", superParcellable)
        state.putString("text", getRawText())
        state.putBoolean("keepHint", isKeepHint())
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val bundle = state as Bundle
        keepHint = bundle.getBoolean("keepHint", false)
        super.onRestoreInstanceState(state.getParcelable("super"))
        val text = bundle.getString("text")
        Log.d(TAG, "onRestoreInstanceState: setText >${text}<")
        setText(text)
        Log.d(MaskedDateText::class.java.simpleName, "onRestoreInstanceState: $text")
    }

    /** @param listener - its onFocusChange() method will be called before performing MaskedEditText operations,
     * related to this event.
     */
    override fun setOnFocusChangeListener(listener: OnFocusChangeListener) {
        focusChangeListener = listener
    }

    fun setImeActionEnabled(isEnabled: Boolean) {
        if (isEnabled)
            setOnEditorActionListener(onEditorActionListener)
        else
            setOnEditorActionListener(null)
    }

    override fun beforeTextChanged(
        s: CharSequence, start: Int, count: Int,
        after: Int
    ) {
        if (!editingBefore) {
            editingBefore = true
            if (start > lastValidMaskPosition) {
                ignore = true
            }
            var rangeStart = start
            if (after == 0) {
                rangeStart = erasingStart(start)
            }
            val range =
                calculateRange(rangeStart, start + count)
            if (range.start != -1) {
                rawText.subtractFromString(range)
            }
            if (count > 0) {
                mySelection = previousValidPosition(start)
            }
        }
    }

    override fun getAddedString(
        s: CharSequence, start: Int, before: Int, count: Int
    ): String =
        s.subSequence(start, start + count).toString()

    override fun onTextChanged(
        s: CharSequence,
        start: Int,
        before: Int,
        count: Int
    ) {
        @Suppress("SENSELESS_COMPARISON")
        if (rawText != null) {
            isDeleting = inputCount > rawText.length()
        }
        var mCount = count
        if (!editingOnChanged && editingBefore) {
            editingOnChanged = true
            if (ignore) return

            if (mCount > 0) {
                val startingPosition = maskToRaw[nextValidPosition(start)]
                val addedString = getAddedString(s, start, before, count)
                try {
                    mCount =
                        rawText.addToString(this.clear(addedString), startingPosition, maxRawLength)
                } catch (e: IllegalArgumentException) {
                    Log.e(MaskedDateText::class.java.simpleName, e.message, e)
                }
                if (initialized) {
                    val currentPosition =
                        if (startingPosition + mCount < rawToMask.size)
                            rawToMask[startingPosition + mCount]
                        else
                            lastValidMaskPosition + 1
                    mySelection = nextValidPosition(currentPosition)
                }
            }
        }
        onTextChanged?.invoke(rawText?.getText())
    }

    override fun afterTextChanged(s: Editable) {
        inputCount = rawText.length()
        if (!editingAfter && editingBefore && editingOnChanged) {
            editingAfter = true

            checkAfterTextChangedRules()

            if (hasHint && (keepHint || rawText.length() == 0)) {
                setText(makeMaskedTextWithHint())
            } else {
                setText(makeMaskedText())
            }
            selectionChanged = false
            setSelection(mySelection)
            editingBefore = false
            editingOnChanged = false
            editingAfter = false
            ignore = false
        }
        onChanged?.invoke()
    }

    protected fun cleanUp() {
        initialized = false
        if (mask.isEmpty()) {
            return
        }
        generatePositionArrays()
        if (!isKeepingText) {
            rawText.clean()
            if (rawToMask.isNotEmpty())
                mySelection = rawToMask[0]
        }
        editingBefore = true
        editingOnChanged = true
        editingAfter = true
        if (hasHint && rawText.length() == 0) {
            this.setText(makeMaskedTextWithHint())
        } else {
            this.setText(makeMaskedText())
        }
        editingBefore = false
        editingOnChanged = false
        editingAfter = false
        maxRawLength = maskToRaw[previousValidPosition(mask.length - 1)] + 1
        lastValidMaskPosition = findLastValidMaskPosition()
        initialized = true
        super.setOnFocusChangeListener { v, hasFocus ->
            setColorHint(hasFocus)
            if (focusChangeListener != null) {
                focusChangeListener!!.onFocusChange(v, hasFocus)
            }
            if (hasFocus()) {
                selectionChanged = false
                this@MaskedDateText.setSelection(lastValidPosition())
            }
        }
    }

    protected fun getOffset(position: Int): Int {
        var res = 0
        for (i in mask.indices) {
            if (position >= i && charRepresentation != mask[i])
                res++
        }
        return res
    }

    protected fun makeMaskedText(): String {
        val maskedTextLength: Int = if (rawText.length() < rawToMask.size) {
            rawToMask[rawText.length()]
        } else {
            mask.length
        }
        val maskedText =
            CharArray(maskedTextLength) //mask.replace(charRepresentation, ' ').toCharArray();
        for (i in maskedText.indices) {
            val rawIndex = maskToRaw[i]
            if (rawIndex == -1) {
                maskedText[i] = mask[i]
            } else {
                maskedText[i] = rawText.charAt(rawIndex)
            }
        }
        return String(maskedText)
    }

    protected fun makeMaskedTextWithHint(): CharSequence {
        val ssb = SpannableStringBuilder()
        var mtrv: Int
        val maskFirstChunkEnd = if (rawToMask.isNotEmpty()) rawToMask[0] else 0
        for (i in mask.indices) {
            mtrv = maskToRaw[i]
            if (mtrv != -1) {
                if (mtrv < rawText.length()) {
                    val char = rawText.charAt(mtrv)
                    ssb.append(char)
                } else {
                    ssb.append(hint[maskToRaw[i]])
                }
            } else {
                ssb.append(mask[i])
            }
            if (keepHint && rawText.length() < rawToMask.size && i >= rawToMask[rawText.length()]
                || !keepHint && i >= maskFirstChunkEnd
            ) {
                val color = getHintTextColor(hasFocus())
                ssb.setSpan(ForegroundColorSpan(color), i, i + 1, 0)
            }
        }
        return ssb
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        // On Android 4+ this method is being called more than 1 time if there is a hint in the EditText, what moves the cursor to left
        // Using the boolean var selectionChanged to limit to one execution
        var mSelStart = selStart
        var mSelEnd = selEnd
        Log.d(
            MaskedDateText::class.java.simpleName,
            "onSelectionChanged initialized: $initialized selectionChanged: $selectionChanged mySelection: $mySelection"
        )
        if (initialized) {
            if (!selectionChanged) {
                mSelStart = fixSelection(mSelStart)
                mSelEnd = fixSelection(mSelEnd)

                // exactly in this order. If getText.length() == 0 then selStart will be -1
                if (mSelStart > text!!.length) mSelStart = text!!.length
                if (mSelStart < 0) mSelStart = 0

                // exactly in this order. If getText.length() == 0 then selEnd will be -1
                if (mSelEnd > text!!.length) mSelEnd = text!!.length
                if (mSelEnd < 0) mSelEnd = 0
                Log.d(
                    MaskedDateText::class.java.simpleName,
                    "mSelStart: $mSelStart mSelEnd: $mSelEnd"
                )
                if (mSelStart == mSelEnd) mySelection = mSelEnd
                setSelection(mSelStart, mSelEnd)
                selectionChanged = true
            } else {
                //check to see if the current selection is outside the already entered text
                if (mSelStart > rawText.length() - 1) {
                    val start = fixSelection(mSelStart)
                    val end = fixSelection(mSelEnd)
                    if (isNeedToSetSelection(start, end)) {
                        Log.d(MaskedDateText::class.java.simpleName, "start: $start end: $end")
                        setSelection(start, end)
                    }
                }
            }
        }
        super.onSelectionChanged(mSelStart, mSelEnd)
    }

    protected open fun isNeedToSetSelection(start: Int, end: Int): Boolean {
        return start >= 0 && end < text!!.length
    }

    protected open fun fixSelection(selection: Int): Int {
        return if (selection > lastValidPosition()) {
            lastValidPosition()
        } else {
            nextValidPosition(selection)
        }
    }

    protected fun lastValidPosition(): Int {
        return if (rawText.length() == maxRawLength) {
            rawToMask[rawText.length() - 1] + 1
        } else nextValidPosition(if (rawToMask.size < rawText.length()) rawToMask.size else rawToMask[rawText.length()])
    }

    private fun initialize() {
        addTextChangedListener(this)
    }

    private fun getRawText(): String {
        return rawText.getText()
    }

    private fun nextValidPosition(currentPosition: Int): Int {
        var mCurrentPosition = currentPosition
        while (mCurrentPosition < lastValidMaskPosition && maskToRaw[mCurrentPosition] == -1) {
            mCurrentPosition++
        }
        return if (mCurrentPosition > lastValidMaskPosition) lastValidMaskPosition + 1 else mCurrentPosition
    }

    private fun findLastValidMaskPosition(): Int {
        for (i in maskToRaw.indices.reversed()) {
            if (maskToRaw[i] != -1) return i
        }
        throw RuntimeException("Mask must contain at least one representation char")
    }

    private fun erasingStart(start: Int): Int {
        var mStart = start
        while (mStart > 0 && maskToRaw[mStart] == -1) {
            mStart--
        }
        return mStart
    }

    private fun previousValidPosition(currentPosition: Int): Int {
        var mCurrentPosition = currentPosition
        while (mCurrentPosition >= 0 && maskToRaw[mCurrentPosition] == -1) {
            mCurrentPosition--
            if (mCurrentPosition < 0) {
                return nextValidPosition(0)
            }
        }
        return mCurrentPosition
    }

    private fun setColorHint(hasFocus: Boolean) {
        val text = java.lang.StringBuilder(this.text.toString())
        removeTextChangedListener(this)
        setText(null)
        setHintTextColor(getHintTextColor(hasFocus))
        addTextChangedListener(this)
        setText(text)
    }

    private fun getHintTextColor(hasFocus: Boolean): Int {
        if (hintColor == null) {
            hintColor = currentHintTextColor
        }
        return when {
            hintIfUnfocused -> hintColor ?: ContextCompat.getColor(
                context,
                android.R.color.darker_gray
            )
            hasFocus -> hintColor ?: ContextCompat.getColor(
                context,
                android.R.color.darker_gray
            )
            else -> ContextCompat.getColor(context, android.R.color.transparent)
        }
    }

    private fun isKeepHint() = keepHint

    /**
     * Generates positions for values characters. For instance:
     * Input data: mask = "+7(###)###-##-##
     * After method execution:
     * rawToMask = [3, 4, 5, 6, 8, 9, 11, 12, 14, 15]
     * maskToRaw = [-1, -1, -1, 0, 1, 2, -1, 3, 4, 5, -1, 6, 7, -1, 8, 9]
     * charsInMask = "+7()- " (and space, yes)
     */
    private fun generatePositionArrays() {
        val aux = IntArray(mask.length)
        maskToRaw = IntArray(mask.length)
        var charsInMaskAux = ""
        var charIndex = 0
        for (i in mask.indices) {
            val currentChar = mask[i]
            if (currentChar == charRepresentation) {
                aux[charIndex] = i
                maskToRaw[i] = charIndex++
            } else {
                val charAsString = currentChar.toString()
                if (!charsInMaskAux.contains(charAsString)) {
                    charsInMaskAux += charAsString
                }
                maskToRaw[i] = -1
            }
        }
        if (charsInMaskAux.indexOf(' ') < 0) {
            charsInMaskAux += SPACE
        }
        //val charsInMask = charsInMaskAux.toCharArray()
        rawToMask = IntArray(charIndex)
        System.arraycopy(aux, 0, rawToMask, 0, charIndex)
    }

    private fun calculateRange(
        start: Int,
        end: Int
    ): Range {
        val range =
            Range()
        var i = start
        while (i <= end && i < mask.length) {
            if (maskToRaw[i] != -1) {
                if (range.start == -1) {
                    range.start = maskToRaw[i]
                }
                range.end = maskToRaw[i]
            }
            i++
        }
        if (end == mask.length) {
            range.end = rawText.length()
        }
        if (range.start == range.end && start < end) {
            val newStart = previousValidPosition(range.start - 1)
            if (newStart < range.start) {
                range.start = newStart
            }
        }
        return range
    }

    private fun clear(string: String): String {
        var mString = string
        if (deniedChars != null) {
            for (c in deniedChars!!.toCharArray()) {
                mString = mString.replace(c.toString(), "")
            }
        }
        if (allowedChars != null) {
            val builder = StringBuilder(mString.length)
            for (c in mString.toCharArray()) {
                if (allowedChars!!.contains(c.toString())) {
                    builder.append(c)
                }
            }
            mString = builder.toString()
        }
        return mString
    }


    companion object {
        const val SPACE = " "
    }
}
