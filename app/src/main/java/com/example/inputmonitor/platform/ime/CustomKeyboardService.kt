package com.example.inputmonitor.platform.ime

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

import com.example.inputmonitor.MonitoringApplication
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.RawInteractionEvent

/**
 * Android InputMethodService implementing the custom keyboard (SRS.md §22, Architecture.md §23).
 *
 * Implements the Keyboard Independence rule:
 * Keyboard typing operates completely isolated from event logging or persistence layers.
 */
class CustomKeyboardService : InputMethodService() {

    private val keyProcessor = KeyProcessor()
    private val keyboardController = KeyboardController()
    private var keyboardView: KeyboardView? = null
    private data class PendingMedia(val uri: android.net.Uri, val mime: String,
        val editorPackage: String?, val field: Int?)
    private var pendingMedia: PendingMedia? = null

    private fun deliverPendingMedia() {
        val media = pendingMedia ?: return
        pendingMedia = null
        val info = currentInputEditorInfo
        val supported = info?.contentMimeTypes.orEmpty().any {
            android.content.ClipDescription.compareMimeTypes(media.mime, it)
        }
        val sent = if (info?.packageName == media.editorPackage && info?.fieldId == media.field && supported) {
            runCatching {
                currentInputConnection?.commitContent(android.view.inputmethod.InputContentInfo(media.uri,
                    android.content.ClipDescription("Keyboard media", arrayOf(media.mime)), null),
                    InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null) == true
            }.getOrDefault(false)
        } else false
        if (!sent) android.widget.Toast.makeText(this,
            "This field does not support this media. Try a messaging attachment field.",
            android.widget.Toast.LENGTH_LONG).show()
    }
    private val typingSuggestions by lazy {
        TypingSuggestions(this, { currentInputConnection }) { keyboardView?.setSuggestions(it) }
    }

    // Allows test harness injection; falls back to AppContainer singleton
    var publisherOverride: ImeEventPublisher? = null
    internal val publisher: ImeEventPublisher?
        get() = publisherOverride ?: runCatching { (application as? MonitoringApplication)?.container?.imeEventPublisher }.getOrNull()
    var inputConnectionOverride: InputConnection? = null
    var editorInfoOverride: EditorInfo? = null

    override fun getCurrentInputConnection(): InputConnection? {
        return inputConnectionOverride ?: runCatching { super.getCurrentInputConnection() }.getOrNull()
    }

    override fun getCurrentInputEditorInfo(): EditorInfo? {
        return editorInfoOverride ?: runCatching { super.getCurrentInputEditorInfo() }.getOrNull()
    }

    override fun onEvaluateInputViewShown(): Boolean {
        return true
    }

