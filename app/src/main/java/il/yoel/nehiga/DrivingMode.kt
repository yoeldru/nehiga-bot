// נכתב: 2026-10-29 02:10 | עודכן: 2026-10-29 21:00 (גרסה 2: בחירת הודעה, Bluetooth לפי מכשיר)
package il.yoel.nehiga

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** נקודה אחת שמדליקה, מכבה ומחליפה הודעה — מהאפליקציה, מהווילון או מה-Bluetooth. */
object DrivingMode {
    private const val CHANNEL_ID = "driving2"
    private const val OLD_CHANNEL_ID = "driving"
    private const val NOTIF_ID = 1001
    const val ACTION_UPDATED = "il.yoel.nehiga.UPDATED"

    // מקורות הפעלה/כיבוי (מוצגים ביומן ובמסך)
    const val SRC_APP = "מסך האפליקציה"
    const val SRC_TILE = "כפתור בווילון"
    const val SRC_NOTIF = "כפתור כבה בהתראה"
    fun srcBt(device: String) = "Bluetooth: $device"

    /**
     * on=true: מפעיל (או מחליף) הודעה. msgId=null → ההודעה שנבחרה (✓).
     * btAddress: אם ההפעלה הגיעה מ-Bluetooth — כדי לכבות כשדווקא הוא מתנתק.
     */
    fun set(c: Context, on: Boolean, source: String, msgId: String? = null, btAddress: String? = null) {
        val ctx = c.applicationContext
        val wasOn = Prefs.isActive(ctx)
        val oldId = Prefs.currentId(ctx)

        if (on) {
            val id = msgId ?: Prefs.selectedId(ctx)
            // הפעלה חדשה או הודעה אחרת — כולם יקבלו שוב תשובה (פעם אחת)
            if (!wasOn || id != oldId) Prefs.clearReplied(ctx)
            Prefs.setCurrentId(ctx, id)
            if (btAddress != null) Prefs.setActiveBt(ctx, btAddress)
            else if (!wasOn) Prefs.setActiveBt(ctx, null)
            if (!wasOn) Prefs.setActivationSource(ctx, source)
            Prefs.setActive(ctx, true)

            val name = Prefs.currentMessage(ctx).name
            if (!wasOn) EventLog.add(ctx, "🟢 מענה אוטומטי הופעל: $name ($source)")
            else if (id != oldId) EventLog.add(ctx, "🔄 הוחלפה הודעה: $name ($source)")
            showOngoing(ctx)
        } else {
            if (wasOn) {
                Prefs.setActive(ctx, false)
                Prefs.setActivationSource(ctx, null)
                Prefs.setActiveBt(ctx, null)
                EventLog.add(ctx, "🔴 מענה אוטומטי כובה ($source)")
            }
            hideOngoing(ctx)
        }
        refreshTile(ctx)
        notifyUi(ctx)
    }

    fun notifyUi(c: Context) {
        c.sendBroadcast(Intent(ACTION_UPDATED).setPackage(c.packageName))
    }

    /** מעדכן את ההתראה הקבועה (אחרי תשובה, עריכה וכו'). */
    fun refreshOngoing(c: Context) {
        if (Prefs.isActive(c)) showOngoing(c.applicationContext)
        refreshTile(c.applicationContext)
    }

    private fun refreshTile(c: Context) {
        try {
            TileService.requestListeningState(c, ComponentName(c, DrivingTileService::class.java))
        } catch (_: Exception) {
        }
    }

    private fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(OLD_CHANNEL_ID)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "מענה אוטומטי פעיל", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) } // הנקודה על האייקון שמורה לעדכונים
            )
        }
    }

    private fun showOngoing(c: Context) {
        ensureChannel(c)
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getBroadcast(
            c, 1, Intent(c, StopReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val fromBt = Prefs.activeBt(c) != null
        val count = Prefs.repliedNames(c).size
        val text = buildString {
            Prefs.activationSource(c)?.let { append("הופעל דרך $it · ") }
            append(if (count == 0) "עוד לא נשלחו תשובות" else "נענו $count אנשים")
        }
        // כשהופעל מ-Bluetooth — הכיבוי תקף עד החיבור הבא
        val stopLabel = if (fromBt) "כבה עד החיבור הבא" else "כבה"

        val n = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle("🟢 מענה אוטומטי: ${Prefs.currentMessage(c).name}")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, stopLabel, stop)
            .build()
        try {
            NotificationManagerCompat.from(c).notify(NOTIF_ID, n)
        } catch (_: SecurityException) {
            // אין הרשאת התראות — הבוט עדיין עובד, רק בלי ההתראה הקבועה
        }
    }

    private fun hideOngoing(c: Context) {
        NotificationManagerCompat.from(c).cancel(NOTIF_ID)
    }
}
