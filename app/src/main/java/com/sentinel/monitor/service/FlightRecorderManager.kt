package com.sentinel.monitor.service

import android.content.Context
import android.os.SystemClock
import com.sentinel.monitor.data.model.FlightSessionReport
import com.sentinel.monitor.data.model.SystemSnapshot
import com.sentinel.monitor.util.SystemTelemetryEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * On-demand Game / Benchmark "Flight Recorder" Session Profiler.
 * Runs exclusively while explicitly recording (zero idle overhead).
 */
object FlightRecorderManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null
    private var startedAtElapsedMs: Long = 0L
    private val samples = ArrayList<SystemSnapshot>()

    private val _isRecordingFlow = MutableStateFlow(false)
    val isRecordingFlow: StateFlow<Boolean> = _isRecordingFlow.asStateFlow()

    private val _elapsedSecondsFlow = MutableStateFlow(0)
    val elapsedSecondsFlow: StateFlow<Int> = _elapsedSecondsFlow.asStateFlow()

    private val _latestReportFlow = MutableStateFlow<FlightSessionReport?>(null)
    val latestReportFlow: StateFlow<FlightSessionReport?> = _latestReportFlow.asStateFlow()

    @Synchronized
    fun startRecording(context: Context) {
        recordingJob?.cancel()
        samples.clear()
        startedAtElapsedMs = SystemClock.elapsedRealtime()
        _elapsedSecondsFlow.value = 0
        _isRecordingFlow.value = true
        val appCtx = context.applicationContext

        recordingJob = scope.launch {
            while (isActive) {
                val snap = SystemTelemetryEngine.sampleSnapshot(appCtx)
                synchronized(this@FlightRecorderManager) {
                    samples.add(snap)
                }
                _elapsedSecondsFlow.value =
                    ((SystemClock.elapsedRealtime() - startedAtElapsedMs) / 1000L).toInt().coerceAtLeast(1)
                delay(2000L)
            }
        }
    }

    @Synchronized
    fun stopRecording(): FlightSessionReport? {
        recordingJob?.cancel()
        recordingJob = null
        _isRecordingFlow.value = false

        if (samples.isEmpty()) return null
        val durationSec = ((SystemClock.elapsedRealtime() - startedAtElapsedMs) / 1000L)
            .toInt()
            .coerceAtLeast(samples.size * 2)

        val avgCpuMhz = samples.map { it.peakCpuMhz }.average().roundToInt()
        val peakCpuMhz = samples.maxOf { it.peakCpuMhz }
        val avgRamPct = samples.map { it.ramUsageFraction * 100f }.average().roundToInt()
        val peakRamPct = samples.maxOf { (it.ramUsageFraction * 100f).roundToInt() }
        val startTemp = samples.first().batteryTempCelsius
        val peakTemp = samples.maxOf { it.batteryTempCelsius }
        val throttleHits = samples.count { it.thermalSeverityLevel >= 2 }
        val avgWatts = samples.map { it.batteryPowerWatts.toDouble() }.average().toFloat()
        val avgCurrentMa = samples.map { abs(it.batteryCurrentMa).toDouble() }.average().toFloat()
        val estMah = avgCurrentMa * (durationSec / 3600f)

        val report = FlightSessionReport(
            durationSec = durationSec,
            sampleCount = samples.size,
            avgCpuMhz = avgCpuMhz,
            peakCpuMhz = peakCpuMhz,
            avgRamPercent = avgRamPct,
            peakRamPercent = peakRamPct,
            startTempC = startTemp,
            peakTempC = peakTemp,
            thermalThrottleHits = throttleHits,
            avgPowerWatts = avgWatts,
            estimatedMahUsed = estMah
        )
        _latestReportFlow.value = report
        return report
    }
}
