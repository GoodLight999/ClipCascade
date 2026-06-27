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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import java.text.DateFormat
import java.util.Date

class RelaySettingsActivity : AppCompatActivity() {
    private lateinit var accessibilityStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var selectedAppsSummary: TextView
    private lateinit var queueStatus: TextView
    private lateinit var healthStatus: TextView

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
                updateStatus()
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
                updateStatus()
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

        content.addView(sectionTitle("保留キュー"))
        queueStatus = bodyText("")
        content.addView(queueStatus)
        content.addView(
            bodyText(
                "未送信データはアプリ専用領域に一時保存されます。ここでは内容を表示せず件数だけを表示します。",
            ),
        )
        content.addView(Button(this).apply {
            text = "保留中のデータをすべて消去"
            setOnClickListener {
                ClipboardRelayStore.clear(applicationContext)
                OtpRelayStore.clear(applicationContext)
                updateStatus()
                Toast.makeText(
                    this@RelaySettingsActivity,
                    "保留キューを消去しました",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        })

        content.addView(sectionTitle("最近の診断"))
        healthStatus = bodyText("")
        content.addView(healthStatus)
        content.addView(
            bodyText(
                "コピー内容・認証コード・通知本文・利用アプリ名は表示または診断保存しません。",
            ),
        )
        content.addView(Button(this).apply {
            text = "診断履歴を消去"
            setOnClickListener {
                RelayHealthStore.clear(applicationContext)
                updateStatus()
                Toast.makeText(
                    this@RelaySettingsActivity,
                    "診断履歴を消去しました",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        })

        content.addView(sectionTitle("動作確認"))
        content.addView(Button(this).apply {
            text = "テスト文字列をPCへ送る"
            setOnClickListener {
                if (!RelaySettingsStore.clipboardEnabled(this@RelaySettingsActivity)) {
                    AlertDialog.Builder(this@RelaySettingsActivity)
                        .setTitle("クリップボード共有がOFFです")
                        .setMessage("テスト送信の前に「コピーしたテキストを自動送信」をONにしてください。")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setOnClickListener
                }

                val now = System.currentTimeMillis()
                val queued = ClipboardRelayStore.enqueue(
                    applicationContext,
                    ClipboardRelayStore.Item(
                        id = "settings-test:$now",
                        text = "ClipCascade clipboard relay test",
                        sourcePackage = packageName,
                        createdAt = now,
                    ),
                )
                if (queued) {
                    ClipboardRelayDispatcher.schedule(applicationContext)
                    RelayHealthStore.record(
                        applicationContext,
                        category = "clipboard",
                        trigger = "settings_test",
                        path = "manual_test",
                        result = "queued",
                    )
                    Toast.makeText(
                        this@RelaySettingsActivity,
                        "テスト文字列を送信キューへ追加しました",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(
                        this@RelaySettingsActivity,
                        "同じテスト文字列がすでに保留中です",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                updateStatus()
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

        queueStatus.text =
            "通常コピー: ${ClipboardRelayStore.count(this)}件 / " +
                "認証コード: ${OtpRelayStore.pendingCount(this)}件"

        healthStatus.text = listOf(
            formatHealth("通常コピー", RelayHealthStore.read(this, "clipboard")),
            formatHealth("認証コード", RelayHealthStore.read(this, "verification")),
            formatHealth("自動復旧", RelayHealthStore.read(this, "recovery")),
        ).joinToString("\n")
    }

    private fun formatHealth(label: String, snapshot: RelayHealthStore.Snapshot?): String {
        if (snapshot == null) return "$label: 記録なし"
        val time = DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.MEDIUM,
        ).format(Date(snapshot.timestamp))
        return "$label: $time / ${healthLabel(snapshot.trigger)} / " +
            "${healthLabel(snapshot.path)} / ${healthLabel(snapshot.result)}"
    }

    private fun healthLabel(value: String): String = when (value) {
        "service_connected" -> "サービス接続"
        "service_interrupted" -> "サービス中断"
        "selection" -> "文字選択"
        "click" -> "コピーボタン"
        "announcement" -> "コピー通知"
        "copy_notice" -> "コピー完了通知"
        "ctrl_c" -> "Ctrl+C"
        "settings_test" -> "設定テスト"
        "listener_connected" -> "通知リスナー接続"
        "listener_disconnected" -> "通知リスナー切断"
        "context_match" -> "コード文脈一致"
        "accessibility" -> "ユーザー補助"
        "accessibility_selection" -> "選択文字列"
        "clipboard_manager" -> "クリップボード読取"
        "selected_text_fallback" -> "選択文字列フォールバック"
        "clipboard_denied_no_fallback" -> "読取拒否・代替なし"
        "no_text_available" -> "文字列未取得"
        "notification_access" -> "通知アクセス"
        "local_extractor" -> "端末内抽出"
        "coordinator" -> "復旧調整"
        "active_react_context" -> "実行中アプリ"
        "headless_js" -> "バックグラウンド処理"
        "manual_test" -> "手動テスト"
        "ready" -> "待機中"
        "remembered" -> "選択を記憶"
        "queued" -> "キュー追加"
        "deduplicated" -> "重複抑止"
        "retrying" -> "再試行中"
        "interrupted" -> "中断"
        "rebind_requested" -> "再接続要求"
        "requested" -> "復旧要求済み"
        "declined" -> "OSが開始を拒否"
        "blocked" -> "バックグラウンド制限"
        "cooldown" -> "重複復旧を抑止"
        "sync_disabled" -> "同期OFF"
        "heartbeat_timeout" -> "応答タイムアウト"
        "network_available_but_offline" -> "ネット復帰後も未接続"
        else -> value.ifBlank { "―" }
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
                Pair(
                    it.activityInfo.packageName,
                    it.loadLabel(packageManager).toString(),
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
