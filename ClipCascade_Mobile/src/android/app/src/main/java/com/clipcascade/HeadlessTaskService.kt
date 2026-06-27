package com.clipcascade

import android.content.Intent
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig

class HeadlessTaskService : HeadlessJsTaskService() {
    override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig? =
        intent?.extras?.let { extras ->
            HeadlessJsTaskConfig(
                "Restart",
                Arguments.fromBundle(extras),
                30_000,
                true,
            )
        }
}
