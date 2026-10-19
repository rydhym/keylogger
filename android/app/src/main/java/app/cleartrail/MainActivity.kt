package app.cleartrail

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val applicationState get() = application as ClearTrailApplication
    private lateinit var column: LinearLayout
    private lateinit var status: TextView
    private lateinit var keyboardStatus: TextView
    private lateinit var sessionStatus: TextView
    private lateinit var pairingInput: EditText
    private lateinit var start: Button
    private lateinit var pause: Button
    private lateinit var stop: Button
    private lateinit var capture: Button
    private lateinit var sync: Button
    private lateinit var pair: Button
    private val checkboxes = linkedMapOf<String, CheckBox>()
    private val controls = mutableListOf<Button>()
    private var snapshot: Snapshot? = null
    private var busy = false
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(247, 248, 245)) }
        column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(30), dp(24), dp(30)) }
        scroll.addView(column)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(scroll)
        text("CLEARTRAIL", 14, Color.rgb(22, 94, 80))
        text("Your activity. Your control.", 27)
        text("Track selected-app usage on your own phone. Messages, typed text, notifications and browsing history are not collected. Usage records are encrypted before syncing. You can optionally enable ClearTrail Keyboard for typing; its input never enters dashboard history or email.")
        text("Foundation preview: capture and sync are manual. Capture at least once every 24 hours. After a process restart, sessions pause and the uncaptured interval is discarded.", 12)
        sessionStatus = text("Loading secure storage…", 16)
        status = text("", 13)
        text("Optional: ClearTrail Keyboard", 19)
        text("Enable the keyboard in Android settings, then select it in the system picker. Android will show its standard keyboard warning. Only you can grant access or choose the active keyboard. Keep a system keyboard available to switch back. ClearTrail Keyboard sends taps directly to the focused field, with no typed-text history, suggestions, previews or email. Browser practice history is separate.", 12)
        keyboardStatus = text("Checking keyboard state…", 13)
        button("Enable keyboard in Android settings") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        button("Select keyboard (Android picker)") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
        button("Refresh keyboard state") { refreshKeyboardState() }
        button("1. Open Android Usage Access") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        text("2. Pair with your dashboard", 19)
        pairingInput = EditText(this).apply {
            hint = "Paste the phone setup bundle"
            minLines = 3; maxLines = 5
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
            setTextIsSelectable(false)
        }
        column.addView(pairingInput)
        pair = button("Save pairing securely") {
            val raw = pairingInput.text.toString()
            perform { it.pair(raw); "Paired. Choose apps and start a session." }
            pairingInput.text.clear()
        }
        text("3. Select applications", 19)
        UsageCollector.APPS.forEach { (pkg, label) ->
            val box = CheckBox(this).apply { text = label; isChecked = false }
            checkboxes[pkg] = box; column.addView(box)
        }
        text("Only foreground timestamps and estimated durations for these apps are captured. Selection stays fixed until the session ends.", 12)
        start = button("Start monitoring session") {
            val selected = checkboxes.filterValues { it.isChecked }.keys.toSet()
            perform { it.start(selected); "Session active. Use selected apps, then return to capture or sync." }
        }
        pause = button("Pause session") {
            val active = snapshot?.active == true
            perform { if (active) { it.pause(); "Paused. No activity collected while paused." } else { it.resume(); "Session resumed from now." } }
        }
        capture = button("Capture to encrypted outbox") { perform { it.capture(); "Captured and encrypted locally." } }
        sync = button("Capture and sync now") { perform { "Synced ${it.sync()} encrypted records." } }
        stop = button("Stop session and save interval") { perform { it.stop(); "Session stopped. Pending records remain available to sync." } }
        button("Stop and discard uncaptured interval") {
            confirm("Discard the uncaptured interval? Already encrypted records remain in the outbox.") { perform { it.discardAndStop(); "Session stopped; uncaptured interval discarded." } }
        }
        text("Data controls", 19)
        text("Delete remote records or revoke this phone from the dashboard. Clearing this phone does not delete remote history.", 12)
        button("Clear local data and unpair") {
            confirm("Stop monitoring, delete all queued records and remove pairing from this phone? Remote history remains on the server.") {
                runTask {
                    Outbox(this).clear(); Vault(this).clear(); applicationState.controller = null
                    "Local data cleared and phone unpaired."
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        refreshKeyboardState()
        if (::status.isInitialized && !busy) perform { "Usage Access and session state refreshed. Capture is manual." }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshKeyboardState()
    }
    private fun refreshKeyboardState() {
        if (!::keyboardStatus.isInitialized) return
        val component = ComponentName(this, ClearTrailInputMethod::class.java)
        val manager = getSystemService(InputMethodManager::class.java)
        val enabled = manager.enabledInputMethodList.any { ComponentName.unflattenFromString(it.id) == component }
        val selected = ComponentName.unflattenFromString(Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "") == component
        keyboardStatus.text = "ClearTrail Keyboard: ${if (enabled) "enabled" else "not enabled"}\nSelected input method: ${if (selected) "ClearTrail Keyboard" else "another keyboard"}. Enablement and selection are separate Android settings."
    }
    private fun text(value: String, size: Int = 14, color: Int = Color.rgb(39, 58, 53)): TextView = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); setPadding(0, dp(10), 0, dp(8))
        column.addView(this)
    }
    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { action() }
        column.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        controls.add(this)
    }
    private fun confirm(message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle("Confirm data change").setMessage(message)
            .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ -> action() }.show()
    }
    private fun perform(action: (SessionController) -> String) = runTask { action(applicationState.engine()) }
    private fun runTask(action: () -> String) {
        if (busy) return
        busy = true; controls.forEach { it.isEnabled = false }; status.setText(R.string.working)
        applicationState.executor.execute {
            var next: Snapshot? = null
            val message = try { action() } catch (error: Exception) {
                // Use controlled messages only; JSON/crypto failures can include secret-bearing inputs.
                when (error) {
                    is IllegalStateException -> error.message ?: "Operation failed safely. Try again or clear local data."
                    else -> "Could not complete the operation. Check pairing, network and secure storage. No plaintext fallback was used."
                }
            }
            try { next = applicationState.engine().snapshot() } catch (_: Exception) { /* Keep reset available. */ }
            runOnUiThread {
                if (!isDestroyed) {
                    busy = false; status.text = message; snapshot = next; updateControls(next)
                }
            }
        }
    }
    private fun updateControls(value: Snapshot?) {
        controls.forEach { it.isEnabled = true }
        val hasSession = value?.sessionId != null
        start.isEnabled = value?.paired == true && value.permitted && !hasSession
        pause.isEnabled = hasSession
        pause.text = if (value?.active == true) "Pause session" else "Resume session"
        capture.isEnabled = value?.active == true
        stop.isEnabled = hasSession
        sync.isEnabled = value?.paired == true
        pair.isEnabled = value != null && !value.paired
        pairingInput.isEnabled = pair.isEnabled
        checkboxes.forEach { (pkg, checkbox) ->
            checkbox.isEnabled = !hasSession
            if (hasSession) checkbox.isChecked = pkg in (value?.selected ?: emptySet())
        }
        sessionStatus.text = if (value == null) "Secure storage unavailable. Reset local data if recovery is impossible." else {
            val phase = if (value.active) "ACTIVE · Manual capture" else if (hasSession) "PAUSED" else "STOPPED"
            "$phase\nUsage Access: ${if (value.permitted) "enabled" else "off"}\nDashboard: ${if (value.paired) "paired" else "not paired"} · Outbox: ${value.pending}"
        }
    }
}
