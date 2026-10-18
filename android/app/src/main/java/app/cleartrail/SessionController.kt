package app.cleartrail

import android.content.Context
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class Snapshot(val paired: Boolean, val sessionId: String?, val active: Boolean, val selected: Set<String>, val pending: Int, val permitted: Boolean)

/** All methods are confined to ClearTrailApplication's single executor. */
class SessionController(context: Context) {
    private val vault = Vault(context)
    private val outbox = Outbox(context)
    private val collector = UsageCollector(context)
    private var state = vault.load() ?: JSONObject().put("active", false).put("selected", JSONArray())
    init {
        finishPending()
        // Never infer consent to collect through process downtime.
        if (state.optBoolean("active")) commit(copy().put("active", false).put("from", 0L))
    }
    private fun copy() = JSONObject(state.toString())
    private fun commit(next: JSONObject) { vault.save(next); state = next }
    private fun pairing(): Pairing = Pairing.parse(state.optJSONObject("pairing") ?: error("Pair this phone first"))
    private fun selected(): Set<String> = state.optJSONArray("selected")?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() } ?: emptySet()
    fun snapshot() = Snapshot(state.has("pairing"), state.optString("sessionId").ifEmpty { null }, state.optBoolean("active"), selected(), outbox.files().size, collector.permitted())
    fun pair(raw: String) {
        check(!state.has("pairing") && !state.has("sessionId") && outbox.files().isEmpty()) { "Clear local data before replacing a pairing" }
        require(raw.length <= 4096)
        val pairing = Pairing.parse(JSONObject(raw))
        commit(copy().put("pairing", pairing.json()))
    }
    fun start(apps: Set<String>) {
        pairing()
        check(collector.permitted()) { "Enable Usage Access first" }
        check(!state.has("sessionId")) { "Stop the existing session first" }
        require(apps.isNotEmpty() && UsageCollector.APPS.keys.containsAll(apps)) { "Select at least one application" }
        commit(copy().put("selected", JSONArray(apps.toList())).put("sessionId", UUID.randomUUID().toString()).put("active", true).put("from", System.currentTimeMillis()))
    }
    private fun finishPending() {
        val pending = state.optJSONObject("pending") ?: return
        outbox.put(pending.getString("id"), pending.getJSONObject("envelope"))
        val next = copy().put("from", pending.getLong("to"))
        next.remove("pending")
        commit(next)
    }
    fun capture() {
        finishPending()
        check(state.optBoolean("active")) { "Start or resume a session first" }
        val from = state.getLong("from")
        val to = System.currentTimeMillis()
        check(to >= from) { "Phone clock moved backwards. Stop and discard this interval." }
        if (to == from) return
        val pairing = pairing()
        val payload = collector.collect(from, to, state.getString("sessionId"), selected())
        val id = UUID.randomUUID().toString()
        val envelope = Crypto.encrypt(pairing.key, pairing.deviceId, id, payload)
        // Journal before writing the outbox. Retry uses the same identity and ciphertext.
        val pending = JSONObject().put("id", id).put("envelope", envelope).put("to", to)
        commit(copy().put("pending", pending))
        finishPending()
    }
    fun pause() {
        capture()
        commit(copy().put("active", false).put("from", 0L))
    }
    fun resume() {
        finishPending()
        check(state.has("sessionId") && !state.optBoolean("active"))
        check(collector.permitted()) { "Enable Usage Access first" }
        commit(copy().put("active", true).put("from", System.currentTimeMillis()))
    }
    fun stop() {
        finishPending()
        if (state.optBoolean("active")) capture()
        val next = copy().put("active", false).put("from", 0L)
        next.remove("sessionId")
        commit(next)
    }
    fun discardAndStop() {
        val next = copy().put("active", false).put("from", 0L)
        next.remove("sessionId"); next.remove("pending")
        commit(next)
    }
    fun sync(): Int {
        finishPending()
        // Upload queued records first, so a full queue can recover without collecting more.
        var uploaded = SyncClient().upload(pairing(), outbox)
        if (state.optBoolean("active")) {
            capture()
            uploaded += SyncClient().upload(pairing(), outbox)
        }
        return uploaded
    }
}
