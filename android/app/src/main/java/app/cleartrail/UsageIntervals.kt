package app.cleartrail

data class Transition(val packageName: String, val timestamp: Long, val kind: Kind, val activity: String = "") {
    enum class Kind { RESUME, PAUSE, SCREEN_OFF, SHUTDOWN }
}
data class UsageInterval(val start: Long, val end: Long)

/** Foreground estimates from observed lifecycle transitions, not proof of user attention. */
object UsageIntervals {
    fun derive(events: List<Transition>, selected: Set<String>, from: Long, to: Long): Map<String, List<UsageInterval>> {
        require(from >= 0 && to > from && to - from <= 86_400_000L)
        require(events.size <= 20_000)
        val active = mutableMapOf<String, MutableSet<String>>()
        val starts = mutableMapOf<String, Long>()
        val result = selected.associateWith { mutableListOf<UsageInterval>() }
        fun close(pkg: String, time: Long) {
            val start = starts.remove(pkg) ?: return
            if (time > start) {
                val list = result.getValue(pkg)
                if (list.lastOrNull()?.end == start) {
                    val previous = list.removeAt(list.lastIndex)
                    list.add(UsageInterval(previous.start, time))
                } else list.add(UsageInterval(start, time))
            }
        }
        // Seed state with metadata from just before the active boundary, but never emit it.
        for (event in events.sortedBy { it.timestamp }) {
            if (event.timestamp >= to) break
            if (event.kind == Transition.Kind.SCREEN_OFF || event.kind == Transition.Kind.SHUTDOWN) {
                starts.keys.toList().forEach { close(it, maxOf(from, event.timestamp)) }
                active.clear()
                continue
            }
            if (event.packageName !in selected) continue
            val running = active.getOrPut(event.packageName) { mutableSetOf() }
            when (event.kind) {
                Transition.Kind.RESUME -> {
                    if (running.isEmpty()) starts[event.packageName] = maxOf(from, event.timestamp)
                    running.add(event.activity)
                }
                Transition.Kind.PAUSE -> {
                    running.remove(event.activity)
                    if (running.isEmpty()) close(event.packageName, maxOf(from, event.timestamp))
                }
                else -> Unit
            }
        }
        starts.keys.toList().forEach { close(it, to) }
        require(result.values.sumOf { it.size } <= 512) { "Too many intervals; use shorter capture sessions" }
        return result
    }
}
