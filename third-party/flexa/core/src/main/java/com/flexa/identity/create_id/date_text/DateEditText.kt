package com.flexa.identity.create_id.date_text

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.InputType
import android.util.AttributeSet
import androidx.core.text.isDigitsOnly
import androidx.core.widget.doAfterTextChanged
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

private const val DAY = 'D'
private const val MONTH = 'M'
private const val YEAR = 'Y'
private const val MAX_MONTH_FIRST_DIGIT = 1
private const val MAX_MONTH_SECOND_DIGIT = 2
private const val MAX_DAY_FIRST_DIGIT = 3
private const val BLOCK_INPUT = ""

class DateEditText : MaskedDateText {


    internal var onDateChanged: ((Date?) -> Unit)? = null
    private var maskType: Mask = Mask.US
        set(value) {
            field = value
            mask = maskType.mask
            hint = maskType.hint
            cleanUp()
        }
    private val localeMX = Locale("Mexico")
    private val currentYearDigits = IntArray(4)
    private var appended: Char? = null
    private var tmpDate: Date? = null

    init {
        inputType = InputType.TYPE_CLASS_NUMBER
        typeface = Typeface.MONOSPACE
        allowedChars = "0123456789"
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            resources.configuration.locales[0] else
            @Suppress("DEPRECATION")
            resources.configuration.locale
        maskType = when (locale) {
            Locale.CANADA_FRENCH ->
                Mask.CAfr
            Locale.ENGLISH,
            localeMX,
            Locale.CANADA ->
                Mask.CAen_GB_MX
            else ->
                Mask.US
        }
        doAfterTextChanged {
            post {
                if (appended != null) {
                    val tmp = appended!!
                    appended = null
                    appendChar(tmp)
                }
            }
        }
        onChanged = {
            val date = getDate()
            if (tmpDate != date) {
                tmpDate = date
                onDateChanged?.invoke(date)
            }
        }
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    fun setText(text: String) {
        rawText.set(text)
        removeTextChangedListener(this)
        if (keepHint) super.setText(makeMaskedTextWithHint())
        else super.setText(makeMaskedText())
        mySelection = lastValidPosition()
        addTextChangedListener(this)
    }

    fun getRawText(): String = rawText.getText()

    fun getDate(): Date? {
        var date: Date? = null
        val separatedDate = SeparatedDate(getDay(), getMonth(), getYear())
        if (separatedDate.isFilled()) {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, separatedDate.year!!)
                set(Calendar.MONTH, separatedDate.month!! - 1)
                set(Calendar.DAY_OF_MONTH, separatedDate.day!!)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            date = calendar.time
        }
        return date
    }

