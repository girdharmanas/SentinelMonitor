package com.sentinel.monitor.service

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.sentinel.monitor.data.model.SensorBlackboxEvent
import com.sentinel.monitor.data.preferences.SentinelPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Event-driven Privacy Blackbox listener for Camera and Microphone hardware usage.
 * Zero polling: relies exclusively on OS CameraManager & AudioManager callbacks.
 */
object CameraMicBlackboxTracker {

    private val registered = AtomicBoolean(false)
    private val trackerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeCameras = ConcurrentHashMap<String, Long>()

    @Volatile
    private var registeredAtElapsedMs: Long = 0L

    @Volatile
    private var activeMicStartElapsedMs: Long = 0L

    fun register(context: Context) {
        if (!registered.compareAndSet(false, true)) return
        val appCtx = context.applicationContext
        registeredAtElapsedMs = SystemClock.elapsedRealtime()
        val mainHandler = Handler(Looper.getMainLooper())

        runCatching {
            val cameraManager = appCtx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cameraManager?.registerAvailabilityCallback(
                object : CameraManager.AvailabilityCallback() {
                    override fun onCameraUnavailable(cameraId: String) {
                        activeCameras.putIfAbsent(cameraId, SystemClock.elapsedRealtime())
                    }

                    override fun onCameraAvailable(cameraId: String) {
                        val startedAt = activeCameras.remove(cameraId) ?: return
                        val now = SystemClock.elapsedRealtime()
                        // Ignore instantaneous initial enumeration callbacks on app startup
                        if (startedAt - registeredAtElapsedMs < 800L && now - startedAt < 400L) return
                        val duration = (now - startedAt).coerceAtLeast(100L)
                        recordEvent(appCtx, "CAMERA", duration)
                    }
                },
                mainHandler
            )
        }

        runCatching {
            val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.registerAudioRecordingCallback(
                object : AudioManager.AudioRecordingCallback() {
                    override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                        val now = SystemClock.elapsedRealtime()
                        val isMicActive = !configs.isNullOrEmpty()
                        if (isMicActive) {
                            if (activeMicStartElapsedMs == 0L) {
                                activeMicStartElapsedMs = now
                            }
                        } else {
                            val startedAt = activeMicStartElapsedMs
                            if (startedAt > 0L) {
                                activeMicStartElapsedMs = 0L
                                val duration = (now - startedAt).coerceAtLeast(100L)
                                recordEvent(appCtx, "MIC", duration)
                            }
                        }
                    }
                },
                mainHandler
            )
        }
    }

    private fun recordEvent(context: Context, sensorType: String, durationMillis: Long) {
        val ev = SensorBlackboxEvent(
            timestampMs = System.currentTimeMillis(),
            sensorType = sensorType,
            durationMillis = durationMillis
        )
        trackerScope.launch {
            runCatching {
                SentinelPreferencesRepository.appendSensorBlackboxEvent(context, ev)
            }
        }
    }
}
