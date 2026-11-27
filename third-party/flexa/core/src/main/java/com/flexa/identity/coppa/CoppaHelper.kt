package com.flexa.identity.coppa

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.Period
import java.util.Calendar.DAY_OF_MONTH
import java.util.Calendar.MONTH
import java.util.Calendar.YEAR
import java.util.Calendar.getInstance
import java.util.Date

class CoppaHelper {

    companion object {
        const val MINIMUM_ALLOWED_AGE = 13

        fun isTooYoung(date: Date?): Boolean =
            getUserAge(date) < MINIMUM_ALLOWED_AGE

        fun getUserAge(date: Date?): Int {
            return when {
                date == null -> {
                    0
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    getYearDiff(date, Date())
                }

                else -> {
                    getYearDiffJoda(date, Date())
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        internal fun getYearDiff(date: Date, currentDate: Date): Int {
            val cal = getInstance().apply { time = date }
            val curCal = getInstance().apply { time = currentDate }
            return Period.between(
                LocalDate.of(
                    cal.get(YEAR), cal.get(MONTH) + 1, cal[DAY_OF_MONTH]
                ),
                LocalDate.of(
                    curCal.get(YEAR), curCal.get(MONTH) + 1, curCal[DAY_OF_MONTH]
                )
            ).years
        }

        internal fun getYearDiffJoda(date: Date, currentDate: Date): Int {
            val old = org.joda.time.LocalDate.fromDateFields(date)
            val cur = org.joda.time.LocalDate.fromDateFields(currentDate)
            return org.joda.time.Period(old.toDate().time, cur.toDate().time).years
        }
    }
}
