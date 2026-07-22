const fs = require('fs');
const path = require('path');

function replaceRequired(source, before, after, label) {
  if (!source.includes(before)) {
    throw new Error(`Expected ${label} text was not found`);
  }
  return source.replace(before, after);
}

const root = path.resolve(__dirname, '..');
const javaDir = path.join(root, 'android', 'app', 'src', 'main', 'java', 'com', 'clipcascade');
const testDir = path.join(root, 'android', 'app', 'src', 'test', 'java', 'com', 'clipcascade');
const manifestPath = path.join(root, 'android', 'app', 'src', 'main', 'AndroidManifest.xml');

const accessibilitySource = String.raw`package com.clipcascade

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Accessibility detects candidates; native sticky service proves clipboard mutation. */
class ClipboardAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "ClipboardAccessibility"
    }

    private var acquisitionService: ClipboardAcquisitionService? = null
    private var acquisitionBound = false

    private val acquisitionConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            acquisitionService = (binder as? ClipboardAcquisitionService.LocalBinder)?.getService()
            acquisitionBound = acquisitionService != null
            acquisitionService?.requestClipboardObservation(
                trigger = "accessibility_bound_prime",
                sourcePackage = "",
                strongCopySignal = false,
            )
            RelayHealthStore.record(
                applicationContext,
                category = "copy_runtime",
                trigger = "native_service_bound",
                path = "accessibility_bind",
                result = if (acquisitionBound) "ready" else "interrupted",
            )
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            acquisitionService = null
            acquisitionBound = false
            startAndBindAcquisitionService()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED or
                AccessibilityEvent.TYPE_ANNOUNCEMENT or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        startAndBindAcquisitionService()
        RelayHealthStore.record(
            applicationContext,
            category = "clipboard",
            trigger = "service_connected",
            path = "accessibility",
            result = "ready",
        )
        ClipboardRelayDispatcher.schedule(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val current = event ?: return
        if (!RelaySettingsStore.clipboardEnabled(this)) return
        val sourcePackage = current.packageName?.toString().orEmpty()
        if (sourcePackage == packageName) return

        when (current.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                RelayHealthStore.record(
                    applicationContext,
                    category = "copy_detection",
                    trigger = "selection_probe",
                    path = "accessibility_mutation_candidate",
                    result = "requested",
                )
                requestNativeObservation(current, "selection_probe", strong = false)
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED,
            -> {
                val semanticCopy = current.action == AccessibilityNodeInfo.ACTION_COPY
                val localizedCopy = isSystemLocalizedCopyCue(current)
                requestNativeObservation(
                    current,
                    when {
                        semanticCopy -> "semantic_copy_action"
                        localizedCopy -> "system_copy_label"
                        else -> "click_probe"
                    },
                    strong = semanticCopy || localizedCopy,
                )
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED ->
                requestNativeObservation(current, "notification_probe", strong = false)

            AccessibilityEvent.TYPE_ANNOUNCEMENT ->
                requestNativeObservation(current, "announcement_probe", strong = false)
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        val current = event ?: return false
        if (
            RelaySettingsStore.clipboardEnabled(this) &&
            current.action == KeyEvent.ACTION_UP &&
            current.keyCode == KeyEvent.KEYCODE_C &&
            current.isCtrlPressed
        ) {
            requestNativeObservation(null, "ctrl_c", strong = true)
        }
        return false
    }

    private fun requestNativeObservation(
        event: AccessibilityEvent?,
        trigger: String,
        strong: Boolean,
    ) {
        val sourcePackage = event?.packageName?.toString().orEmpty()
        val service = acquisitionService
        if (service != null) {
            service.requestClipboardObservation(trigger, sourcePackage, strong)
            return
        }
        try {
            ClipboardAcquisitionService.request(this, trigger, sourcePackage, strong)
            startAndBindAcquisitionService()
        } catch (error: Exception) {
            Log.w(TAG, "Unable to request native clipboard observation", error)
            RelayHealthStore.record(
                applicationContext,
                category = "copy_runtime",
                trigger = trigger,
                path = "native_service_start",
                result = "blocked",
            )
        }
    }

    private fun isSystemLocalizedCopyCue(event: AccessibilityEvent): Boolean {
        if (
            event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED
        ) {
            return false
        }
        val node = event.source
        val candidates = linkedSetOf<CharSequence?>().apply {
            add(event.contentDescription)
            addAll(event.text)
            addNodeCopyCueCandidates(node, this)
            val parent = node?.parent
            addNodeCopyCueCandidates(parent, this)
            if (node != null) {
                for (index in 0 until node.childCount.coerceAtMost(12)) {
                    addNodeCopyCueCandidates(node.getChild(index), this)
                }
            }
            if (parent != null) {
                for (index in 0 until parent.childCount.coerceAtMost(12)) {
                    addNodeCopyCueCandidates(parent.getChild(index), this)
                }
            }
        }
        return SystemCopyCuePolicy.matchesAny(
            candidates = candidates,
            copyLabel = getText(android.R.string.copy),
            copyUrlLabel = getText(android.R.string.copyUrl),
        )
    }

    private fun addNodeCopyCueCandidates(
        node: AccessibilityNodeInfo?,
        target: MutableSet<CharSequence?>,
    ) {
        if (node == null) return
        target.add(node.text)
        target.add(node.contentDescription)
        node.actionList.forEach { action -> target.add(action.label) }
    }

    private fun startAndBindAcquisitionService() {
        if (!RelaySettingsStore.clipboardEnabled(this)) return
        val intent = Intent(this, ClipboardAcquisitionService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            if (!acquisitionBound) {
                bindService(intent, acquisitionConnection, Context.BIND_AUTO_CREATE)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to start or bind clipboard acquisition service", error)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (acquisitionBound) {
            try {
                unbindService(acquisitionConnection)
            } catch (_: Exception) {
            }
        }
        acquisitionBound = false
        acquisitionService = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        RelayHealthStore.record(
            applicationContext,
            category = "clipboard",
            trigger = "service_interrupted",
            path = "accessibility",
            result = "interrupted",
        )
    }
}
`;
fs.writeFileSync(path.join(javaDir, 'ClipboardAccessibilityService.kt'), accessibilitySource, 'utf8');


console.log('Prepared alpha.22 broad Accessibility probes bound to native acquisition service.');
