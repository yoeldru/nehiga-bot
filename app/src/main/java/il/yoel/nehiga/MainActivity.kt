// נכתב: 2026-10-29 02:10 | עודכן: 2026-10-29 21:00 (גרסה 2: עיצוב חדש, הודעות מרובות, Bluetooth לפי מכשיר, עדכונים)
package il.yoel.nehiga

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var cardStatus: MaterialCardView
    private lateinit var txtStatusTitle: TextView
    private lateinit var txtStatusMsg: TextView
    private lateinit var txtStats: TextView
    private lateinit var btnToggle: MaterialButton
    private lateinit var cardUpdate: MaterialCardView
    private lateinit var txtUpdateTitle: TextView
    private lateinit var txtUpdateNotes: TextView
    private lateinit var btnUpdate: MaterialButton
    private lateinit var listMessages: LinearLayout
    private lateinit var listRules: LinearLayout
    private lateinit var txtNoRules: TextView
    private lateinit var rowAccess: View
    private lateinit var rowRestricted: View
    private lateinit var rowBattery: View
    private lateinit var rowInstall: View
    private lateinit var rowCheck: View
    private lateinit var switchGroups: MaterialSwitch

    private var downloading = false
    private var checkResultText: String? = null

    // מתעדכן כשהמצב משתנה מבחוץ (ווילון, Bluetooth, תשובה שנשלחה)
    private val updates = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cardStatus = findViewById(R.id.cardStatus)
        txtStatusTitle = findViewById(R.id.txtStatusTitle)
        txtStatusMsg = findViewById(R.id.txtStatusMsg)
        txtStats = findViewById(R.id.txtStats)
        btnToggle = findViewById(R.id.btnToggle)
        cardUpdate = findViewById(R.id.cardUpdate)
        txtUpdateTitle = findViewById(R.id.txtUpdateTitle)
        txtUpdateNotes = findViewById(R.id.txtUpdateNotes)
        btnUpdate = findViewById(R.id.btnUpdate)
        listMessages = findViewById(R.id.listMessages)
        listRules = findViewById(R.id.listRules)
        txtNoRules = findViewById(R.id.txtNoRules)
        rowAccess = findViewById(R.id.rowAccess)
        rowRestricted = findViewById(R.id.rowRestricted)
        rowBattery = findViewById(R.id.rowBattery)
        rowInstall = findViewById(R.id.rowInstall)
        rowCheck = findViewById(R.id.rowCheck)
        switchGroups = findViewById(R.id.switchGroups)

        findViewById<TextView>(R.id.txtVersion).text = "גרסה ${BuildConfig.VERSION_NAME}"

        btnToggle.setOnClickListener {
            val turnOn = !Prefs.isActive(this)
            if (turnOn && !hasNotificationAccess()) {
                toast("קודם צריך לתת לבוט גישה להתראות (בהגדרות למטה)")
                return@setOnClickListener
            }
            DrivingMode.set(this, turnOn, DrivingMode.SRC_APP)
            refresh()
        }

        findViewById<View>(R.id.btnLog).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        findViewById<View>(R.id.btnAddMessage).setOnClickListener {
            startActivity(Intent(this, MessageEditActivity::class.java))
        }
        findViewById<View>(R.id.btnAddRule).setOnClickListener { addRule() }

        rowAccess.setOnClickListener {
            if (!hasNotificationAccess()) startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        rowRestricted.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        rowBattery.setOnClickListener { if (!batteryOk()) requestNoBatteryLimit() }
        rowInstall.setOnClickListener { if (!installOk()) openInstallPermission() }
        rowCheck.setOnClickListener { checkUpdates(manual = true) }
        btnUpdate.setOnClickListener { startUpdate() }

        // setOnClickListener (ולא onCheckedChange) — כדי שרענון המסך לא ירשום ביומן
        switchGroups.setOnClickListener {
            val checked = switchGroups.isChecked
            Prefs.setReplyToGroups(this, checked)
            EventLog.add(this, if (checked) "⚙️ מענה בקבוצות הופעל" else "⚙️ מענה בקבוצות כובה")
        }

        askRuntimePermissions()
        Updater.schedule(this)
        checkUpdates(manual = false)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(DrivingMode.ACTION_UPDATED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(updates, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updates, filter)
        }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(updates)
        } catch (_: Exception) {
        }
    }

    // ================= תצוגה =================

    private fun refresh() {
        val active = Prefs.isActive(this)
        val cur = Prefs.currentMessage(this)

        if (active) {
            cardStatus.setCardBackgroundColor(getColor(R.color.brand))
            listOf(txtStatusTitle, txtStatusMsg, txtStats).forEach { it.setTextColor(Color.WHITE) }
            txtStatusTitle.text = "🟢 מענה אוטומטי פעיל"
            val src = Prefs.activationSource(this)
            txtStatusMsg.text = "נשלחת: ${cur.name}" + (src?.let { "\nהופעל דרך $it" } ?: "")
            val names = Prefs.repliedNames(this)
            txtStats.text = if (names.isEmpty()) "עוד לא נשלחו תשובות"
            else "נענו (${names.size}): " + names.joinToString(", ")
            btnToggle.text = if (Prefs.activeBt(this) != null) "כבה (עד החיבור הבא)" else "כבה"
            btnToggle.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            btnToggle.setTextColor(getColor(R.color.brand))
        } else {
            cardStatus.setCardBackgroundColor(getColor(R.color.card_off))
            val tc = getColor(R.color.text_main)
            listOf(txtStatusTitle, txtStatusMsg, txtStats).forEach { it.setTextColor(tc) }
            txtStatusTitle.text = "⚪ כבוי"
            txtStatusMsg.text = "בהפעלה תישלח: ${Prefs.message(this, Prefs.selectedId(this))?.name.orEmpty()}"
            txtStats.text = "לא נשלחות תשובות אוטומטיות"
            btnToggle.text = "הפעל עכשיו"
            btnToggle.backgroundTintList = ColorStateList.valueOf(getColor(R.color.brand))
            btnToggle.setTextColor(Color.WHITE)
        }

        renderMessages(active, cur.id)
        renderRules()
        renderSettings()
        renderUpdate()
    }

    private fun renderMessages(active: Boolean, currentId: String) {
        listMessages.removeAllViews()
        val sel = Prefs.selectedId(this)
        for (m in Prefs.messages(this)) {
            val v = layoutInflater.inflate(R.layout.item_message, listMessages, false) as MaterialCardView
            val isSel = m.id == sel
            v.findViewById<TextView>(R.id.txtName).text = m.name
            v.findViewById<TextView>(R.id.txtCheck).visibility = if (isSel) View.VISIBLE else View.INVISIBLE
            v.findViewById<TextView>(R.id.txtActive).visibility =
                if (active && m.id == currentId) View.VISIBLE else View.GONE
            v.findViewById<TextView>(R.id.txtBubble).text = WaFormat.render(m.text)
            v.setStrokeWidth(dp(if (isSel) 2 else 1))
            v.setStrokeColor(getColor(if (isSel) R.color.brand else R.color.divider))

            v.setOnClickListener { selectMessage(m) }
            v.findViewById<View>(R.id.btnEdit).setOnClickListener {
                startActivity(Intent(this, MessageEditActivity::class.java).putExtra(MessageEditActivity.EXTRA_ID, m.id))
            }
            v.findViewById<View>(R.id.btnDelete).setOnClickListener {
                MessageActions.confirmDelete(this, m.id) { refresh() }
            }
            listMessages.addView(v)
        }
    }

    private fun selectMessage(m: Prefs.Msg) {
        val active = Prefs.isActive(this)
        val sameSel = m.id == Prefs.selectedId(this)
        if (sameSel && (!active || Prefs.currentId(this) == m.id)) return
        Prefs.setSelectedId(this, m.id)
        if (active) {
            DrivingMode.set(this, true, DrivingMode.SRC_APP, m.id)
        } else {
            EventLog.add(this, "⚙️ נבחרה הודעה: ${m.name}")
            DrivingMode.refreshOngoing(this)
        }
        refresh()
    }

    private fun renderRules() {
        listRules.removeAllViews()
        val rules = Prefs.rules(this)
        txtNoRules.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        for (r in rules) {
            val v = layoutInflater.inflate(R.layout.item_rule, listRules, false)
            v.findViewById<TextView>(R.id.txtDevice).text = r.device
            v.findViewById<TextView>(R.id.txtRuleMsg).text =
                "בחיבור ישלח: " + (Prefs.message(this, r.msgId)?.name ?: "?") + "  (לחץ לשינוי)"
            v.setOnClickListener {
                pickMessage("איזו הודעה לשלוח כשמחוברים ל-${r.device}?") { msg ->
                    Prefs.saveRules(this, Prefs.rules(this).map {
                        if (it.address == r.address) it.copy(msgId = msg.id) else it
                    })
                    EventLog.add(this, "⚙️ ${r.device} ← ${msg.name}")
                    refresh()
                }
            }
            v.findViewById<View>(R.id.btnDelete).setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle("להסיר את ${r.device}?")
                    .setMessage("החיבור למכשיר הזה לא יפעיל יותר מענה אוטומטי.")
                    .setPositiveButton("הסר") { _, _ ->
                        Prefs.saveRules(this, Prefs.rules(this).filter { it.address != r.address })
                        EventLog.add(this, "⚙️ הוסרה הפעלה אוטומטית: ${r.device}")
                        refresh()
                    }
                    .setNegativeButton("ביטול", null)
                    .show()
            }
            listRules.addView(v)
        }
    }

    private fun renderSettings() {
        val access = hasNotificationAccess()
        setRow(
            rowAccess, "גישה להתראות",
            if (access) "מאושרת — הבוט יכול לקרוא ולענות" else "חובה כדי שהבוט יעבוד — לחץ לאישור", access
        )
        rowRestricted.visibility = if (access) View.GONE else View.VISIBLE
        setRow(
            rowRestricted, "המתג של גישה להתראות אפור?",
            "לחץ כאן ← ⋮ למעלה ← \"אפשר הגדרות מוגבלות\", ואז חזור ואשר גישה", null
        )
        val battery = batteryOk()
        setRow(
            rowBattery, "ללא הגבלת סוללה",
            if (battery) "הבוט לא יירדם" else "מומלץ — אחרת סמסונג עלולה להרדים את הבוט", battery
        )
        val install = installOk()
        setRow(
            rowInstall, "התקנת עדכונים מתוך האפליקציה",
            if (install) "מאושר" else "נדרש כדי לעדכן בלחיצת כפתור", install
        )
        setRow(
            rowCheck, "🔄 בדוק עדכונים",
            checkResultText ?: "גרסה מותקנת: ${BuildConfig.VERSION_NAME}", null
        )
        switchGroups.isChecked = Prefs.replyToGroups(this)
    }

    /** ok: true = ✓ ירוק, false = "לחץ" באדום, null = חץ רגיל. */
    private fun setRow(row: View, title: String, sub: String, ok: Boolean?) {
        row.findViewById<TextView>(R.id.rowTitle).text = title
        row.findViewById<TextView>(R.id.rowSub).text = sub
        val st = row.findViewById<TextView>(R.id.rowStatus)
        when (ok) {
            true -> {
                st.text = "✓"
                st.setTextColor(getColor(R.color.ok))
            }
            false -> {
                st.text = "לחץ ›"
                st.setTextColor(getColor(R.color.warn))
            }
            null -> {
                st.text = "›"
                st.setTextColor(getColor(R.color.text_main))
            }
        }
    }

    private fun renderUpdate() {
        val info = Updater.available(this)
        if (info == null) {
            cardUpdate.visibility = View.GONE
            NotificationManagerCompat.from(this).cancel(2002)
            return
        }
        cardUpdate.visibility = View.VISIBLE
        txtUpdateTitle.text = "🎁 גרסה חדשה זמינה: ${info.name}"
        txtUpdateNotes.text = info.notes
        txtUpdateNotes.visibility = if (info.notes.isBlank()) View.GONE else View.VISIBLE
        if (!downloading) {
            btnUpdate.isEnabled = true
            btnUpdate.text = "הורד והתקן"
        }
    }

    // ================= עדכונים =================

    private fun checkUpdates(manual: Boolean) {
        if (manual) {
            checkResultText = "בודק…"
            renderSettings()
        }
        Thread {
            val r = Updater.check(applicationContext)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val msg = when (r) {
                    is Updater.CheckResult.Newer -> "יש גרסה חדשה: ${r.info.name}"
                    is Updater.CheckResult.UpToDate -> "יש לך את הגרסה האחרונה ✓ (${BuildConfig.VERSION_NAME})"
                    is Updater.CheckResult.Failed -> "לא ניתן לבדוק: ${r.reason}"
                }
                if (manual || r !is Updater.CheckResult.Failed) checkResultText = msg
                if (manual) toast(msg)
                refresh()
            }
        }.start()
    }

    private fun startUpdate() {
        val info = Updater.available(this) ?: return
        if (!installOk()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("צריך אישור חד-פעמי")
                .setMessage("כדי לעדכן מתוך האפליקציה, אנדרואיד צריך לאשר לבוט להתקין עדכונים. במסך שייפתח — הדלק את המתג וחזור לכאן.")
                .setPositiveButton("לאישור") { _, _ -> openInstallPermission() }
                .setNegativeButton("ביטול", null)
                .show()
            return
        }
        downloading = true
        btnUpdate.isEnabled = false
        btnUpdate.text = "מוריד…"
        EventLog.add(this, "⬇️ מוריד עדכון: ${info.name}")
        Thread {
            try {
                val f = Updater.download(applicationContext, info) { p ->
                    runOnUiThread { btnUpdate.text = "מוריד… $p%" }
                }
                runOnUiThread {
                    downloading = false
                    btnUpdate.isEnabled = true
                    btnUpdate.text = "התקן"
                    Updater.install(this, f)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    downloading = false
                    btnUpdate.isEnabled = true
                    btnUpdate.text = "נסה שוב"
                    toast("ההורדה נכשלה — בדוק חיבור לאינטרנט")
                    EventLog.add(this, "❗ הורדת העדכון נכשלה: ${e.javaClass.simpleName}")
                }
            }
        }.start()
    }

    private fun installOk() = packageManager.canRequestPackageInstalls()

    private fun openInstallPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
    }

    // ================= Bluetooth =================

    @SuppressLint("MissingPermission")
    private fun addRule() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            toast("צריך לאשר הרשאת \"מכשירים בקרבת מקום\"")
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 2)
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val devices = try {
            adapter?.bondedDevices?.toList().orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        if (devices.isEmpty()) {
            toast("לא נמצאו מכשירי Bluetooth מצומדים. ודא שה-Bluetooth דולק.")
            return
        }
        val labels = devices.map { it.name ?: it.address }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("בחר מכשיר")
            .setItems(labels) { _, which ->
                val d = devices[which]
                val label = labels[which]
                pickMessage("איזו הודעה לשלוח כשמחוברים ל-$label?") { msg ->
                    val rest = Prefs.rules(this).filter { !it.address.equals(d.address, ignoreCase = true) }
                    Prefs.saveRules(this, rest + Prefs.Rule(d.address, label, msg.id))
                    EventLog.add(this, "⚙️ הפעלה אוטומטית: $label ← ${msg.name}")
                    toast("מעכשיו: חיבור ל-$label = ${msg.name}")
                    refresh()
                }
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun pickMessage(title: String, onPick: (Prefs.Msg) -> Unit) {
        val msgs = Prefs.messages(this)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(msgs.map { it.name }.toTypedArray()) { _, which -> onPick(msgs[which]) }
            .setNegativeButton("ביטול", null)
            .show()
    }

    // ================= הרשאות =================

    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun batteryOk(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun askRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.BLUETOOTH_CONNECT
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 1)
    }

    @SuppressLint("BatteryLife")
    private fun requestNoBatteryLimit() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
}
