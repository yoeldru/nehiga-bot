// נכתב: 2026-10-29 02:35 | עודכן: 2026-10-29 21:00 (עיצוב Material)
package il.yoel.nehiga

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** מסך היומן: כל הפעולות, ניקוי ידני והגדרת ניקוי אוטומטי. */
class LogActivity : AppCompatActivity() {

    private lateinit var txtLog: TextView
    private lateinit var txtCount: TextView

    private val updates = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = showLog()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)
        txtLog = findViewById(R.id.txtLog)
        txtCount = findViewById(R.id.txtCount)

        val group = findViewById<RadioGroup>(R.id.groupRetention)
        group.check(
            when (Prefs.logRetentionDays(this)) {
                7 -> R.id.r7
                90 -> R.id.r90
                0 -> R.id.rNever
                else -> R.id.r30
            }
        )
        group.setOnCheckedChangeListener { _, id ->
            val days = when (id) {
                R.id.r7 -> 7
                R.id.r90 -> 90
                R.id.rNever -> 0
                else -> 30
            }
            Prefs.setLogRetentionDays(this, days)
            EventLog.autoClean(this)
            showLog()
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("לנקות את היומן?")
                .setMessage("כל הרשומות יימחקו.")
                .setPositiveButton("נקה") { _, _ ->
                    EventLog.clear(this)
                    EventLog.add(this, "🗑️ היומן נוקה ידנית")
                    showLog()
                }
                .setNegativeButton("ביטול", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(DrivingMode.ACTION_UPDATED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(updates, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updates, filter)
        }
        EventLog.autoClean(this)
        showLog()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(updates)
        } catch (_: Exception) {
        }
    }

    private fun showLog() {
        val entries = EventLog.read(this)
        txtCount.text = "${entries.size} רשומות (החדשות למעלה)"
        txtLog.text = if (entries.isEmpty()) "היומן ריק"
        else entries.joinToString("\n") { EventLog.format(it) }
    }
}
