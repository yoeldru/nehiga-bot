// נכתב: 2026-10-29 02:10 | עודכן: 2026-10-29 02:35 (יומן, מקור הפעלה)
package il.yoel.nehiga

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

@SuppressLint("UseSwitchCompatOrMaterialCode")
class MainActivity : Activity() {

    private lateinit var switchActive: Switch
    private lateinit var switchGroups: Switch
    private lateinit var txtStats: TextView
    private lateinit var txtAccess: TextView
    private lateinit var txtCar: TextView
    private lateinit var editMessage: EditText
    private lateinit var btnAccess: Button
    private lateinit var btnAppInfo: Button
    private lateinit var btnBattery: Button

    // מתעדכן כשהמצב משתנה מבחוץ (ווילון, Bluetooth, תשובה שנשלחה)
    private val updates = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchActive = findViewById(R.id.switchActive)
        switchGroups = findViewById(R.id.switchGroups)
        txtStats = findViewById(R.id.txtStats)
        txtAccess = findViewById(R.id.txtAccess)
        txtCar = findViewById(R.id.txtCar)
        editMessage = findViewById(R.id.editMessage)
        btnAccess = findViewById(R.id.btnAccess)
        btnAppInfo = findViewById(R.id.btnAppInfo)
        btnBattery = findViewById(R.id.btnBattery)

        editMessage.setText(Prefs.message(this))

        switchActive.setOnClickListener {
            if (switchActive.isChecked && !hasNotificationAccess()) {
                switchActive.isChecked = false
                Toast.makeText(this, "קודם צריך לתת לבוט גישה להתראות", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            DrivingMode.set(this, switchActive.isChecked, DrivingMode.SRC_APP)
            refresh()
        }

        // setOnClickListener (ולא onCheckedChange) — כדי שרענון המסך לא ירשום ביומן
        switchGroups.setOnClickListener {
            val checked = switchGroups.isChecked
            Prefs.setReplyToGroups(this, checked)
            EventLog.add(this, if (checked) "⚙️ מענה בקבוצות הופעל" else "⚙️ מענה בקבוצות כובה")
        }

        findViewById<Button>(R.id.btnLog).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val text = editMessage.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "ההודעה ריקה", Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setMessage(this, text)
                EventLog.add(this, "⚙️ הודעת המענה עודכנה: $text")
                Toast.makeText(this, "ההודעה נשמרה ✓", Toast.LENGTH_SHORT).show()
            }
        }

        btnAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnAppInfo.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }

        btnBattery.setOnClickListener { requestNoBatteryLimit() }

        findViewById<Button>(R.id.btnCar).setOnClickListener { pickCar() }
        findViewById<Button>(R.id.btnCarClear).setOnClickListener {
            if (Prefs.carName(this) != null) EventLog.add(this, "⚙️ ההפעלה האוטומטית ברכב בוטלה")
            Prefs.setCar(this, null, null)
            refresh()
        }

        askRuntimePermissions()
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

    private fun refresh() {
        val active = Prefs.isActive(this)
        switchActive.isChecked = active
        switchGroups.isChecked = Prefs.replyToGroups(this)

        val names = Prefs.repliedNames(this)
        val src = Prefs.activationSource(this)
        val head = if (src != null) "פעיל (הופעל דרך: $src)" else "פעיל"
        txtStats.text = when {
            !active -> "כבוי — לא נשלחות תשובות"
            names.isEmpty() -> "$head — עוד לא נשלחו תשובות בנסיעה הזו"
            else -> "$head — נענו בנסיעה הזו: " + names.joinToString(", ")
        }

        val access = hasNotificationAccess()
        txtAccess.text = if (access) "✅ גישה להתראות: מאושרת" else "❌ גישה להתראות: חסרה"
        btnAccess.isEnabled = !access

        val pm = getSystemService(PowerManager::class.java)
        val unrestricted = pm.isIgnoringBatteryOptimizations(packageName)
        btnBattery.text = if (unrestricted) "✅ הסוללה לא מוגבלת" else "אל תגביל סוללה לבוט"
        btnBattery.isEnabled = !unrestricted

        val car = Prefs.carName(this)
        txtCar.text = if (car != null) "רכב נבחר: $car" else "לא נבחר רכב — ההפעלה רק ידנית"
    }

    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

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

    @SuppressLint("MissingPermission")
    private fun pickCar() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "צריך לאשר הרשאת \"מכשירים בקרבת מקום\"", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "לא נמצאו מכשירי Bluetooth מצומדים. ודא שה-Bluetooth דולק.", Toast.LENGTH_LONG).show()
            return
        }
        val labels = devices.map { it.name ?: it.address }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("איזה מהם הרכב?")
            .setItems(labels) { _, which ->
                val d = devices[which]
                Prefs.setCar(this, d.address, labels[which])
                EventLog.add(this, "⚙️ נבחר רכב להפעלה אוטומטית: ${labels[which]}")
                refresh()
                Toast.makeText(this, "מעכשיו: חיבור לרכב = מצב נהיגה 🚗", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("ביטול", null)
            .show()
    }
}
