package com.mahaam.recorder

import android.content.Context
import java.util.Calendar
import java.util.Locale

object Prefs {
    private const val FILE = "mahaam_prefs"
    private val MONTHS = arrayOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun nextNumber(c: Context): Int = sp(c).getInt("next_number", 1)
    fun setNextNumber(c: Context, n: Int) = sp(c).edit().putInt("next_number", n).apply()

    fun treeUri(c: Context): String? = sp(c).getString("tree_uri", null)
    fun setTreeUri(c: Context, s: String?) {
        val e = sp(c).edit()
        if (s == null) e.remove("tree_uri") else e.putString("tree_uri", s)
        e.apply()
    }

    fun permissionsAsked(c: Context): Boolean = sp(c).getBoolean("perm_asked", false)
    fun setPermissionsAsked(c: Context) = sp(c).edit().putBoolean("perm_asked", true).apply()

    fun monthKey(cal: Calendar = Calendar.getInstance()): String =
        String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)

    fun prevMonthKey(): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, -1)
        return monthKey(cal)
    }

    fun monthLabel(key: String): String {
        val y = key.substring(0, 4)
        val m = key.substring(5, 7).toInt()
        return "${MONTHS[m - 1]} $y"
    }

    fun monthCount(c: Context, key: String = monthKey()): Int = sp(c).getInt("count_$key", 0)

    fun incrementMonth(c: Context) {
        val k = "count_${monthKey()}"
        sp(c).edit().putInt(k, sp(c).getInt(k, 0) + 1).apply()
    }

    fun lastReported(c: Context): String? = sp(c).getString("last_reported", null)
    fun setLastReported(c: Context, key: String) = sp(c).edit().putString("last_reported", key).apply()

    /** أول تشغيل: نعتبر الشهر السابق «مُبلَّغًا» حتى لا يصل تقرير فارغ. */
    fun ensureBaseline(c: Context) {
        if (lastReported(c) == null) setLastReported(c, prevMonthKey())
    }
}
