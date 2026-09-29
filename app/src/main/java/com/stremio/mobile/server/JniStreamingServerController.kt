package com.stremio.mobile.server

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.stremio.mobile.BuildConfig
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class JniStreamingServerController(
    private val context: Context,
    private val useForegroundService: () -> Boolean
) : StreamingServerController {
    private val mutableState = MutableStateFlow<StreamingServerState>(StreamingServerState.Stopped)
    private val startMutex = Mutex()
    override val state: StateFlow<StreamingServerState> = mutableState

    companion object {
        private const val TAG = "JniStreamingServer"

        init {
            System.loadLibrary("stream_server")
            if (BuildConfig.DEBUG) Timber.tag(TAG).i("Native library loaded")
        }

        @JvmStatic
        private external fun startServerNative(context: Context, configDir: String, cacheDir: String, port: Int): String?

        @JvmStatic
        private external fun stopServerNative()

        @JvmStatic
        private external fun getServerUrlNative(): String?
    }

    override suspend fun start() {
        startMutex.withLock {
            val current = mutableState.value
            if (current is StreamingServerState.Ready) {
                if (waitForServerReady(current.baseUrl, timeoutMs = 750)) {
                    return
                }
                Timber.tag(TAG).w("Previous Ready state failed the /settings reachability probe; restarting native server")
                dumpServerLogSummary("ready-state-unreachable")
                stopNativeServer()
            }

            mutableState.value = StreamingServerState.Starting
            val useForeground = useForegroundService()
            val startupStartedAtMs = SystemClock.elapsedRealtime()
            val serviceIntent = Intent(context, ServerService::class.java).apply {
                putExtra(ServerService.EXTRA_FOREGROUND, useForeground)
            }
            try {
                val url = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && useForeground) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }

                    val configDir = File(context.filesDir, "stream-server").absolutePath
                    val cacheDir = File(context.cacheDir, "stream-server").absolutePath
                    Timber.tag(TAG).i("Invoking native server start")
                    val nativeStartedAtMs = SystemClock.elapsedRealtime()
                    startServerNative(context.applicationContext, configDir, cacheDir, 11470)
                        .also {
                            Timber.tag(TAG).i("Native server start call returned in %d ms", SystemClock.elapsedRealtime() - nativeStartedAtMs)
                        }
                }

                if (url != null && waitForServerReady(url)) {
                    Timber.tag(TAG).i("Streaming server ready; /settings returned 2xx in %d ms", SystemClock.elapsedRealtime() - startupStartedAtMs)
                    mutableState.value = StreamingServerState.Ready(url)
                } else {
                    val failure = if (url == null) {
                        StreamingServerState.Failed(
                            StreamingServerFailureCategory.NativeStartReturnedNull,
                            "Native server start returned no endpoint.",
                        )
                    } else {
                        StreamingServerState.Failed(
                            StreamingServerFailureCategory.SettingsEndpointUnreachable,
                            "Native server started, but the local /settings endpoint did not respond successfully.",
                        )
                    }
                    Timber.tag(TAG).e("Streaming server startup failed: %s after %d ms", failure.category.name, SystemClock.elapsedRealtime() - startupStartedAtMs)
                    dumpServerLogSummary(failure.category.name)
                    stopNativeServer()
                    mutableState.value = failure
                }
            } catch (e: LinkageError) {
                Timber.tag(TAG).e("Native function unavailable during server startup after %d ms", SystemClock.elapsedRealtime() - startupStartedAtMs)
                dumpServerLogSummary("native-function-unavailable")
                mutableState.value = StreamingServerState.Failed(
                    StreamingServerFailureCategory.NativeFunctionUnavailable,
                    "The stream-server native start function is unavailable.",
                )
                stopNativeServer()
            } catch (e: Exception) {
                Timber.tag(TAG).e("Native server startup exception category=%s after %d ms", e.javaClass.simpleName, SystemClock.elapsedRealtime() - startupStartedAtMs)
                dumpServerLogSummary("native-startup-exception")
                mutableState.value = StreamingServerState.Failed(
                    StreamingServerFailureCategory.NativeStartupException,
                    "Native server startup failed (${e.javaClass.simpleName}).",
                )
                stopNativeServer()
            }
        }
    }

    override suspend fun stop() {
        stopNativeServer()
        mutableState.value = StreamingServerState.Stopped
    }

    private suspend fun stopNativeServer() {
        withContext(Dispatchers.IO) {
            runCatching { stopServerNative() }
            try {
                context.stopService(Intent(context, ServerService::class.java))
            } catch (ignored: Exception) {}
        }
    }

    private suspend fun waitForServerReady(baseUrl: String, timeoutMs: Long = 5_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isServerReachable(baseUrl)) {
                return true
            }
            delay(150)
        }
        return false
    }

    private suspend fun isServerReachable(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL("$baseUrl/settings").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 500
            connection.readTimeout = 500
            connection.setRequestProperty("Accept", "application/json")
            try {
                    connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }

    private fun dumpServerLogSummary(reason: String) {
        val logDir = File(context.filesDir, "stream-server/logs")
        if (!logDir.exists()) {
            Timber.tag(TAG).e("Server log summary: reason=%s files=0", reason)
            return
        }

        val candidates = logDir.listFiles()
            ?.filter { it.isFile && (it.name == "server_current.log" || it.extension == "log" || it.extension == "jsonl") }
            .orEmpty()
        Timber.tag(TAG).e(
            "Server log summary: reason=%s files=%d totalBytes=%d; contents omitted",
            reason,
            candidates.size,
            candidates.sumOf { it.length() },
        )
    }
}
