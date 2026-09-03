// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.touchcontrols

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

interface FireDragListener {
    fun onHoldStart(rawX: Float, rawY: Float)
    fun onDrag(dx: Float, dy: Float)
    fun onHoldEnd()
}

class FireDragView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var listener: FireDragListener? = null

    private var isDown = false
    private var activePointerId = -1
    private var lastRawX = 0f
    private var lastRawY = 0f

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb((0.15f * 255).toInt(), 255, 255, 255)
        isAntiAlias = true
    }
    private val pressedPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb((0.25f * 255).toInt(), 255, 255, 255)
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
        color = Color.WHITE
        isAntiAlias = true
    }
    private val flamePath = Path()
    private val arrowPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val paint = if (isDown) pressedPaint else bgPaint
        // background
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 12f * resources.displayMetrics.density, 12f * resources.displayMetrics.density, paint)
        // border
        canvas.drawRoundRect(1f, 1f, width.toFloat()-1f, height.toFloat()-1f, 12f * resources.displayMetrics.density, 12f * resources.displayMetrics.density, borderPaint)
        // icon: stylized flame + diagonal arrow
        val cx = width / 2f
        val cy = height / 2f
        val s = kotlin.math.min(width, height) / 3f
        // flame
        flamePath.reset()
        flamePath.moveTo(cx, cy - s * 0.6f)
        flamePath.quadTo(cx + s*0.35f, cy - s*0.9f, cx + s*0.25f, cy - s*0.1f)
        flamePath.quadTo(cx + s*0.15f, cy + s*0.25f, cx, cy + s*0.4f)
        flamePath.quadTo(cx - s*0.15f, cy + s*0.25f, cx - s*0.25f, cy - s*0.1f)
        flamePath.quadTo(cx - s*0.35f, cy - s*0.9f, cx, cy - s*0.6f)
        flamePath.close()
        canvas.drawPath(flamePath, iconPaint)
        // diagonal arrow
        arrowPath.reset()
        val ax = cx + s*0.45f
        val ay = cy - s*0.45f
        arrowPath.moveTo(ax - s*0.5f, ay + s*0.5f)
        arrowPath.lineTo(ax + s*0.2f, ay + s*0.5f)
        arrowPath.lineTo(ax + s*0.2f, ay + s*0.2f)
        arrowPath.lineTo(ax + s*0.7f, ay - s*0.3f)
        arrowPath.lineTo(ax + s*0.4f, ay - s*0.3f)
        arrowPath.lineTo(ax + s*0.4f, ay - s*0.6f)
        arrowPath.close()
        canvas.drawPath(arrowPath, iconPaint)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // start tracking active pointer, immediately notify fragment
                isDown = true
                activePointerId = ev.getPointerId(0)
                lastRawX = ev.rawX
                lastRawY = ev.rawY
                listener?.onHoldStart(lastRawX, lastRawY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDown) return true
                // find index for active pointer
                val pid = activePointerId
                val idx = ev.findPointerIndex(pid)
                if (idx < 0) return true
                // calculate raw coordinates robustly
                val curRawX = ev.getX(idx) + (ev.rawX - ev.x)
                val curRawY = ev.getY(idx) + (ev.rawY - ev.y)
                val dx = curRawX - lastRawX
                val dy = curRawY - lastRawY
                lastRawX = curRawX
                lastRawY = curRawY
                // physically move the view by same delta
                x = x + dx
                y = y + dy
                // inform listener with raw delta
                listener?.onDrag(dx, dy)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // check if active pointer was released
                val pid = ev.getPointerId(ev.actionIndex)
                if (pid == activePointerId) {
                    // active pointer released
                    activePointerId = -1
                    isDown = false
                    listener?.onHoldEnd()
                    invalidate()
                    return true
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // stop tracking, leave view where dragged
                activePointerId = -1
                isDown = false
                listener?.onHoldEnd()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}