    override fun checkAfterTextChangedRules() {
        if (isDeleting)
            return
        /*Check day*/
        /**
         * Parsing DD
         * For DD, valid inputs are dependent on MM
         *
         * If MM is in [1, 3, 5, 7, 8, 10, 12]
         * These months have 31 days
         *
         * Valid inputs:
         * [01, 02, 03, 04, 05, 06, 07, 08, 09, 1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 2, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 3, 30, 31, 4, 5, 6, 7, 8, 9]
         * If a user inputs any of the valid inputs except for 1, 2, or 3:
         * Insert a slash (/) and advance to YYYY
         * If the user inputs 1 or 2, ignore it
         * All possible next characters are also valid, so we can’t auto-advance
         * If the user inputs 3, wait for the next character
         * If the next character is in [0, 1], then treat as 30, 31, insert a slash, advance to YYYY
         * If the next character is in [2, 3, 4, 5, 6, 7, 8, 9], then treat as 3, insert a slash, advance to YYYY, and insert the next character as the first digit of YYYY
         * All other characters should be blocked from input
         */
        val dayPosition = getFirstPosition(DAY)
        if (rawText.length() > dayPosition &&
            Character.isDigit(rawText.charAt(dayPosition)) &&
            Character.getNumericValue(rawText.charAt(dayPosition)) > MAX_DAY_FIRST_DIGIT
        ) {
            rawText.insert(dayPosition, '0')
            ++mySelection
            return
        }

        /*Check month*/
        /**
         * Parsing MM
         * Valid inputs:
         * [01, 02, 03, 04, 05, 06, 07, 08, 09, 1, 10, 11, 12, 2, 3, 4, 5, 6, 7, 8, 9]
         * If a user inputs any of the valid inputs except for 1:
         * Insert a slash (/) and advance to DD
         * If the user inputs 1, wait for the next character
         * If the next character is in [0, 1, 2], then treat as 10, 11, 12, insert a slash, advance to DD
         * All other characters should be blocked from input
         * The user should be blocked from inputting 00
         */
        val monthPosition = getFirstPosition(MONTH)
        if (rawText.length() > monthPosition &&
            Character.isDigit(rawText.charAt(monthPosition)) &&
            Character.getNumericValue(rawText.charAt(monthPosition)) > MAX_MONTH_FIRST_DIGIT
        ) {
            rawText.insert(monthPosition, '0')
            ++mySelection
            return
        }

        /*Validate Max Day*/
        val separatedDate = SeparatedDate(getDay(), getMonth(), getYear())
        if (separatedDate.isFilled()) {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.MONTH, separatedDate.month!! - 1)
                set(Calendar.YEAR, separatedDate.year!!)
            }
            val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            if (separatedDate.day!! > maxDay) {
                val dayDigits = IntArray(2)
                var tmpDay = maxDay
                var i = dayDigits.size - 1
                while (tmpDay > 0) {
                    dayDigits[i--] = tmpDay % 10
                    tmpDay /= 10
                }
                val f = (dayDigits[0] + 48).toChar()
                val s = (dayDigits[1] + 48).toChar()
                rawText.replace(dayPosition, f)
                rawText.replace(dayPosition + 1, s)
            }
        }
    }

    override fun getAddedString(s: CharSequence, start: Int, before: Int, count: Int): String {
        var addedString = s.subSequence(start, start + count).toString()
//        var addedString = s.findLast { c -> c.isDigit() }.toString()
        if (!addedString.isDigitsOnly())
            return BLOCK_INPUT

        val offset = getOffset(start)
        val firstDayPosition = getFirstPosition(DAY) + offset
        val secondDayPosition = getFirstPosition(DAY) + offset + 1
        val firstMonthPosition = getFirstPosition(MONTH) + offset
        val secondMonthPosition = getFirstPosition(MONTH) + offset + 1


        val digit = addedString.toInt()
        /*Check Day*/
        //First Digit
        if (start == firstDayPosition) {
            getMonth()?.let { month ->
                when (month) {
                    /**
                     * If MM is 2
                     * We don’t know whether February is a leap year yet (because the user hasn’t entered YYYY), so we have to treat it as though it has 29 days
                     */
                    2 -> {
                        if (digit > 2) {
                            addedString = BLOCK_INPUT
                        }
                    }
                }
            }
        }
        //Second Digit
        if (start == secondDayPosition) {
            val previousDigit = if (start > 0) {
                val symbol =
                    s.subSequence(start - 1, start + count - 1).toString()
                if (symbol.isDigitsOnly()) symbol.toInt() else 0
            } else 0
            val month = getMonth()
            month?.let {
                when (it) {
                    /**
                     * If MM is in [4, 6, 9, 11]
                     * These months do not have a 31st day
                     */
                    4, 6, 9, 11 -> {
                        if (previousDigit == 3 && digit >= 1) {
                            addedString = BLOCK_INPUT
                        }
                    }
                }
            }
            val year = getYear()
            if (month != null && year != null) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.YEAR, year)
                }
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val d = combine(previousDigit, digit)
                if (d > maxDay)
                    addedString = BLOCK_INPUT
            }
            if (previousDigit > MAX_DAY_FIRST_DIGIT - 1 && digit > 1) {
                addedString = "1"
            }
            /**
             * The user should be blocked from inputting 00
             */
            if (previousDigit == 0 && digit == 0)
                addedString = BLOCK_INPUT
            if (combineDigits(previousDigit, digit) > 31)
                addedString = BLOCK_INPUT
        }

        /*Check month*/
        //First month digit
        if (start == firstMonthPosition ||
            start == secondMonthPosition
        ) {
            val month = if (start == firstMonthPosition)
                digit else {
                val previousDigit = if (start > 0) {
                    val symbol =
                        s.subSequence(start - 1, start + count - 1).toString()
                    if (symbol.isDigitsOnly()) symbol.toInt() else 0
                } else 0
                combine(previousDigit, digit)
            }
            getDay()?.let { day ->
                when (month) {
                    4, 6, 9, 11 -> {
                        if (day == 31)
                            addedString = BLOCK_INPUT
                    }
                    2 -> {
                        if (day > 29)
                            addedString = BLOCK_INPUT
                    }
                }
            }
        }
        //Second month digit
        if (start == secondMonthPosition) {
            val previousDigit = if (start > 0) {
                val symbol =
                    s.subSequence(start - 1, start + count - 1).toString()
                if (symbol.isDigitsOnly()) symbol.toInt() else 0
            } else 0
            /**
             * The user should be blocked from inputting 00
             */
            if (previousDigit == 0 && digit == 0)
                addedString = BLOCK_INPUT
            /**
             * If the next character is in [3, 4, 5, 6, 7, 8, 9], then treat as 1, insert a slash, advance to DD, and insert the next character as the first digit of DD
             */
            if (previousDigit > 0 && digit > MAX_MONTH_SECOND_DIGIT) {
                rawText.insert(firstMonthPosition, '0')
                ++mySelection
                addedString = BLOCK_INPUT
                appended = (digit + 48).toChar()
            }
        }

        val yearPosition = getFirstPosition(YEAR)
        var curYear = Calendar.getInstance().get(Calendar.YEAR)
        var i = currentYearDigits.size - 1
        while (curYear > 0) {
            currentYearDigits[i--] = curYear % 10
            curYear /= 10
        }
        /*Check year first digit*/
        var index = 0
        if (start == yearPosition + getOffset(start) + index
        ) {
            if (digit > currentYearDigits[0])
                addedString = currentYearDigits[0].toString()
            if (digit < 1)
                addedString = "1"
        }
        /*Check year second digit*/
        ++index
        if (start == yearPosition + getOffset(start) + index) {
            val firstDigit = Character.getNumericValue(rawText.charAt(yearPosition))
            //Restrict to input less than 1900
            if (firstDigit == 1 && digit < 9) {
                addedString = "9"
            }
            //Restrict to input greater than current year
            if (firstDigit == currentYearDigits[0] && digit > currentYearDigits[index]) {
                addedString = currentYearDigits[index].toString()
            }
        }
        /*Check year third digit*/
        ++index
        if (start == yearPosition + getOffset(start) + index
        ) {
            val firstDigit = Character.getNumericValue(rawText.charAt(yearPosition))
            //Restrict to input greater than current year
            if (firstDigit == currentYearDigits[0] && digit > currentYearDigits[index]) {
                addedString = currentYearDigits[index].toString()
            }
        }
        /*Check year fourth digit*/
        ++index
        if (start == yearPosition + getOffset(start) + index) {
            val enteredYear = combineDigits(
                Character.getNumericValue(rawText.charAt(yearPosition)),
                Character.getNumericValue(rawText.charAt(yearPosition + 1)),
                Character.getNumericValue(rawText.charAt(yearPosition + 2)),
                digit
            )
            /**
             * Restrict to input greater than current year
             */
            if (enteredYear > Calendar.getInstance().get(Calendar.YEAR)) {
                //addedString = (currentYearDigits[index]).toString()
                addedString = BLOCK_INPUT
            }
            val day = getDay()
            val month = getMonth()
            if (day != null && month != null) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.YEAR, enteredYear)
                }
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                if (day > maxDay)
                    addedString = BLOCK_INPUT
            }
        }
        return addedString
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        val start = max(selStart, mySelection)
        val end = max(selEnd, mySelection)
        super.onSelectionChanged(start, end)
    }

    override fun isNeedToSetSelection(start: Int, end: Int): Boolean {
        return start >= 0 && end <= text!!.length
    }

    override fun fixSelection(selection: Int): Int {
        val validSelection = super.fixSelection(selection)
        return max(validSelection, mySelection)
    }

    private fun appendChar(char: Char) {
        val length = lastValidPosition()
        text?.insert(length, char.toString())
    }

    private fun getFirstPosition(char: Char): Int {
        var res = 0
        hint?.let {
            for (i in it.indices)
                if (it[i] == char) {
                    res = i
                    break
                }
        }
        return res
    }

    private fun combineDigits(vararg digits: Int): Int {
        var result = 0
        for (digit in digits) {
            result = combine(result, digit)
        }
        return result
    }

    private fun combine(a: Int, b: Int): Int {
        if (b == 0) {
            return a * 10
        }
        var times = 1
        while (times <= b) times *= 10
        return a * times + b
    }

    private fun getDay(): Int? = getDateAccordanceToHint(DAY, 2)

    private fun getMonth(): Int? = getDateAccordanceToHint(MONTH, 2)

    private fun getYear(): Int? = getDateAccordanceToHint(YEAR, 4)

    private fun getDateAccordanceToHint(dateType: Char, length: Int): Int? {
        if (maskType == null)
            return null
        val res: Int?
        var monthIndex: Int = Int.MIN_VALUE
        for (i in maskType.hint.indices)
            if (maskType.hint[i] == dateType) {
                monthIndex = i
                break
            }
        res = if (rawText.length() > monthIndex + length - 1) {
            val monthString = rawText.getText()
                .substring(monthIndex, monthIndex + length)
            if (monthString.isDigitsOnly())
                monthString.toInt() else null
        } else null
        return res
    }
}
