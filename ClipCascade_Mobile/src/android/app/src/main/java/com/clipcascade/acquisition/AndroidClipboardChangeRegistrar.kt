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
        val androidListener = listeners.remove(listener) ?: return
        clipboardManager.removePrimaryClipChangedListener(androidListener)
    }
}
