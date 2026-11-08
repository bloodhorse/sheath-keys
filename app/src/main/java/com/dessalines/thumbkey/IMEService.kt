package com.dessalines.thumbkey

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dessalines.thumbkey.db.DEFAULT_CLIPBOARD_HISTORY_ENABLED
import com.dessalines.thumbkey.db.DEFAULT_DISABLE_FULLSCREEN_EDITOR
import com.dessalines.thumbkey.db.DEFAULT_SHOW_ON_SCREEN_KEYBOARD
import com.dessalines.thumbkey.db.DEFAULT_SWITCH_IME_MODE
import com.dessalines.thumbkey.db.DEFAULT_USE_PRIVATE_CLIPBOARD
import com.dessalines.thumbkey.ui.components.keyboard.SketchState
import com.dessalines.thumbkey.utils.ImeSwitchMode
import com.dessalines.thumbkey.utils.KeyboardDefinition
import com.dessalines.thumbkey.utils.KeyboardLayout
import com.dessalines.thumbkey.utils.TAG
import com.dessalines.thumbkey.utils.ThumbKeyClipboardManager
import com.dessalines.thumbkey.utils.resolveKeyboardDefinition
import com.dessalines.thumbkey.utils.toBool
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.WindowManager
import java.util.function.Consumer
import com.dessalines.thumbkey.utils.PANEL_INSET_DP
import android.view.WindowInsets

private const val PANEL_RADIUS_DP = 22f
private const val PANEL_BLUR_DP = 40f
private const val PANEL_FROST = 0xCC101012.toInt()
private const val PANEL_SOLID = 0xF2101012.toInt()
// thevoid's light: Dawnfox's quiet surface, milky
private const val PANEL_FROST_LIGHT = 0xCCF2E9E1.toInt()
private const val PANEL_SOLID_LIGHT = 0xF2F2E9E1.toInt()

class IMEService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private fun setupView(): ComposeKeyboardView {
        val app = application as ThumbkeyApplication
        val settingsRepo = app.appSettingsRepository
        val clipboardRepo = app.clipboardRepository

        val appSettings = settingsRepo.appSettings.value
        val layoutIndex = appSettings?.keyboardLayout
        if (layoutIndex != null) {
            currentKeyboardDefinition =
                resolveKeyboardDefinition(KeyboardLayout.entries[layoutIndex], appSettings.keyModifications)
        }

