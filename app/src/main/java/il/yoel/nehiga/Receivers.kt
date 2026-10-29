// נכתב: 2026-10-29 02:10 | עודכן: 2026-10-29 21:00 (גרסה 2: הודעה לפי מכשיר Bluetooth)
package il.yoel.nehiga

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * חיבור למכשיר Bluetooth שהוגדר → מפעיל את ההודעה שנקבעה לו. ניתוק → מכבה.
 * אפשר לכבות ידנית גם כשהמכשיר מחובר (עצירה ארוכה) — המצב יישאר כבוי עד החיבור הבא,
 * כי ההפעלה האוטומטית קורית רק ברגע החיבור.
 */
class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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

        val rule = Prefs.rules(context).firstOrNull { it.address.equals(address, ignoreCase = true) } ?: return

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                EventLog.add(context, "🔵 התחבר: ${rule.device}")
                DrivingMode.set(context, true, DrivingMode.srcBt(rule.device), rule.msgId, address)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                EventLog.add(context, "⚪ התנתק: ${rule.device}")
                if (Prefs.isActive(context) && Prefs.activeBt(context).equals(address, ignoreCase = true)) {
                    DrivingMode.set(context, false, DrivingMode.srcBt(rule.device))
                }
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
