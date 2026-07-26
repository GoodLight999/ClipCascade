package com.clipcascade.acquisition

import android.content.ClipboardManager
import java.util.IdentityHashMap

/** Thin Android framework adapter for [OrdinaryClipboardBackend]. */
class AndroidClipboardChangeRegistrar(
    private val clipboardManager: ClipboardManager,
) : ClipboardChangeRegistrar {
    private val listeners = IdentityHashMap<
        () -> Unit,
        ClipboardManager.OnPrimaryClipChangedListener
        >()

    @Synchronized
    override fun register(listener: () -> Unit) {
        if (listeners.containsKey(listener)) {
            // The same logical listener may remain registered after a framework
            // removal failure. Reuse it instead of creating a duplicate.
            return
        }
        val androidListener = ClipboardManager.OnPrimaryClipChangedListener {
            listener()
        }
        clipboardManager.addPrimaryClipChangedListener(androidListener)
        listeners[listener] = androidListener
    }

    @Synchronized
    override fun unregister(listener: () -> Unit) {
        val androidListener = listeners[listener] ?: return
        clipboardManager.removePrimaryClipChangedListener(androidListener)
        listeners.remove(listener)
    }
}
