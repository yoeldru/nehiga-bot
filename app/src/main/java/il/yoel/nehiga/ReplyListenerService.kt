// נכתב: 2026-10-29 02:10 | עודכן: 2026-10-29 02:35 (רישום ביומן כולל מספר טלפון)
package il.yoel.nehiga

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * הבוט עצמו.
 * אנדרואיד מעביר לכאן כל התראה שמופיעה בטלפון. כשמצב נהיגה פעיל ומגיעה הודעת ווטסאפ,
 * אנחנו "לוחצים" על כפתור ההשב שבתוך ההתראה ושולחים את ההודעה שהגדרת.
 * השם שמופיע בהתראה הוא השם מאנשי הקשר שלך (ווטסאפ כבר מצליב), ולכן אין צורך בהרשאת אנשי קשר.
 */
class ReplyListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NehigaBot"
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val JID = Regex("""^(\d{7,15})@s\.whatsapp\.net""")
    }

    override fun onListenerConnected() {
        EventLog.add(this, "⚙️ הבוט מחובר להתראות ומוכן")
    }

    override fun onListenerDisconnected() {
        EventLog.add(this, "⚠️ הבוט נותק מההתראות (בדוק גישה להתראות והגבלת סוללה)")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            handle(sbn)
        } catch (e: Exception) {
            Log.e(TAG, "שגיאה בטיפול בהתראה", e)
            EventLog.add(this, "❗ שגיאה: ${e.javaClass.simpleName} ${e.message ?: ""}")
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        if (!Prefs.isActive(this)) return
        if (sbn.packageName !in WHATSAPP_PACKAGES) return

        val n = sbn.notification
        // התראת סיכום ("5 הודעות מ-3 שיחות") והתראות קבועות — לא עונים עליהן
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        // שיחות טלפון ושיחות שלא נענו
        if (n.category == Notification.CATEGORY_CALL || n.category == "missed_call") return

        // צריך כפתור "השב" עם שדה טקסט — בלעדיו אין איך לענות
        val replyAction = n.actions?.firstOrNull { a ->
            a.remoteInputs?.any { it.allowFreeFormInput } == true
        } ?: return

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val isGroup = style?.isGroupConversation
            ?: n.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)

        val title = (style?.conversationTitle
            ?: n.extras.getCharSequence(Notification.EXTRA_TITLE))?.toString() ?: "?"
        // מזהה קבוע לשיחה: shortcutId של ווטסאפ (מבוסס מספר הטלפון), ואם אין — שם השיחה
        val key = n.shortcutId ?: title
        val who = describe(title, key)

        if (isGroup && !Prefs.replyToGroups(this)) {
            if (Prefs.markOnce(this, "skip:$key")) {
                EventLog.add(this, "⏭️ דילוג על הודעה בקבוצה: $title")
            }
            return
        }

        // כל אדם מקבל תשובה פעם אחת בנסיעה. מסמנים לפני השליחה, כי ווטסאפ מעדכן
        // את ההתראה מיד אחרי שאנחנו עונים וזה היה מפעיל אותנו שוב.
        if (Prefs.alreadyReplied(this, key)) return
        if (!Prefs.markReplied(this, key, title)) return

        val ok = sendReply(replyAction, Prefs.message(this))
        if (ok) {
            EventLog.add(this, "✉️ נשלחה תשובה אל $who" + if (isGroup) " (קבוצה)" else "")
            DrivingMode.refreshOngoing(this)
            DrivingMode.notifyUi(this)
        } else {
            Prefs.unmarkReplied(this, key)
            EventLog.add(this, "❗ שליחה נכשלה אל $who")
        }
    }

    /** "שם (050-1234567)" כשאפשר לחלץ מספר, אחרת רק השם. */
    private fun describe(title: String, key: String): String {
        val digits = JID.find(key)?.groupValues?.get(1) ?: return title
        val local = if (digits.startsWith("972")) "0" + digits.substring(3) else "+$digits"
        val pretty = if (local.length == 10 && local.startsWith("0"))
            local.substring(0, 3) + "-" + local.substring(3) else local
        return if (title.filter { it.isDigit() }.endsWith(digits.takeLast(7))) pretty
        else "$title ($pretty)"
    }

    private fun sendReply(action: Notification.Action, text: String): Boolean {
        val inputs = action.remoteInputs ?: return false
        val results = Bundle()
        for (ri in inputs) results.putCharSequence(ri.resultKey, text)
        val fill = Intent()
        RemoteInput.addResultsToIntent(inputs, fill, results)
        return try {
            action.actionIntent.send(this, 0, fill)
            true
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "כפתור ההשב כבר לא בתוקף", e)
            false
        }
    }
}
