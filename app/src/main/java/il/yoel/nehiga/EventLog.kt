// נכתב: 2026-10-29 02:35
package il.yoel.nehiga

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * יומן פעולות: כל מה שהאפליקציה עושה נרשם כאן עם תאריך ושעה.
 * נשמר בקובץ בתוך האפליקציה; ניקוי ידני או אוטומטי לפי הגדרה.
 */
object EventLog {
    private const val FILE_NAME = "log.txt"
    private const val MAX_LINES = 3000
    private const val DAY_MS = 24L * 60 * 60 * 1000

    data class Entry(val time: Long, val text: String)

    private fun file(c: Context) = File(c.applicationContext.filesDir, FILE_NAME)

    @Synchronized
    fun add(c: Context, text: String) {
        val clean = text.replace('\n', ' ').replace('\t', ' ')
        file(c).appendText("${System.currentTimeMillis()}\t$clean\n")
        autoClean(c)
    }

    /** הרשומות, החדשה ביותר ראשונה. */
    @Synchronized
    fun read(c: Context): List<Entry> {
        val f = file(c)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@mapNotNull null
            val t = line.substring(0, tab).toLongOrNull() ?: return@mapNotNull null
            Entry(t, line.substring(tab + 1))
        }.asReversed()
    }

    @Synchronized
    fun clear(c: Context) {
        file(c).delete()
    }

    /** מוחק רשומות ישנות לפי ההגדרה (0 = לשמור תמיד), ושומר על גודל מקסימלי. */
    @Synchronized
    fun autoClean(c: Context) {
        val f = file(c)
        if (!f.exists()) return
        val days = Prefs.logRetentionDays(c)
        val lines = f.readLines()
        var kept = lines
        if (days > 0) {
            val cutoff = System.currentTimeMillis() - days * DAY_MS
            kept = kept.filter { (it.substringBefore('\t').toLongOrNull() ?: 0L) >= cutoff }
        }
        if (kept.size > MAX_LINES) kept = kept.takeLast(MAX_LINES)
        if (kept.size != lines.size) {
            f.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n"))
        }
    }

    fun format(e: Entry): String {
        val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
        return "${fmt.format(Date(e.time))}  ${e.text}"
    }
}
