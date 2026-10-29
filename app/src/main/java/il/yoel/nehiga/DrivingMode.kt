// נכתב: 2026-10-29 02:10 | עודכן: 2026-10-29 02:35 (יומן + מקור ההפעלה)
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

/** נקודה אחת שמדליקה ומכבה את מצב הנהיגה — מהאפליקציה, מהכפתור בווילון או מה-Bluetooth. */
object DrivingMode {
    private const val CHANNEL_ID = "driving"
    private const val NOTIF_ID = 1001
    const val ACTION_UPDATED = "il.yoel.nehiga.UPDATED"

    // מקורות הפעלה/כיבוי (מוצגים ביומן)
    const val SRC_APP = "מתג באפליקציה"
    const val SRC_TILE = "כפתור בווילון"
    const val SRC_CAR = "Bluetooth של הרכב"
    const val SRC_NOTIF = "כפתור כבה בהתראה"

    fun set(c: Context, on: Boolean, source: String) {
        val ctx = c.applicationContext
        val wasOn = Prefs.isActive(ctx)
        if (on && !wasOn) {
            // נסיעה חדשה — כולם יקבלו שוב תשובה פעם אחת
            Prefs.clearReplied(ctx)
        }
        Prefs.setActive(ctx, on)
        Prefs.setActivationSource(ctx, if (on) source else null)
        if (on != wasOn) {
            EventLog.add(ctx, if (on) "🟢 מצב נהיגה הופעל ($source)" else "🔴 מצב נהיגה כובה ($source)")
        }
        if (on) showOngoing(ctx) else hideOngoing(ctx)
        refreshTile(ctx)
        notifyUi(ctx)
    }

    fun notifyUi(c: Context) {
        c.sendBroadcast(Intent(ACTION_UPDATED).setPackage(c.packageName))
    }

    /** מעדכן את טקסט ההתראה הקבועה (למשל אחרי שנשלחה תשובה). */
    fun refreshOngoing(c: Context) {
        if (Prefs.isActive(c)) showOngoing(c.applicationContext)
    }

    private fun refreshTile(c: Context) {
        try {
            TileService.requestListeningState(c, ComponentName(c, DrivingTileService::class.java))
        } catch (_: Exception) {
        }
    }

    private fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "מצב נהיגה", NotificationManager.IMPORTANCE_LOW)
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
        val fromCar = Prefs.activationSource(c) == SRC_CAR
        val count = Prefs.repliedNames(c).size
        val text = buildString {
            append(if (fromCar) "הופעל אוטומטית מהרכב. " else "")
            append(if (count == 0) "עונה אוטומטית בווטסאפ" else "נענו $count אנשים בנסיעה הזו")
        }
        // כשהופעל מהרכב — הכיבוי תקף עד הנסיעה הבאה (ההפעלה הבאה תהיה בחיבור הבא לרכב)
        val stopLabel = if (fromCar) "כבה עד הנסיעה הבאה" else "כבה"

        val n = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle("🚗 מצב נהיגה פעיל")
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
