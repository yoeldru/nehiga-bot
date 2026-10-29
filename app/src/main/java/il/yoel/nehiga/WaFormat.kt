// נכתב: 2026-10-29 21:00
package il.yoel.nehiga

import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/**
 * כללי העיצוב של ווטסאפ:  *מודגש*  _נטוי_  ~קו חוצה~  ```מונו```
 * render  — מציג את ההודעה כמו בווטסאפ (בלי הסימנים), לתצוגה מקדימה.
 * highlight — מעצב בתוך תיבת העריכה עצמה (הסימנים נשארים, באפור), כמו בווטסאפ.
 */
object WaFormat {

    // ממשק סימון — כדי למחוק רק את העיצוב שלנו בכל רענון
    interface WaSpan
    class Bold : StyleSpan(Typeface.BOLD), WaSpan
    class Italic : StyleSpan(Typeface.ITALIC), WaSpan
    class Strike : StrikethroughSpan(), WaSpan
    class Mono : TypefaceSpan("monospace"), WaSpan
    class Dim : ForegroundColorSpan(Color.parseColor("#9E9E9E")), WaSpan

    private class Rule(val marker: String, val regex: Regex, val span: () -> Any)

    // סימן פותח צמוד לטקסט, סימן סוגר צמוד לטקסט, ולא באמצע מילה — כמו בווטסאפ
    private fun simple(m: String) = Regex(
        "(?<![\\p{L}\\p{N}$m])$m(?=\\S)([^\\n$m]*?\\S)$m(?![\\p{L}\\p{N}$m])"
    )

    private val rules = listOf(
        Rule("```", Regex("```([\\s\\S]+?)```")) { Mono() },
        Rule("*", simple("\\*")) { Bold() },
        Rule("_", simple("_")) { Italic() },
        Rule("~", simple("~")) { Strike() }
    )

    /** הטקסט כפי שיופיע בווטסאפ. */
    fun render(text: CharSequence): CharSequence {
        val sb = SpannableStringBuilder(text)
        for (r in rules) {
            val l = r.marker.length
            for (m in r.regex.findAll(sb.toString()).toList().asReversed()) {
                val start = m.range.first
                val endEx = m.range.last + 1
                sb.setSpan(r.span(), start + l, endEx - l, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.delete(endEx - l, endEx)
                sb.delete(start, start + l)
            }
        }
        return sb
    }

    /** עיצוב חי בתוך תיבת הטקסט. */
    fun highlight(e: Editable) {
        for (s in e.getSpans(0, e.length, WaSpan::class.java)) e.removeSpan(s)
        val text = e.toString()
        for (r in rules) {
            val l = r.marker.length
            for (m in r.regex.findAll(text)) {
                val start = m.range.first
                val endEx = m.range.last + 1
                e.setSpan(r.span(), start + l, endEx - l, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                e.setSpan(Dim(), start, start + l, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                e.setSpan(Dim(), endEx - l, endEx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}
