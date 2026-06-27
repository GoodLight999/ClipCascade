package com.clipcascade

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.util.Log

object NetworkRecoveryMonitor {
    private const val TAG = "NetworkRecoveryMonitor"
    private const val RECOVERY_GRACE_MS = 5_000L

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var registered = false

    @Volatile
    private var networkGeneration = 0L

    @Synchronized
    fun register(context: Context) {
        if (registered) return

        val applicationContext = context.applicationContext
        val manager = applicationContext.getSystemService(
            Context.CONNECTIVITY_SERVICE,
        ) as ConnectivityManager

        try {
            manager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val generation = ++networkGeneration
                        ClipboardRelayDispatcher.schedule(applicationContext)
                        OtpRelayDispatcher.schedule(applicationContext)

                        handler.postDelayed(
                            {
                                if (generation != networkGeneration) return@postDelayed
                                requestRecoveryIfStillOffline(applicationContext)
                            },
                            RECOVERY_GRACE_MS,
                        )
                    }

                    override fun onLost(network: Network) {
                        networkGeneration += 1
                    }
                },
            )
            registered = true
        } catch (error: Exception) {
            Log.w(TAG, "Unable to register network recovery callback", error)
        }
    }

    private fun requestRecoveryIfStillOffline(context: Context) {
        val storage = AsyncStorageBridge(context)
        try {
            if (storage.getValue("wsIsRunning") != "true") return
            val status = storage.getValue("wsStatusMessage").orEmpty()
            if (status.contains("Connected", ignoreCase = true)) return
        } finally {
            storage.disconnect()
        }

        RecoveryCoordinator.request(context, "network_available_but_offline")
    }
}
