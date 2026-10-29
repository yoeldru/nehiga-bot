// נכתב: 2026-10-29 21:00
package il.yoel.nehiga

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * עדכונים: בודק ב-GitHub אם יש גרסה חדשה, מציג התראה (ונקודה על האייקון),
 * מוריד את הקובץ ופותח את מסך ההתקנה של אנדרואיד.
 * המאגר חייב להיות ציבורי כדי שהאפליקציה תוכל לקרוא ממנו.
 */
object Updater {
    private const val API = "https://api.github.com/repos/yoeldru/nehiga-bot/releases/latest"
    private const val CHANNEL_ID = "updates"
    private const val NOTIF_ID = 2002

    data class Info(val code: Int, val name: String, val notes: String, val url: String)

    sealed class CheckResult {
        data class Newer(val info: Info) : CheckResult()
        object UpToDate : CheckResult()
        data class Failed(val reason: String) : CheckResult()
    }

    /** בודק ב-GitHub. רץ ברקע (לא על המסך הראשי). */
    fun check(c: Context): CheckResult {
        return try {
            val conn = URL(API).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "nehiga-bot")
            val status = conn.responseCode
            if (status == 404) return CheckResult.Failed("לא נמצאו גרסאות (ייתכן שהמאגר עדיין פרטי)")
            if (status != 200) return CheckResult.Failed("שגיאת שרת $status")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })

            val tag = json.optString("tag_name")
            val code = tag.removePrefix("v").toIntOrNull() ?: return CheckResult.UpToDate
            val assets = json.optJSONArray("assets")
            var url: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url")
                }
            }
            if (url.isNullOrEmpty()) return CheckResult.Failed("לא נמצא קובץ התקנה")

            val info = Info(code, json.optString("name", tag), cleanNotes(json.optString("body")), url)
            if (code > BuildConfig.VERSION_CODE) {
                Prefs.setAvailableUpdate(c, info)
                maybeNotify(c, info)
                CheckResult.Newer(info)
            } else {
                clear(c)
                CheckResult.UpToDate
            }
        } catch (_: Exception) {
            CheckResult.Failed("אין חיבור לאינטרנט")
        }
    }

    /** העדכון השמור, אם הוא באמת חדש מהגרסה המותקנת. */
    fun available(c: Context): Info? =
        Prefs.availableUpdate(c)?.takeIf { it.code > BuildConfig.VERSION_CODE && it.url.isNotEmpty() }

    fun clear(c: Context) {
        Prefs.setAvailableUpdate(c, null)
        NotificationManagerCompat.from(c).cancel(NOTIF_ID)
    }

    private fun cleanNotes(s: String): String =
        s.lines()
            .filterNot { it.startsWith("Co-Authored-By") || it.startsWith("Claude-Session") }
            .joinToString("\n").trim()

    /** התראה פעם אחת לכל גרסה. ההתראה גם מוסיפה נקודה על אייקון האפליקציה. */
    private fun maybeNotify(c: Context, info: Info) {
        if (Prefs.notifiedCode(c) >= info.code) return
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "עדכונים", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { setShowBadge(true) }
            )
        }
        val open = PendingIntent.getActivity(
            c, 10, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle("🎁 עדכון זמין לבוט נהיגה")
            .setContentText("${info.name} — לחץ כדי לעדכן")
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.notes.ifBlank { info.name }))
            .setContentIntent(open)
            .setNumber(1)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(c).notify(NOTIF_ID, n)
            Prefs.setNotifiedCode(c, info.code)
            EventLog.add(c, "🎁 נמצא עדכון: ${info.name}")
        } catch (_: SecurityException) {
        }
    }

    /** מוריד את קובץ ההתקנה. רץ ברקע. */
    fun download(c: Context, info: Info, progress: (Int) -> Unit): File {
        val dir = File(c.cacheDir, "updates").apply { mkdirs() }
        val f = File(dir, "nehiga.apk")
        val conn = URL(info.url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "nehiga-bot")
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            f.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastPct = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            progress(pct)
                        }
                    }
                }
            }
        }
        return f
    }

    /** פותח את מסך ההתקנה של אנדרואיד. */
    fun install(c: Context, f: File) {
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", f)
        c.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** בדיקה אוטומטית כל 12 שעות, כשיש אינטרנט. */
    fun schedule(c: Context) {
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(c)
            .enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class UpdateWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        Updater.check(applicationContext)
        return Result.success()
    }
}
