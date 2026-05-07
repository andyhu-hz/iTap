package com.itap

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import kotlin.math.roundToInt

/**
 * 顶部居中略偏右的 AutoTap 控制按钮（小号）；长按约 2.5 秒隐藏面板；点桌面图标可恢复。
 *
 * 使用 [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]（无障碍服务专属类型），
 * 不需要 SYSTEM_ALERT_WINDOW 权限，从而避免 ColorOS 对该权限 app 限制 continueStroke。
 * WindowManager 通过 service 自身 context 获取，而非 applicationContext。
 */
class FloatingPanelController(private val service: ITapService) {

    private var windowManager: WindowManager? = null
    private var container: LinearLayout? = null
    private var actionButton: CycleButtonView? = null
    private var lp: WindowManager.LayoutParams? = null
    /** 视图层级已创建（不代表在 WM 里） */
    private var viewsCreated = false
    /** 当前在 WindowManager 里 */
    private var inWindowManager = false
    private var tapCount = 0

    @SuppressLint("InflateParams")
    fun attach() {
        if (inWindowManager) return
        // 使用 service 自身 context，TYPE_ACCESSIBILITY_OVERLAY 需要有效的 service window token
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        if (!viewsCreated) {
            val density = service.resources.displayMetrics.density
            val btnPx = (36 * density).roundToInt()
            val padH = (3 * density).roundToInt()
            val padBottom = (3 * density).roundToInt()

            val btn = CycleButtonView(service).apply {
                useLightStroke = false
                layoutParams = LinearLayout.LayoutParams(btnPx, btnPx)
                onTap = { onButtonTapped() }
                onLongPress = { service.requestExitFromLongPress() }
            }
            actionButton = btn

            val root = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                // 顶格：无上内边距，窗口 y=0 时贴屏幕顶
                setPadding(padH, 0, padH, padBottom)
                addView(btn)
            }
            container = root

            val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

            val density2 = service.resources.displayMetrics.density
            lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = (25 * density2).roundToInt()
                y = (10 * density2).roundToInt()
            }
            viewsCreated = true
        }

        try {
            wm.addView(container, lp)
            inWindowManager = true
        } catch (e: Exception) {
            // view 可能因 parent 冲突失败，强制重建
            viewsCreated = false
        }
    }

    /** 彻底销毁（长按隐藏 / 服务销毁时调用）。 */
    fun detach() {
        if (inWindowManager) {
            try { container?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
            inWindowManager = false
        }
        container = null
        windowManager = null
        actionButton = null
        lp = null
        viewsCreated = false
        tapCount = 0
    }

    /**
     * Hold 开始时调用：把窗口从 WM 移除，使 ColorOS 等 OEM 解除对 dispatchGesture 的限制。
     * 视图层级保留，Hold 结束后可快速通过 [showPanel] 重新加入 WM。
     */
    fun hidePanel() {
        if (!inWindowManager) return
        try {
            container?.let { windowManager?.removeView(it) }
            inWindowManager = false
        } catch (_: Exception) {}
    }

    /** Hold 结束时调用：把窗口重新加入 WM。 */
    fun showPanel() {
        if (inWindowManager) return
        if (!viewsCreated) return
        val wm = windowManager ?: service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        try {
            wm.addView(container, lp)
            inWindowManager = true
        } catch (_: Exception) {}
    }

    fun resetUiAndCounter() {
        tapCount = 0
        actionButton?.visualState = CycleButtonView.VisualState.IDLE_EMPTY
    }

    private fun onButtonTapped() {
        val nextCount = tapCount + 1
        if (nextCount % 2 == 1 && !service.canAcceptButtonTap()) return
        tapCount++
        if (tapCount % 2 == 1) {
            actionButton?.visualState = CycleButtonView.VisualState.PRESSED
            service.onStartAutoClick()
        } else {
            actionButton?.visualState = CycleButtonView.VisualState.IDLE_EMPTY
            service.onStopAutoClick()
        }
    }
}
