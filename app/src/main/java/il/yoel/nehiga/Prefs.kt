// נכתב: 2026-10-29 02:10 | עודכן: 2026-10-29 21:00 (גרסה 2: הודעות מרובות, Bluetooth לפי מכשיר, עדכונים)
package il.yoel.nehiga

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** כל ההגדרות והמצב של הבוט, שמורים בטלפון בלבד. */
object Prefs {
    private const val FILE = "nehiga"

    // מצב
    private const val K_ACTIVE = "active"
    private const val K_SOURCE = "source"
    private const val K_CURRENT = "current_msg"
    private const val K_ACTIVE_BT = "active_bt"
    private const val K_REPLIED = "replied"
    private const val K_REPLIED_NAMES = "replied_names"

    // הגדרות
    private const val K_MSGS = "messages_json"
    private const val K_SELECTED = "selected_msg"
    private const val K_RULES = "bt_rules_json"
    private const val K_GROUPS = "groups"
    private const val K_RETENTION = "log_retention"

    // עדכונים
    private const val K_UPD_CODE = "upd_code"
    private const val K_UPD_NAME = "upd_name"
    private const val K_UPD_NOTES = "upd_notes"
    private const val K_UPD_URL = "upd_url"
    private const val K_UPD_NOTIFIED = "upd_notified"

    // מגרסה 1.1 — לצורך העברה
    private const val K_OLD_MESSAGE = "message"
    private const val K_OLD_CAR_ADDR = "car_address"
    private const val K_OLD_CAR_NAME = "car_name"

    data class Msg(val id: String, val name: String, val text: String)
    data class Rule(val address: String, val device: String, val msgId: String)

    private fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun newId(): String = UUID.randomUUID().toString().take(8)

    // ---------- מצב ----------

    fun isActive(c: Context) = sp(c).getBoolean(K_ACTIVE, false)
    fun setActive(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_ACTIVE, v).apply()

    /** מה הדליק את המצב הנוכחי (מוצג במסך וביומן). */
    fun activationSource(c: Context): String? = sp(c).getString(K_SOURCE, null)
    fun setActivationSource(c: Context, v: String?) = sp(c).edit().putString(K_SOURCE, v).apply()

    /** כתובת ה-Bluetooth שהדליק את המצב — כדי לכבות רק כשדווקא הוא מתנתק. */
    fun activeBt(c: Context): String? = sp(c).getString(K_ACTIVE_BT, null)
    fun setActiveBt(c: Context, v: String?) = sp(c).edit().putString(K_ACTIVE_BT, v).apply()

    fun replyToGroups(c: Context) = sp(c).getBoolean(K_GROUPS, false)
    fun setReplyToGroups(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_GROUPS, v).apply()

    /** כמה ימים לשמור את היומן. 0 = לשמור תמיד. */
    fun logRetentionDays(c: Context) = sp(c).getInt(K_RETENTION, 30)
    fun setLogRetentionDays(c: Context, v: Int) = sp(c).edit().putInt(K_RETENTION, v).apply()

    // ---------- הודעות ----------

