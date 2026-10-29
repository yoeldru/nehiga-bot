// נכתב: 2026-10-29 21:00
package il.yoel.nehiga

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import com.google.android.material.textfield.TextInputEditText

/**
 * תיבת טקסט בסגנון ווטסאפ:
 * - מעצבת תוך כדי כתיבה (*מודגש* וכו').
 * - הדבקה תמיד כטקסט רגיל — כך הודעה שהועתקה מווטסאפ נשמרת עם סימני העיצוב שלה.
 */
class WaEditText : TextInputEditText {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    private var busy = false

    init {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (busy || s == null) return
                busy = true
                WaFormat.highlight(s)
                busy = false
            }
        })
    }

    override fun onTextContextMenuItem(id: Int): Boolean =
        if (id == android.R.id.paste) super.onTextContextMenuItem(android.R.id.pasteAsPlainText)
        else super.onTextContextMenuItem(id)

    /** עוטף את הטקסט המסומן בסימן (למשל *), או מכניס זוג סימנים ושם את הסמן ביניהם. */
    fun wrapSelection(marker: String) {
        val e = text ?: return
        var s = minOf(selectionStart, selectionEnd)
        var t = maxOf(selectionStart, selectionEnd)
        if (s < 0) {
            s = e.length; t = e.length
        }
        // ווטסאפ לא מעצב אם יש רווח צמוד לסימן — מדלגים על רווחים בקצוות
        while (s < t && e[s].isWhitespace()) s++
        while (t > s && e[t - 1].isWhitespace()) t--
        e.insert(t, marker)
        e.insert(s, marker)
        if (s == t) setSelection(s + marker.length) else setSelection(t + 2 * marker.length)
    }
}
