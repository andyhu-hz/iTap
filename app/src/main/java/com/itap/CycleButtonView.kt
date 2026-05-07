package com.itap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * 连点按钮视觉：空圆 / 三同心圆+点（运行中）/ 单外圈（过渡）。
 * 长按约 2.5 秒触发 [onLongPress]（短按仍走 [onTap]）。
 */
class CycleButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class VisualState {
        IDLE_EMPTY,
        PRESSED,
        RELEASED
    }

    companion object {
        private const val LONG_PRESS_MS = 2500L
    }

    var useLightStroke: Boolean = false
        set(value) {
            field = value
            applyStrokeColors()
            paintFill.color = if (value) 0xFFFFFFFF.toInt() else 0xFF202020.toInt()
            invalidate()
        }

    var visualState: VisualState = VisualState.IDLE_EMPTY
        set(value) {
            field = value
            invalidate()
        }

    var onTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null

    private val uiHandler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false
    private val longPressRunnable = Runnable {
        longPressTriggered = true
        onLongPress?.invoke()
    }

    private val paintRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val center = PointF()

    init {
        applyStrokeColors()
        paintFill.color = if (useLightStroke) 0xFFFFFFFF.toInt() else 0xFF303030.toInt()
    }

    private fun applyStrokeColors() {
        // 描线固定白色（深色底上可见）；实心仍由 [useLightStroke] 控制
        paintRing.color = 0xFFFFFFFF.toInt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        center.set(w / 2f, h / 2f)
        val r = min(w, h) / 2f * 0.92f
        paintRing.strokeWidth = (1.75f * resources.displayMetrics.density).coerceIn(1f, 3f)
        ringOuter = r * 0.88f
        ringMid = r * 0.55f
        ringInner = r * 0.30f
        dotR = r * 0.12f
    }

    private var ringOuter = 0f
    private var ringMid = 0f
    private var ringInner = 0f
    private var dotR = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (visualState) {
            VisualState.IDLE_EMPTY -> {
                canvas.drawCircle(center.x, center.y, ringOuter * 0.82f, paintRing)
            }
            VisualState.PRESSED -> {
                canvas.drawCircle(center.x, center.y, ringOuter, paintRing)
                canvas.drawCircle(center.x, center.y, ringMid, paintRing)
                canvas.drawCircle(center.x, center.y, ringInner, paintRing)
                canvas.drawCircle(center.x, center.y, dotR, paintFill)
            }
            VisualState.RELEASED -> {
                canvas.drawCircle(center.x, center.y, ringOuter, paintRing)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressTriggered = false
                uiHandler.removeCallbacks(longPressRunnable)
                uiHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            }
            MotionEvent.ACTION_UP -> {
                uiHandler.removeCallbacks(longPressRunnable)
                if (!longPressTriggered &&
                    event.x in 0f..width.toFloat() &&
                    event.y in 0f..height.toFloat()
                ) {
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                uiHandler.removeCallbacks(longPressRunnable)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap?.invoke()
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        uiHandler.removeCallbacks(longPressRunnable)
    }
}
