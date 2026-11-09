package com.flexa.identity.create_id.date_text

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

abstract class BaseMaskedEditText : AppCompatEditText {


    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    open fun checkAfterTextChangedRules() {}

    abstract fun getAddedString(
        s: CharSequence,
        start: Int,
        before: Int,
        count: Int
    ): String
}