    // Keep a consistent docked keyboard rather than changing to an extracted
    // fullscreen editor on rotation, which has a separate show/hide lifecycle.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        if (super.onShowInputRequested(flags, configChange)) return true
        // The default may reject an implicit re-show when an emulator/hardware
        // keyboard is present. Honor the app's request while an editor is active.
        val inputType = currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL
        return inputType != InputType.TYPE_NULL && onEvaluateInputViewShown()
    }

    override fun onCreateInputView(): View {
        val view = KeyboardView(this) { action ->
            handleKeyAction(action)
        }
        view.setLayout(keyboardController.currentLayout.value)
        keyboardView = view
        view.setSuggestionsAllowed(SuggestionPolicy.allows(currentInputEditorInfo?.inputType ?: 0, currentInputEditorInfo?.imeOptions ?: 0))
        view.onMedia = { mime ->
            val editorPackage = currentInputEditorInfo?.packageName
            val editorField = currentInputEditorInfo?.fieldId
            KeyboardMediaActivity.deliver = { uri, actualMime ->
                pendingMedia = PendingMedia(uri, actualMime, editorPackage, editorField)
            }
            startActivity(android.content.Intent(this, KeyboardMediaActivity::class.java)
                .putExtra("mime", mime).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        view.onSuggestion = { runCatching { typingSuggestions.choose(it) }; Unit }
        return view
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        keyboardController.updateEditorInfo(attribute)
        runCatching { typingSuggestions.start(attribute) }
        keyboardView?.setSuggestionsAllowed(SuggestionPolicy.allows(attribute?.inputType ?: 0, attribute?.imeOptions ?: 0))
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardController.updateEditorInfo(info)
        // Android may recreate the IME window independently of the editor session.
        val view = keyboardView ?: (onCreateInputView() as KeyboardView).also { setInputView(it) }
        view.startInput(keyboardController.currentLayout.value)
        view.setSuggestionsAllowed(SuggestionPolicy.allows(info?.inputType ?: 0, info?.imeOptions ?: 0))
        runCatching { typingSuggestions.refresh() }
        deliverPendingMedia()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        keyboardController.setShift(ShiftState.OFF)
    }

    internal fun handleKeyAction(action: KeyAction) {
        // Step 1: Input delivery to target app via InputConnection ALWAYS executes first
        when (action) {
            is KeyAction.Shift -> {
                keyboardController.toggleShift()
                keyboardView?.setLayout(keyboardController.currentLayout.value)
            }
            is KeyAction.SwitchIme -> {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            }
            is KeyAction.SwitchLayout -> {
                keyboardController.switchLayout(action.target)
                keyboardView?.setLayout(keyboardController.currentLayout.value)
            }
            is KeyAction.Character -> {
                keyProcessor.processKey(action, currentInputConnection, currentInputEditorInfo)
                if (keyboardController.shiftState == ShiftState.ON_ONCE) {
                    keyboardController.onCharacterTyped()
                    keyboardView?.setLayout(keyboardController.currentLayout.value)
                }
                emitKeyEventCandidate(action)
                runCatching { typingSuggestions.refresh() }
            }
            is KeyAction.Space,
            is KeyAction.Backspace,
            is KeyAction.Enter -> {
                if (action == KeyAction.Space) runCatching { typingSuggestions.correctBeforeSpace() }
                keyProcessor.processKey(action, currentInputConnection, currentInputEditorInfo)
                emitKeyEventCandidate(action)
                runCatching { typingSuggestions.refresh() }
            }
        }
    }

    /**
     * Emits a raw event candidate asynchronously.
     * Isolated in a defensive try-catch so auxiliary pipeline failures never impact typing.
     */
    private fun emitKeyEventCandidate(action: KeyAction) {
        try {
            val pub = publisher ?: return
            val context = keyboardController.currentEditorContext
            val packageName = currentInputEditorInfo?.packageName ?: context?.packageName

            val (eventType, candidateData) = when (action) {
                is KeyAction.Character -> EventType.TEXT_COMMITTED to action.text
                is KeyAction.Space -> EventType.TEXT_COMMITTED to " "
                is KeyAction.Backspace -> EventType.DELETE_KEY to null
                is KeyAction.Enter -> EventType.ACTION_KEY to (context?.imeActionLabel ?: "↵")
                else -> return
            }

            val classification = context?.suggestedClassification ?: InputClassification.UNSPECIFIED

            val rawEvent = RawInteractionEvent(
                timestamp = System.currentTimeMillis(),
                source = EventSource.KEYBOARD,
                eventType = eventType,
                packageName = packageName,
                candidateData = candidateData,
                inputClassification = classification,
                metadata = buildMap {
                    context?.fieldCategory?.let { put("fieldCategory", it.name) }
                    context?.imeActionLabel?.let { put("actionLabel", it) }
                }
            )

            pub.publish(rawEvent)
        } catch (_: Throwable) {
            // Failure tolerance: logging failures must never affect typing
        }
    }

    override fun onDestroy() {
        KeyboardMediaActivity.deliver = null
        pendingMedia = null
        typingSuggestions.close()
        keyboardView = null
        super.onDestroy()
    }

    override fun onFinishInput() {
        typingSuggestions.close()
        super.onFinishInput()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int,
        newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        runCatching { typingSuggestions.refresh() }
    }
}