    private fun readMsgs(c: Context): List<Msg> {
        val raw = sp(c).getString(K_MSGS, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Msg(o.getString("id"), o.optString("name"), o.optString("text"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun messages(c: Context): List<Msg> {
        val list = readMsgs(c)
        if (list.isNotEmpty()) return list
        seed(c)
        return readMsgs(c)
    }

    fun saveMessages(c: Context, list: List<Msg>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("text", it.text)) }
        sp(c).edit().putString(K_MSGS, a.toString()).commit()
    }

    fun message(c: Context, id: String?): Msg? = id?.let { i -> messages(c).firstOrNull { it.id == i } }

    fun upsertMessage(c: Context, m: Msg) {
        val list = messages(c).toMutableList()
        val i = list.indexOfFirst { it.id == m.id }
        if (i >= 0) list[i] = m else list.add(m)
        saveMessages(c, list)
    }

    /** מוחק הודעה. לא מוחק את האחרונה. מכשירים שקושרו אליה מבוטלים. */
    fun deleteMessage(c: Context, id: String): Boolean {
        val list = messages(c)
        if (list.size <= 1) return false
        val rest = list.filter { it.id != id }
        saveMessages(c, rest)
        saveRules(c, rules(c).filter { it.msgId != id })
        if (sp(c).getString(K_SELECTED, null) == id) setSelectedId(c, rest.first().id)
        if (sp(c).getString(K_CURRENT, null) == id) setCurrentId(c, selectedId(c))
        return true
    }

    /** ההודעה שנבחרה (✓) — נשלחת בהפעלה ידנית. */
    fun selectedId(c: Context): String {
        val list = messages(c)
        val id = sp(c).getString(K_SELECTED, null)
        return list.firstOrNull { it.id == id }?.id ?: list.first().id
    }

    fun setSelectedId(c: Context, id: String) = sp(c).edit().putString(K_SELECTED, id).apply()

    /** ההודעה שנשלחת כרגע (כשהמצב פעיל). */
    fun currentId(c: Context): String {
        val id = sp(c).getString(K_CURRENT, null)
        return if (message(c, id) != null) id!! else selectedId(c)
    }

    fun setCurrentId(c: Context, id: String) = sp(c).edit().putString(K_CURRENT, id).apply()

    fun currentMessage(c: Context): Msg = message(c, currentId(c)) ?: messages(c).first()

    /** הודעות התחלתיות. אם הייתה גרסה 1.1 — ההודעה והרכב שלה עוברים לכאן. */
    private fun seed(c: Context) {
        val s = sp(c)
        val driving = Msg(
            newId(), "🚗 נהיגה",
            s.getString(K_OLD_MESSAGE, null) ?: c.getString(R.string.default_message)
        )
        val prayer = Msg(newId(), "🙏 תפילה", "אני באמצע תפילה 🙏 אחזור אליך בהקדם. אם זה דחוף, נא להתקשר.")
        val work = Msg(newId(), "📚 עבודה / לימוד", "אני בשיעור או בעבודה כרגע 📚 אחזור אליך בהפסקה. אם זה דחוף, נא להתקשר.")
        saveMessages(c, listOf(driving, prayer, work))
        s.edit().putString(K_SELECTED, driving.id).commit()

        val oldCar = s.getString(K_OLD_CAR_ADDR, null)
        if (oldCar != null && rules(c).isEmpty()) {
            saveRules(c, listOf(Rule(oldCar, s.getString(K_OLD_CAR_NAME, null) ?: "הרכב", driving.id)))
        }
    }

    // ---------- Bluetooth ----------

    fun rules(c: Context): List<Rule> {
        val raw = sp(c).getString(K_RULES, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Rule(o.getString("address"), o.optString("device"), o.optString("msg"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRules(c: Context, list: List<Rule>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("address", it.address).put("device", it.device).put("msg", it.msgId)) }
        sp(c).edit().putString(K_RULES, a.toString()).commit()
    }

    // ---------- מי כבר נענה ----------

    /** מי כבר קיבל תשובה מאז ההפעלה — כדי שכל אחד יקבל תשובה פעם אחת בלבד. */
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

    /** סימון חד-פעמי (בלי לספור כתשובה) — למשל כדי לרשום ביומן דילוג על קבוצה פעם אחת. */
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

    // ---------- עדכונים ----------

    fun availableUpdate(c: Context): Updater.Info? {
        val s = sp(c)
        val code = s.getInt(K_UPD_CODE, 0)
        if (code == 0) return null
        return Updater.Info(
            code,
            s.getString(K_UPD_NAME, "") ?: "",
            s.getString(K_UPD_NOTES, "") ?: "",
            s.getString(K_UPD_URL, "") ?: ""
        )
    }

    fun setAvailableUpdate(c: Context, info: Updater.Info?) {
        val e = sp(c).edit()
        if (info == null) {
            e.remove(K_UPD_CODE).remove(K_UPD_NAME).remove(K_UPD_NOTES).remove(K_UPD_URL)
        } else {
            e.putInt(K_UPD_CODE, info.code).putString(K_UPD_NAME, info.name)
                .putString(K_UPD_NOTES, info.notes).putString(K_UPD_URL, info.url)
        }
        e.apply()
    }

    fun notifiedCode(c: Context) = sp(c).getInt(K_UPD_NOTIFIED, 0)
    fun setNotifiedCode(c: Context, v: Int) = sp(c).edit().putInt(K_UPD_NOTIFIED, v).apply()
}
