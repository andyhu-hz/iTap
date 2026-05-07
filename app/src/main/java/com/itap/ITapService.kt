package com.itap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * iTap = twotap `TwoTapService` 的 Hold 逻辑（拷贝于 [TwoTapHoldEngine]）+ AutoTap。
 * AutoTap 与 Hold 的边界：[ItapHoldBridge.onIdleToHolding] 仅在 IDLE→HOLDING 时执行一次；
 * 置为 null 或与 TwoTap 单独运行时等价（对 Hold 零影响）。
 */
class ITapService : AccessibilityService() {

    companion object {
        private const val TAG = "iTap"
        const val ACTION_SHOW_PANEL = "com.itap.action.SHOW_PANEL"

        private const val KEEPALIVE_NOTIFICATION_ID = 71001
        private const val KEEPALIVE_CHANNEL_ID = "itap_accessibility_keepalive"

        private const val POST_CLICK_COOLDOWN_MS = 250L
        private const val AUTO_TAP_MS = 45L
        private const val AUTO_TAP_GAP_MS = 100L
        private const val AUTO_ROUND_SIZE = 250
        private const val AUTO_REST_MS = 5 * 60 * 1000L
    }

    private val holdEngine = TwoTapHoldEngine(this)

    @Volatile private var keepaliveForegroundActive = false

    // ── AutoTap ───────────────────────────────────────────────────────────────

    @Volatile private var clickGestureReady = true
    private val handler = Handler(Looper.getMainLooper())
    private val rnd = Random()

    @Volatile private var autoRunning = false
    private var autoClickCx = 0f
    private var autoClickCy = 0f
    private var clicksThisRound = 0

    private val autoClickLoop = Runnable { runAutoClickStep() }

    fun canAcceptButtonTap(): Boolean = clickGestureReady && holdEngine.isHoldIdle()

    @RequiresApi(Build.VERSION_CODES.N)
    fun onStartAutoClick() {
        if (!clickGestureReady) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val (w, h) = screenSize(wm)
        val density = resources.displayMetrics.density
        autoClickCx = w * 0.32f - 200f
        autoClickCy = h * 0.40f - (20 * density)
        clicksThisRound = 0
        autoRunning = true
        Log.d(TAG, "AutoTap 开始 (${autoClickCx},${autoClickCy})")
        handler.post(autoClickLoop)
    }

    fun onStopAutoClick() {
        if (!autoRunning) return
        autoRunning = false
        handler.removeCallbacks(autoClickLoop)
        clickGestureReady = false
        handler.postDelayed({ clickGestureReady = true }, POST_CLICK_COOLDOWN_MS)
        Log.d(TAG, "AutoTap 停止")
    }

    private fun stopAutoTapFully() {
        autoRunning = false
        handler.removeCallbacks(autoClickLoop)
        clickGestureReady = true
    }

    private fun runAutoClickStep() {
        if (!autoRunning) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val (x, y) = jitteredPoint()
        dispatchTap(x, y) {
            if (!autoRunning) return@dispatchTap
            clicksThisRound++
            if (clicksThisRound >= AUTO_ROUND_SIZE) {
                clicksThisRound = 0
                handler.postDelayed(autoClickLoop, AUTO_REST_MS)
            } else {
                handler.postDelayed(autoClickLoop, AUTO_TAP_GAP_MS)
            }
        }
    }

    private fun jitteredPoint(): Pair<Float, Float> {
        val ang = rnd.nextDouble() * 2 * Math.PI
        val rad = 10.0 + rnd.nextDouble() * 15.0
        return Pair(
            autoClickCx + (cos(ang) * rad).toFloat(),
            autoClickCy + (sin(ang) * rad).toFloat()
        )
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun dispatchTap(x: Float, y: Float, done: () -> Unit) {
        val stroke = GestureDescription.StrokeDescription(
            Path().apply { moveTo(x, y) }, 0L, AUTO_TAP_MS
        )
        val ok = dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription) = done()
                override fun onCancelled(g: GestureDescription) = done()
            },
            handler
        )
        if (!ok) { Log.w(TAG, "AutoTap dispatch 失败"); done() }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = holdEngine.onKeyEvent(event)

    /** 前台服务 + 低优先级常驻通知，减轻 ColorOS 等厂商杀进程导致无障碍断开（无法阻止系统在设置里关无障碍）。 */
    private fun startAccessibilityKeepaliveForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            KEEPALIVE_CHANNEL_ID,
            getString(R.string.notif_keepalive_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, KEEPALIVE_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_keepalive_title))
            .setContentText(getString(R.string.notif_keepalive_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    KEEPALIVE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(KEEPALIVE_NOTIFICATION_ID, notification)
            }
            keepaliveForegroundActive = true
        } catch (e: Exception) {
            Log.w(TAG, "前台保活启动失败（可忽略）", e)
        }
    }

    private fun stopAccessibilityKeepaliveForeground() {
        if (!keepaliveForegroundActive) return
        keepaliveForegroundActive = false
        try {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground", e)
        }
    }

    // ── 面板控制 ──────────────────────────────────────────────────────────────

    private var floatingPanel: FloatingPanelController? = null
    private var showPanelReceiver: BroadcastReceiver? = null

    fun requestExitFromLongPress() {
        Toast.makeText(
            applicationContext,
            "已隐藏 AutoTap 按钮，无障碍仍开启。需要时再点一次桌面上的 iTap",
            Toast.LENGTH_LONG
        ).show()
        stopAutoTapFully()
        floatingPanel?.detach()
        floatingPanel = null
    }

    private fun attachPanelIfAllowed() {
        if (floatingPanel == null) floatingPanel = FloatingPanelController(this)
        try {
            floatingPanel?.attach()
            floatingPanel?.showPanel()
        } catch (e: Exception) {
            Log.e(TAG, "面板挂载失败", e)
        }
    }

    private fun ensureShowPanelReceiver() {
        if (showPanelReceiver != null) return
        showPanelReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_SHOW_PANEL) attachPanelIfAllowed()
            }
        }
        val filter = IntentFilter(ACTION_SHOW_PANEL)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(showPanelReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(showPanelReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "注册广播失败", e)
            showPanelReceiver = null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.also {
            it.flags = it.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        ItapHoldBridge.onIdleToHolding = {
            stopAutoTapFully()
            floatingPanel?.detach()
            floatingPanel = null
        }
        Log.d(TAG, "服务已连接")
        ensureShowPanelReceiver()
        attachPanelIfAllowed()
        startAccessibilityKeepaliveForeground()
    }

    override fun onInterrupt() {
        stopAccessibilityKeepaliveForeground()
        ItapHoldBridge.onIdleToHolding = null
        floatingPanel?.detach()
        floatingPanel = null
        autoRunning = false
        handler.removeCallbacks(autoClickLoop)
    }

    override fun onDestroy() {
        stopAccessibilityKeepaliveForeground()
        ItapHoldBridge.onIdleToHolding = null
        showPanelReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            showPanelReceiver = null
        }
        floatingPanel?.detach()
        floatingPanel = null
        autoRunning = false
        handler.removeCallbacks(autoClickLoop)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    private fun screenSize(wm: WindowManager): Pair<Float, Float> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            Pair(b.width().toFloat(), b.height().toFloat())
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            Pair(m.widthPixels.toFloat(), m.heightPixels.toFloat())
        }
}
