package com.metallic.chiaki.touchcontrols

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class FireDragView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    interface Listener {
        fun onTap()
        fun onHoldStart(startRawX: Float, startRawY: Float)
        fun onDrag(dx: Float, dy: Float)
        fun onHoldEnd()
    }

    private var listener: Listener? = null
    fun setListener(l: Listener?) { listener = l }

    private val handler = Handler(Looper.getMainLooper())
    private val holdMs = 250L

    // touch state
    private var isDown = false
    private var isHolding = false
    private var downRawX = 0f
    private var downRawY = 0f

    private val holdRunnable = Runnable {
        isHolding = true
        listener?.onHoldStart(downRawX, downRawY)
        invalidate()
    }

    // Simple visuals
    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb((0.30f * 255).toInt(), 255, 80, 0) // orange-ish 30% alpha
    }
    private val pressedPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb((0.70f * 255).toInt(), 255, 120, 20) // brighter on press
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val paint = if (isDown) pressedPaint else bgPaint
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 14f * resources.displayMetrics.density, 14f * resources.displayMetrics.density, paint)
        // Draw icon/text in center
        val text = "🔥↗"
        val x = width / 2f
        val y = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, x, y, textPaint)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDown = true
                isHolding = false
                downRawX = ev.rawX
                downRawY = ev.rawY
                handler.postDelayed(holdRunnable, holdMs)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isHolding) {
                    // compute delta relative to hold start
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    listener?.onDrag(dx, dy)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(holdRunnable)
                if (!isHolding) {
                    // TAP: user released before hold timer -> trigger tap
                    listener?.onTap()
                } else {
                    // ended a hold/drag
                    listener?.onHoldEnd()
                }
                isDown = false
                isHolding = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}
