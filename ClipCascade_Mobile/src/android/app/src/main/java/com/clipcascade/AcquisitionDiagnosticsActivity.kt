package com.clipcascade

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.clipcascade.acquisition.AcquisitionDiagnosticsFormatter

/**
 * Payload-free acquisition diagnostics that remain independent from the large
 * React Native application screen.
 *
 * The activity intentionally exposes only stable health fields and counters.
 * Clipboard contents, credentials, and server URLs never enter this surface.
 */
class AcquisitionDiagnosticsActivity : AppCompatActivity() {
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.acquisition_diagnostics_title)
        setContentView(createContentView())
    }

    override fun onResume() {
        super.onResume()
        refreshSnapshot()
    }

    private fun createContentView(): ScrollView {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }

        container.addView(
            TextView(this).apply {
                text = getString(R.string.acquisition_diagnostics_title)
                textSize = 24f
                setTypeface(typeface, Typeface.BOLD)
            },
            wrapContentParams(),
        )

        container.addView(
            TextView(this).apply {
                text = getString(R.string.acquisition_diagnostics_description)
                textSize = 15f
                setPadding(0, dp(10), 0, dp(8))
            },
            wrapContentParams(),
        )

        statusView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0x11000000)
            text = getString(R.string.acquisition_diagnostics_loading)
        }
        container.addView(statusView, matchWidthParams(topMarginDp = 12))

        container.addView(
            Button(this).apply {
                text = getString(R.string.acquisition_diagnostics_refresh)
                setOnClickListener { refreshSnapshot() }
            },
            matchWidthParams(topMarginDp = 16),
        )

        container.addView(
            Button(this).apply {
                text = getString(R.string.acquisition_diagnostics_overlay_settings)
                setOnClickListener { openOverlaySettings() }
            },
            matchWidthParams(topMarginDp = 8),
        )

        container.addView(
            Button(this).apply {
                text = getString(R.string.acquisition_diagnostics_open_app)
                setOnClickListener { openMainApplication() }
            },
            matchWidthParams(topMarginDp = 8),
        )

        container.addView(
            Button(this).apply {
                text = getString(R.string.acquisition_diagnostics_close)
                setOnClickListener { finish() }
            },
            matchWidthParams(topMarginDp = 8),
        )

        return ScrollView(this).apply {
            isFillViewport = true
            addView(
                container,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun refreshSnapshot() {
        val snapshot = runCatching {
            val application = applicationContext as MainApplication
            val reactContext = application
                .reactNativeHost
                .reactInstanceManager
                .currentReactContext
                ?: return@runCatching null
            reactContext
                .getNativeModule(ClipboardListenerModule::class.java)
                ?.snapshotForDiagnostics()
        }.getOrNull()

        statusView.text = if (snapshot == null) {
            getString(R.string.acquisition_diagnostics_module_unavailable)
        } else {
            AcquisitionDiagnosticsFormatter.format(
                snapshot = snapshot,
                nowMonotonicMs = SystemClock.elapsedRealtime(),
            )
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(intent) }
    }

    private fun openMainApplication() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun wrapContentParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )

    private fun matchWidthParams(topMarginDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(topMarginDp)
            gravity = Gravity.CENTER_HORIZONTAL
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
