package com.metallic.chiaki.touchcontrols

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class FireDragView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    interface Listener {
        fun onHoldStart(startRawX: Float, startRawY: Float)
        fun onDrag(dx: Float, dy: Float)
        fun onHoldEnd()
    }

    private var listener: Listener? = null
    fun setListener(l: Listener?) { listener = l }

    // touch state
    private var isDown = false
    private var activePointerId = -1
    private var lastRawX = 0f
    private var lastRawY = 0f

    // Simple visuals - translucent white styles
    private val idleAlpha = (0.18f * 255).toInt()
    private val pressedAlpha = (0.40f * 255).toInt()

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(idleAlpha, 255, 255, 255)
        isAntiAlias = true
    }
    private val pressedPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(pressedAlpha, 255, 255, 255)
        isAntiAlias = true
    }
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = Color.argb((0.35f * 255).toInt(), 255, 255, 255)
        isAntiAlias = true
    }
    private val iconPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(255, 255, 255, 255)
        isAntiAlias = true
    }
    private val iconStroke = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = Color.argb(200, 255, 255, 255)
        isAntiAlias = true
    }

    private val flamePath = Path()
    private val arrowPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val paint = if (isDown) pressedPaint else bgPaint
        val r = (width.coerceAtMost(height) / 2f)
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, r, paint)
        canvas.drawCircle(cx, cy, r, borderPaint)

        // Draw a simple stylized flame - centered inside circle
        flamePath.reset()
        val fw = width * 0.36f
        val fh = height * 0.44f
        flamePath.moveTo(cx, cy - fh * 0.5f)
        flamePath.cubicTo(cx - fw * 0.8f, cy - fh * 0.2f, cx - fw * 0.4f, cy + fh * 0.45f, cx, cy + fh * 0.5f)
        flamePath.cubicTo(cx + fw * 0.4f, cy + fh * 0.45f, cx + fw * 0.8f, cy - fh * 0.2f, cx, cy - fh * 0.5f)
        flamePath.close()
        canvas.drawPath(flamePath, iconPaint)

        // Draw diagonal arrow (up-right) overlaying the flame
        arrowPath.reset()
        val ax1 = cx - fw * 0.25f
        val ay1 = cy + fh * 0.2f
        val ax2 = cx + fw * 0.45f
        val ay2 = cy - fh * 0.45f
        arrowPath.moveTo(ax1, ay1)
        arrowPath.lineTo(ax2, ay2)
        val ahx = ax2
        val ahy = ay2
        val headLen = 10f * resources.displayMetrics.density
        val vx = ax2 - ax1
        val vy = ay2 - ay1
        val vlen = kotlin.math.hypot(vx.toDouble(), vy.toDouble()).toFloat().coerceAtLeast(1f)
        val nx = vx / vlen
        val ny = vy / vlen
        val cos30 = 0.8660254f
        val sin30 = 0.5f
        val rx1 = cos30 * nx - sin30 * ny
        val ry1 = sin30 * nx + cos30 * ny
        val rx2 = cos30 * nx + sin30 * ny
        val ry2 = -sin30 * nx + cos30 * ny
        arrowPath.moveTo(ahx, ahy)
        arrowPath.lineTo(ahx - rx1 * headLen, ahy - ry1 * headLen)
        arrowPath.moveTo(ahx, ahy)
        arrowPath.lineTo(ahx - rx2 * headLen, ahy - ry2 * headLen)
        canvas.drawPath(arrowPath, iconStroke)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isDown = true
                activePointerId = ev.getPointerId(0)
                // use raw coordinates based on view location + local coords to support pointer index
                val loc = IntArray(2)
                getLocationOnScreen(loc)
                lastRawX = ev.getX(0) + loc[0]
                lastRawY = ev.getY(0) + loc[1]
                listener?.onHoldStart(lastRawX, lastRawY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // ignore additional pointers
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDown) return true
                val idx = ev.findPointerIndex(activePointerId)
                if (idx == -1) return true
                val loc = IntArray(2)
                getLocationOnScreen(loc)
                val curRawX = ev.getX(idx) + loc[0]
                val curRawY = ev.getY(idx) + loc[1]
                val dx = curRawX - lastRawX
                val dy = curRawY - lastRawY
                lastRawX = curRawX
                lastRawY = curRawY
                listener?.onDrag(dx, dy)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pid = ev.getPointerId(ev.actionIndex)
                if (pid == activePointerId) {
                    isDown = false
                    activePointerId = -1
                    parent?.requestDisallowInterceptTouchEvent(false)
                    listener?.onHoldEnd()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDown = false
                activePointerId = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                listener?.onHoldEnd()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}
