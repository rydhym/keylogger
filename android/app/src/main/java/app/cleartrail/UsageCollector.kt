package app.cleartrail

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.BatteryManager
import android.os.Process
import android.os.UserManager
import org.json.JSONArray
import org.json.JSONObject

class UsageCollector(private val context: Context) {
    companion object {
        val APPS = linkedMapOf("com.whatsapp" to "WhatsApp", "com.instagram.android" to "Instagram", "com.android.chrome" to "Chrome")
    }
    fun permitted(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    fun collect(from: Long, to: Long, sessionId: String, selected: Set<String>): JSONObject {
        check(permitted()) { "Usage Access is off. Re-enable it before capture." }
        check(context.getSystemService(UserManager::class.java).isUserUnlocked) { "Unlock the phone before capture." }
        require(selected.isNotEmpty() && APPS.keys.containsAll(selected))
        require(to > from && to - from <= 86_400_000L) { "Capture at least once every 24 hours. Stop and discard if this session is too old." }
        val source = context.getSystemService(UsageStatsManager::class.java).queryEvents(maxOf(0L, from - 300_000L), to)
            ?: error("Android usage history is unavailable")
        val event = UsageEvents.Event()
        val events = mutableListOf<Transition>()
        var scanned = 0
        while (source.hasNextEvent()) {
            check(++scanned <= 20_000) { "Usage history is too large for one capture" }
            source.getNextEvent(event)
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> Transition.Kind.RESUME
                UsageEvents.Event.ACTIVITY_PAUSED -> Transition.Kind.PAUSE
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> Transition.Kind.SCREEN_OFF
                UsageEvents.Event.DEVICE_SHUTDOWN -> Transition.Kind.SHUTDOWN
                else -> null
            } ?: continue
            val pkg = event.packageName ?: ""
            if (pkg in selected || kind == Transition.Kind.SCREEN_OFF || kind == Transition.Kind.SHUTDOWN) {
                events.add(Transition(pkg, event.timeStamp, kind, event.className ?: ""))
            }
        }
        check(permitted()) { "Usage Access changed during capture" }
        val intervals = UsageIntervals.derive(events, selected, from, to)
        val apps = JSONArray()
        for ((pkg, values) in intervals) {
            val array = JSONArray()
            for (item in values) array.put(JSONObject().put("start", item.start).put("end", item.end).put("estimated", true))
            apps.put(JSONObject().put("packageName", pkg).put("intervals", array))
        }
        val battery = context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return JSONObject().put("schemaVersion", 1).put("sessionId", sessionId).put("from", from).put("to", to)
            .put("battery", if (battery in 0..100) battery else -1).put("apps", apps)
    }
}
