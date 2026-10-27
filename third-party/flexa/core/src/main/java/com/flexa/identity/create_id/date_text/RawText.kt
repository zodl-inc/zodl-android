package com.flexa.identity.create_id.date_text

class RawText(private var text: StringBuilder = StringBuilder("")) {


    fun subtractFromString(range: Range) {
        var firstPart = ""
        var lastPart = ""
        if (range.start > 0 && range.start <= text.length) {
            firstPart = text.substring(0, range.start)
        }
        if (range.end >= 0 && range.end < text.length) {
            lastPart = text.substring(range.end, text.length)
        }
        text.setLength(0)
        text.append(firstPart).append(lastPart)
    }

    /**
     *
     * @param newString New String to be added
     * @param start Position to insert newString
     * @param maxLength Maximum raw text length
     * @return Number of added characters
     */
    fun addToString(newString: String?, start: Int, maxLength: Int): Int {
        var string = newString
        var firstPart = ""
        var lastPart = ""
        if (string == null || string == "") {
            return 0
        } else require(start >= 0) { "Start position must be non-negative" }
        require(start <= text.length) { "Start position must be less than the actual text length" }
        var count = string.length
        if (start > 0) {
            firstPart = text.substring(0, start)
        }
        if (start >= 0 && start < text.length) {
            lastPart = text.substring(start, text.length)
        }
        if (text.length + string.length > maxLength) {
            count = maxLength - text.length
            string = string.substring(0, count)
        }
        text.setLength(0)
        text.append(firstPart).append(string).append(lastPart)
        return count
    }

    fun getText(): String {
        return text.toString()
    }

    fun length(): Int {
        return text.length
    }

    fun charAt(position: Int): Char {
        return if (text.length > position) text[position] else ' '
    }

    fun replace(position: Int, value: Char) {
        if (text.length > position)
            text[position] = value
    }

    fun insert(position: Int, value: Char) {
        text.insert(position, value)
    }

    fun set(text: String) {
        clean()
        this.text.append(text)
    }

    fun clean() {
        text.setLength(0)
    }
}
