// נכתב: 2026-10-29 02:10 | עודכן: 2026-10-29 02:35 (יומן)
package il.yoel.nehiga

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * מדליק את מצב הנהיגה כשהטלפון מתחבר ל-Bluetooth של הרכב, ומכבה כשהוא מתנתק.
 * אפשר לכבות ידנית גם כשהרכב מחובר (עצירה ארוכה) — המצב יישאר כבוי עד החיבור הבא לרכב,
 * כי ההפעלה האוטומטית קורית רק ברגע החיבור.
 */
class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val car = Prefs.carAddress(context) ?: return

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val address = try {
            device?.address
        } catch (_: SecurityException) {
            null
        } ?: return
        if (!address.equals(car, ignoreCase = true)) return

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                EventLog.add(context, "🔵 הטלפון התחבר לרכב")
                DrivingMode.set(context, true, DrivingMode.SRC_CAR)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                EventLog.add(context, "⚪ הטלפון התנתק מהרכב")
                if (Prefs.isActive(context)) DrivingMode.set(context, false, DrivingMode.SRC_CAR)
            }
        }
    }
}

/** כפתור "כבה" שבהתראה הקבועה. */
class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DrivingMode.set(context, false, DrivingMode.SRC_NOTIF)
    }
}
