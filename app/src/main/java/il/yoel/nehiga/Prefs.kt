// נכתב: 2026-10-29 02:10 | עודכן: 2026-10-29 02:35 (הגדרות יומן)
package il.yoel.nehiga

import android.content.Context
import android.content.SharedPreferences

/** כל ההגדרות והמצב של הבוט, שמורים בטלפון. */
object Prefs {
    private const val FILE = "nehiga"
    private const val K_ACTIVE = "active"
    private const val K_MESSAGE = "message"
    private const val K_GROUPS = "groups"
    private const val K_CAR_ADDR = "car_address"
    private const val K_CAR_NAME = "car_name"
    private const val K_REPLIED = "replied"
    private const val K_REPLIED_NAMES = "replied_names"

    private fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private const val K_SOURCE = "source"
    private const val K_RETENTION = "log_retention"

    /** כמה ימים לשמור את היומן. 0 = לשמור תמיד. */
    fun logRetentionDays(c: Context) = sp(c).getInt(K_RETENTION, 30)
    fun setLogRetentionDays(c: Context, v: Int) = sp(c).edit().putInt(K_RETENTION, v).apply()

    /** מה הדליק את המצב הנוכחי — כדי להציג "הופעל אוטומטית מהרכב". */
    fun activationSource(c: Context): String? = sp(c).getString(K_SOURCE, null)
    fun setActivationSource(c: Context, v: String?) = sp(c).edit().putString(K_SOURCE, v).apply()

    fun isActive(c: Context) = sp(c).getBoolean(K_ACTIVE, false)
    fun setActive(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_ACTIVE, v).apply()

    fun message(c: Context): String =
        sp(c).getString(K_MESSAGE, null) ?: c.getString(R.string.default_message)
    fun setMessage(c: Context, v: String) = sp(c).edit().putString(K_MESSAGE, v).apply()

    fun replyToGroups(c: Context) = sp(c).getBoolean(K_GROUPS, false)
    fun setReplyToGroups(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_GROUPS, v).apply()

    fun carAddress(c: Context): String? = sp(c).getString(K_CAR_ADDR, null)
    fun carName(c: Context): String? = sp(c).getString(K_CAR_NAME, null)
    fun setCar(c: Context, address: String?, name: String?) =
        sp(c).edit().putString(K_CAR_ADDR, address).putString(K_CAR_NAME, name).apply()

    /** מי כבר קיבל תשובה בנסיעה הנוכחית — כדי שכל אחד יקבל תשובה פעם אחת בלבד. */
    fun alreadyReplied(c: Context, key: String) =
        sp(c).getStringSet(K_REPLIED, emptySet())!!.contains(key)

    /** מסמן שענינו. מחזיר false אם מישהו כבר סימן לפנינו (הגנה מכפילויות). */
    @Synchronized
    fun markReplied(c: Context, key: String, displayName: String): Boolean {
        val set = sp(c).getStringSet(K_REPLIED, emptySet())!!.toMutableSet()
        if (!set.add(key)) return false
        val names = sp(c).getStringSet(K_REPLIED_NAMES, emptySet())!!.toMutableSet()
        names.add(displayName)
        sp(c).edit().putStringSet(K_REPLIED, set).putStringSet(K_REPLIED_NAMES, names).commit()
        return true
    }

    /** סימון חד-פעמי בנסיעה (בלי לספור כתשובה) — למשל כדי לרשום ביומן דילוג על קבוצה פעם אחת. */
    @Synchronized
    fun markOnce(c: Context, key: String): Boolean {
        val set = sp(c).getStringSet(K_REPLIED, emptySet())!!.toMutableSet()
        if (!set.add(key)) return false
        sp(c).edit().putStringSet(K_REPLIED, set).commit()
        return true
    }

    fun unmarkReplied(c: Context, key: String) {
        val set = sp(c).getStringSet(K_REPLIED, emptySet())!!.toMutableSet()
        set.remove(key)
        sp(c).edit().putStringSet(K_REPLIED, set).apply()
    }

    fun repliedNames(c: Context): Set<String> = sp(c).getStringSet(K_REPLIED_NAMES, emptySet())!!

    fun clearReplied(c: Context) =
        sp(c).edit().remove(K_REPLIED).remove(K_REPLIED_NAMES).apply()
}
