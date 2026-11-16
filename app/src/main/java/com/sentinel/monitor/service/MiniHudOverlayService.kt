package com.sentinel.monitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.sentinel.monitor.MainActivity
import com.sentinel.monitor.R
import com.sentinel.monitor.data.model.TelemetryFormatters
import com.sentinel.monitor.util.SystemTelemetryEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class MiniHudOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "sentinel_minihud_channel"
        private const val NOTIF_ID = 4201
        const val ACTION_STOP_HUD = "com.sentinel.monitor.ACTION_STOP_HUD"

        private val _isRunning = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, MiniHudOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MiniHudOverlayService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollJob: Job? = null
    private var windowManager: WindowManager? = null
    private var rootPillView: View? = null
    private var metricsTextView: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        startHudForeground()
        attachFloatingPill()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_HUD) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startHudForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Sentinel Floating Mini-HUD",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active while the live developer telemetry pill is displayed on screen."
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }

        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MiniHudOverlayService::class.java).apply { action = ACTION_STOP_HUD },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_sentinel)
            .setContentTitle("Sentinel Mini-HUD Active")
            .setContentText("Showing live CPU, RAM, Wattage & Thermal overlay")
            .setContentIntent(openIntent)
            .addAction(0, "Close HUD", stopIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun attachFloatingPill() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val density = resources.displayMetrics.density

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f * density
            setColor(Color.parseColor("#E60B1220"))
            setStroke((1.2f * density).toInt().coerceAtLeast(1), Color.parseColor("#6634D399"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bg
            setPadding(
                (12 * density).toInt(),
                (6 * density).toInt(),
                (10 * density).toInt(),
                (6 * density).toInt()
            )
        }

        val dotView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((7 * density).toInt(), (7 * density).toInt()).apply {
                marginEnd = (7 * density).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#10B981"))
            }
        }

        val labelView = TextView(this).apply {
            setTextColor(Color.parseColor("#F1F5F9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            this.text = "Sampling…"
        }
        metricsTextView = labelView

        val closeBtn = TextView(this).apply {
            this.text = " ✕"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding((6 * density).toInt(), 0, (2 * density).toInt(), 0)
            setOnClickListener { stopSelf() }
        }

        container.addView(dotView)
        container.addView(labelView)
        container.addView(closeBtn)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).toInt()
            y = (48 * density).toInt()
        }

        container.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        if (abs(dx) > 4 || abs(dy) > 4) moved = true
                        params.x = startX + dx
                        params.y = startY + dy
                        runCatching { wm.updateViewLayout(container, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            runCatching {
                                startActivity(
                                    Intent(this@MiniHudOverlayService, MainActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        runCatching {
            wm.addView(container, params)
            rootPillView = container
        }

        pollJob = scope.launch {
            while (isActive) {
                val snap = SystemTelemetryEngine.sampleSnapshot(applicationContext)
                val ramPct = (snap.ramUsageFraction * 100).toInt()
                val watts = TelemetryFormatters.formatWatts(snap.batteryPowerWatts, snap.isCharging)
                val peakMhz = if (snap.peakCpuMhz > 0) "${snap.peakCpuMhz}MHz" else "${snap.cpuCoreCount}C"
                metricsTextView?.text = String.format(
                    Locale.US,
                    "%s • %d%% RAM • %s • %.1f°C • %dHz",
                    peakMhz,
                    ramPct,
                    watts,
                    snap.batteryTempCelsius,
                    snap.displayRefreshHz
                )
                delay(1500L)
            }
        }
    }

    override fun onDestroy() {
        _isRunning.value = false
        pollJob?.cancel()
        scope.cancel()
        rootPillView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        rootPillView = null
        super.onDestroy()
    }
}
