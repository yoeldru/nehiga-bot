// נכתב: 2026-10-29 21:00
package il.yoel.nehiga

import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/** עריכה / יצירה של הודעה, עם סרגל עיצוב ווטסאפ ותצוגה מקדימה חיה. */
class MessageEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "id"
    }

    private var msgId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        msgId = intent.getStringExtra(EXTRA_ID)
        val existing = Prefs.message(this, msgId)
        if (existing == null) msgId = null

        val editName = findViewById<TextInputEditText>(R.id.editName)
        val editText = findViewById<WaEditText>(R.id.editText)
        val preview = findViewById<TextView>(R.id.txtPreview)
        val btnDelete = findViewById<MaterialButton>(R.id.btnDelete)
        val btnStrike = findViewById<MaterialButton>(R.id.btnStrike)

        findViewById<TextView>(R.id.txtTitle).text = if (existing == null) "הודעה חדשה" else "עריכת הודעה"
        btnStrike.paintFlags = btnStrike.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

        editText.doAfterTextChanged {
            val t = it?.toString().orEmpty()
            preview.text = if (t.isBlank()) "התצוגה המקדימה תופיע כאן" else WaFormat.render(t)
        }
        editName.setText(existing?.name.orEmpty())
        editText.setText(existing?.text.orEmpty())
        if (existing == null) preview.text = "התצוגה המקדימה תופיע כאן"

        findViewById<View>(R.id.btnBold).setOnClickListener { editText.wrapSelection("*") }
        findViewById<View>(R.id.btnItalic).setOnClickListener { editText.wrapSelection("_") }
        btnStrike.setOnClickListener { editText.wrapSelection("~") }
        findViewById<View>(R.id.btnMono).setOnClickListener { editText.wrapSelection("```") }

        findViewById<View>(R.id.btnSave).setOnClickListener {
            val name = editName.text?.toString()?.trim().orEmpty()
            val text = editText.text?.toString()?.trim().orEmpty()
            when {
                name.isEmpty() -> Toast.makeText(this, "תן שם להודעה", Toast.LENGTH_SHORT).show()
                text.isEmpty() -> Toast.makeText(this, "ההודעה ריקה", Toast.LENGTH_SHORT).show()
                else -> {
                    val m = Prefs.Msg(msgId ?: Prefs.newId(), name, text)
                    Prefs.upsertMessage(this, m)
                    EventLog.add(this, if (existing == null) "⚙️ נוספה הודעה: $name" else "⚙️ עודכנה הודעה: $name")
                    DrivingMode.refreshOngoing(this)
                    DrivingMode.notifyUi(this)
                    Toast.makeText(this, "נשמר ✓", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }

        if (existing == null) {
            btnDelete.visibility = View.GONE
        } else {
            btnDelete.setOnClickListener { MessageActions.confirmDelete(this, existing.id) { finish() } }
        }
    }
}
