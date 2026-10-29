// נכתב: 2026-10-29 21:00
package il.yoel.nehiga

import android.app.Activity
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** מחיקת הודעה עם אישור — משותף למסך הראשי ולעורך. */
object MessageActions {
    fun confirmDelete(a: Activity, id: String, onDone: () -> Unit) {
        val m = Prefs.message(a, id) ?: return
        if (Prefs.messages(a).size <= 1) {
            Toast.makeText(a, "חייבת להישאר לפחות הודעה אחת", Toast.LENGTH_LONG).show()
            return
        }
        val linked = Prefs.rules(a).count { it.msgId == id }
        val extra = if (linked > 0) "\n\nמכשירי Bluetooth שמקושרים להודעה הזו ($linked) יבוטלו." else ""
        MaterialAlertDialogBuilder(a)
            .setTitle("למחוק את \"${m.name}\"?")
            .setMessage("לא ניתן לשחזר את ההודעה אחרי המחיקה.$extra")
            .setPositiveButton("מחק") { _, _ ->
                Prefs.deleteMessage(a, id)
                EventLog.add(a, "🗑️ נמחקה הודעה: ${m.name}")
                DrivingMode.refreshOngoing(a)
                DrivingMode.notifyUi(a)
                onDone()
            }
            .setNegativeButton("ביטול", null)
            .show()
    }
}