        val view = ComposeKeyboardView(this, settingsRepo, clipboardRepo)
        val density = resources.displayMetrics.density
        val inset = (PANEL_INSET_DP * density).toInt()
        // the window hangs one corner radius below the screen (dressPanel), so the keys get it back
        view.setPadding(inset, inset, inset, inset + (PANEL_RADIUS_DP * density).toInt())
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }
        view.let {
            view.setViewTreeLifecycleOwner(this)
            view.setViewTreeViewModelStoreOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
        }
        return view
    }

    var currentKeyboardDefinition: KeyboardDefinition? = null

    // While set, the keys type into this field of the keyboard's own, like sketch mode's
    // search, instead of the app's
    private var inputRedirect: InputConnection? = null

    fun setInputRedirect(connection: InputConnection?) {
        inputRedirect = connection
    }

    // The framework's connection is null while no text field is bound, and the framework itself
    // calls this. Kotlin would check a platform value returned as non-null and throw, so pass it
    // through unchecked, as the Java method does. Nullable would break every caller.
    override fun getCurrentInputConnection(): InputConnection = inputRedirect ?: unchecked(super.getCurrentInputConnection())

    /** The app's text field, even while the keys type into the keyboard's own field. */
    val appInputConnection: InputConnection get() = super.getCurrentInputConnection()

    // Sketch mode's drawing, kept while the keyboard is closed and opened again
    val sketchState = SketchState()
    private var clipboardManager: ThumbKeyClipboardManager? = null

    /**
     * This is called every time the keyboard is brought up.
     * You can't use onCreate, because that can't pick up new numeric inputs
     */
    override fun onStartInput(
        attribute: EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInput(attribute, restarting)
        setInputRedirect(null)
        // A new keyboard opens on the letters: close sketch mode's search and lists, but keep
        // its drawing
        sketchState.searching = false
        sketchState.list = null
        val view = this.setupView()
        this.setInputView(view)
        dressPanel()
    }

    // Lifecycle Methods
    private var lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)

    private fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)

    override val lifecycle = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Initialize clipboard manager
        val app = application as ThumbkeyApplication
        clipboardManager = ThumbKeyClipboardManager(this, app.clipboardRepository)
        clipboardManager?.startListening()
        clipboardManager?.clearExpired()
    }

    // thevoid: the keys sit on a frosted panel. the IME window is exactly the keyboard's rectangle and
    // already translucent, so its background is the panel: a rounded dark tint, and the screen behind
    // it blurred. Android rounds the blur to the background drawable's outline, so the drawable goes
    // on first. when blur is off (battery saver, an old Android) the tint goes nearly solid instead.
    private val panelBackground = GradientDrawable()
    private var blurListener: Consumer<Boolean>? = null

    private fun night() =
        resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun panel(blur: Boolean) =
        if (night()) {
            if (blur) PANEL_FROST else PANEL_SOLID
        } else {
            if (blur) PANEL_FROST_LIGHT else PANEL_SOLID_LIGHT
        }

    // thevoid's `light` flips android's night mode; the panel follows it like the keys do
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        dressPanel()
    }

    private fun dressPanel() {
        val w = window?.window ?: return
        val density = resources.displayMetrics.density
        panelBackground.cornerRadius = PANEL_RADIUS_DP * density
        panelBackground.setColor(panel(false))
        w.setBackgroundDrawable(panelBackground)
        // only the top corners are round; the bottom edge is flush with the screen's. the blur only
        // rounds all four corners alike (a top-only outline makes it square, and the unrounded blur
        // then shows past the top corners), so the window hangs one radius below the screen instead,
        // and its rounded bottom corners are off it.
        w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        w.attributes = w.attributes.apply { y = -(PANEL_RADIUS_DP * density).toInt() }
        // API 35+: an IME that hides its caption bar takes the system's back and switcher buttons
        // off its bottom edge, where they'd hang over the space bar and return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            w.insetsController?.hide(WindowInsets.Type.captionBar())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wm = getSystemService(WindowManager::class.java)
            val frost = { blur: Boolean ->
                panelBackground.setColor(panel(blur))
                w.setBackgroundBlurRadius(if (blur) (PANEL_BLUR_DP * density).toInt() else 0)
            }
            frost(wm.isCrossWindowBlurEnabled)
            if (blurListener == null) {
                val listener = Consumer<Boolean> { frost(it) }
                wm.addCrossWindowBlurEnabledListener(listener)
                blurListener = listener
            }
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurListener?.let { getSystemService(WindowManager::class.java).removeCrossWindowBlurEnabledListener(it) }
        }
        clipboardManager?.stopListening()
        clipboardManager = null
        super.onDestroy()
        handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    // Cursor update Methods
    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)

        cursorMoved =
            if (ignoreCursorMove) {
                ignoreCursorMove = false
                false
            } else {
                Log.d(TAG, "cursor moved")
                cursorAnchorInfo.selectionStart != selectionStart ||
                    cursorAnchorInfo.selectionEnd != selectionEnd
            }

        currentKeyboardDefinition?.settings?.textProcessor?.handleCursorUpdate(
            this,
            selectionStart,
            selectionEnd,
            cursorAnchorInfo.selectionStart,
            cursorAnchorInfo.selectionEnd,
        )

        selectionStart = cursorAnchorInfo.selectionStart
        selectionEnd = cursorAnchorInfo.selectionEnd
    }

    override fun onEvaluateInputViewShown(): Boolean {
        val settingsRepo = (application as ThumbkeyApplication).appSettingsRepository
        val settings = settingsRepo.appSettings.getValue()
        val showOnScreenKeyboard =
            (settings?.showOnScreenKeyboard ?: DEFAULT_SHOW_ON_SCREEN_KEYBOARD).toBool()

        // Always call super implementation because Android docs says:
        // "If you override this method you must call through to the superclass implementation."
        // https://developer.android.com/reference/android/inputmethodservice/InputMethodService#onEvaluateInputViewShown()
        return super.onEvaluateInputViewShown() ||
            showOnScreenKeyboard
    }

    // Disable the fullscreen text editor if set by the user
    override fun onUpdateExtractingVisibility(ei: EditorInfo) {
        val settingsRepo = (application as ThumbkeyApplication).appSettingsRepository
        val settings = settingsRepo.appSettings.getValue()
        if ((settings?.disableFullscreenEditor ?: DEFAULT_DISABLE_FULLSCREEN_EDITOR).toBool()) {
            ei.imeOptions =
                ei.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        }
        super.onUpdateExtractingVisibility(ei)
    }

    fun didCursorMove(): Boolean = cursorMoved

    fun ignoreNextCursorMove() {
        // This gets reset on the next call to `onUpdateCursorAnchorInfo`
        ignoreCursorMove = true
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // Must run before super, which commits any composing text as it stands.
        // Running after it would let the processor write its pending text a second time.
        currentKeyboardDefinition?.settings?.textProcessor?.handleFinishInput(this)
        super.onFinishInputView(finishingInput)
    }

    private var ignoreCursorMove: Boolean = false
    private var cursorMoved: Boolean = false
    private var selectionStart: Int = 0
    private var selectionEnd: Int = 0

    // ViewModelStore Methods
    override val viewModelStore = ViewModelStore()

    // SaveStateRegistry Methods
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

    fun clipboardUsePrivate(): Boolean {
        val settingsRepo = (application as ThumbkeyApplication).appSettingsRepository
        val settings = settingsRepo.appSettings.getValue()
        val clipboardHistoryEnabled = (settings?.clipboardHistoryEnabled ?: DEFAULT_CLIPBOARD_HISTORY_ENABLED).toBool()
        val usePrivateClipboard = (settings?.usePrivateClipboard ?: DEFAULT_USE_PRIVATE_CLIPBOARD).toBool()
        return clipboardHistoryEnabled && usePrivateClipboard
    }

    fun switchImeMode(): ImeSwitchMode {
        val settingsRepo = (application as ThumbkeyApplication).appSettingsRepository
        val settings = settingsRepo.appSettings.getValue()
        return ImeSwitchMode.entries[settings?.switchImeMode ?: DEFAULT_SWITCH_IME_MODE]
    }

    fun clipboardAddPrivateClip(text: String): Unit? = clipboardManager?.addPrivateClip(text)

    fun clipboardWasLastCopyDoneViaSystem(): Boolean = clipboardManager?.wasLastCopyOperationDoneViaSystem() ?: true

    fun clipboardGetLastClip(): String? = clipboardManager?.getLastClip()
}

// Returns `value` without Kotlin's null check, for a platform value that may be null
@Suppress("UNCHECKED_CAST")
private fun <T> unchecked(value: T?): T = value as T
