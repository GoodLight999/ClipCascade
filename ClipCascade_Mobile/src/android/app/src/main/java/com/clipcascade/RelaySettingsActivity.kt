package com.clipcascade

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class RelaySettingsActivity : AppCompatActivity() {
    private lateinit var accessibilityStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var selectedAppsSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "ClipCascade 設定"

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        content.addView(titleText("バックグラウンド共有"))
        content.addView(bodyText("ADBを使わず、ユーザー補助と通知アクセスでコピー内容をPCへ送ります。"))

        content.addView(sectionTitle("通常のクリップボード共有"))
        val clipboardSwitch = Switch(this).apply {
            text = "コピーしたテキストを自動送信"
            isChecked = RelaySettingsStore.clipboardEnabled(this@RelaySettingsActivity)
            setOnCheckedChangeListener { _, enabled ->
                RelaySettingsStore.setClipboardEnabled(this@RelaySettingsActivity, enabled)
            }
        }
        content.addView(clipboardSwitch)

        accessibilityStatus = bodyText("")
        content.addView(accessibilityStatus)
        content.addView(Button(this).apply {
            text = "ユーザー補助設定を開く"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
        content.addView(
            bodyText(
                "有効にすると、コピー操作・選択テキスト・コピー完了通知を検出します。" +
                    "クリップボードを直接読めないアプリでは、選択テキストを代替利用します。",
            ),
        )

        content.addView(sectionTitle("認証コード通知"))
        val codeSwitch = Switch(this).apply {
            text = "通知から認証コードだけを抽出して送信"
            isChecked = RelaySettingsStore.codeRelayEnabled(this@RelaySettingsActivity)
            setOnCheckedChangeListener { _, enabled ->
                RelaySettingsStore.setCodeRelayEnabled(this@RelaySettingsActivity, enabled)
            }
        }
        content.addView(codeSwitch)

        notificationStatus = bodyText("")
        content.addView(notificationStatus)
        content.addView(Button(this).apply {
            text = "通知へのアクセス設定を開く"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        })
        content.addView(
            bodyText(
                "通知本文は端末内だけで解析し、認証コードらしい短い値だけを既存の暗号化同期へ渡します。",
            ),
        )

        content.addView(sectionTitle("認証コードを読むアプリ"))
        selectedAppsSummary = bodyText("")
        content.addView(selectedAppsSummary)
        content.addView(Button(this).apply {
            text = "対象アプリを選ぶ"
            setOnClickListener { showAppPicker() }
        })
        content.addView(Button(this).apply {
            text = "対象をすべてのアプリに戻す"
            setOnClickListener {
                RelaySettingsStore.setSelectedApps(this@RelaySettingsActivity, emptySet())
                updateStatus()
            }
        })

        content.addView(sectionTitle("動作確認"))
        content.addView(Button(this).apply {
            text = "テスト文字列をPCへ送る"
            setOnClickListener {
                val now = System.currentTimeMillis()
                ClipboardRelayStore.enqueue(
                    applicationContext,
                    ClipboardRelayStore.Item(
                        id = "settings-test:$now",
                        text = "ClipCascade clipboard relay test",
                        sourcePackage = packageName,
                        createdAt = now,
                    ),
                )
                ClipboardRelayDispatcher.schedule(applicationContext)
            }
        })

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        accessibilityStatus.text = if (isAccessibilityEnabled()) {
            "状態: ユーザー補助サービスは有効です"
        } else {
            "状態: 無効です。自動クリップボード共有には有効化が必要です"
        }

        notificationStatus.text = if (
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        ) {
            "状態: 通知アクセスは有効です"
        } else {
            "状態: 無効です。SMS・メール等の認証コード取得には許可が必要です"
        }

        val selected = RelaySettingsStore.selectedApps(this)
        selectedAppsSummary.text = if (selected.isEmpty()) {
            "対象: すべてのアプリ（認証コード文脈がある通知だけ）"
        } else {
            "対象: ${selected.size}個の選択済みアプリ"
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, ClipboardAccessibilityService::class.java)
            .flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    @Suppress("DEPRECATION")
    private fun showAppPicker() {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries = packageManager.queryIntentActivities(launchIntent, 0)
            .map {
                Triple(
                    it.activityInfo.packageName,
                    it.loadLabel(packageManager).toString(),
                    it.activityInfo.applicationInfo,
                )
            }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }

        val packages = entries.map { it.first }
        val labels = entries.map { "${it.second}\n${it.first}" }.toTypedArray()
        val selected = RelaySettingsStore.selectedApps(this).toMutableSet()
        val checked = BooleanArray(packages.size) { selected.contains(packages[it]) }

        AlertDialog.Builder(this)
            .setTitle("認証コード通知の対象アプリ")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                if (isChecked) selected += packages[which] else selected -= packages[which]
            }
            .setPositiveButton("保存") { _, _ ->
                RelaySettingsStore.setSelectedApps(this, selected)
                updateStatus()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun titleText(value: String) = TextView(this).apply {
        text = value
        textSize = 24f
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, 0, 0, dp(12))
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 19f
        setPadding(0, dp(24), 0, dp(8))
    }

    private fun bodyText(value: String) = TextView(this).apply {
        text = value
        textSize = 15f
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
