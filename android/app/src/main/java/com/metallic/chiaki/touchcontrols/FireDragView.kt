*** Begin Patch
*** Update File: android/app/src/main/java/com/metallic/chiaki/touchcontrols/FireDragView.kt
@@
-import android.os.Handler
-import android.os.Looper
+import android.os.Handler
+import android.os.Looper
+import android.view.ViewConfiguration
@@
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
@@
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
         val s = Math.min(width, height) / 3f
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
@@
     override fun onTouchEvent(ev: MotionEvent): Boolean {
         when (ev.actionMasked) {
             MotionEvent.ACTION_DOWN -> {
-                isDown = true
-                isHolding = false
-                downRawX = ev.rawX
-                downRawY = ev.rawY
-                handler.postDelayed(holdRunnable, holdMs)
-                invalidate()
-                return true
+                // start tracking active pointer
+                isDown = true
+                activePointerId = ev.getPointerId(0)
+                lastRawX = ev.rawX
+                lastRawY = ev.rawY
+                // Immediately notify fragment that hold/aim started
+                listener?.onHoldStart(lastRawX, lastRawY)
+                invalidate()
+                return true
             }
             MotionEvent.ACTION_MOVE -> {
-                if (isHolding) {
-                    // compute delta relative to hold start
-                    val dx = ev.rawX - downRawX
-                    val dy = ev.rawY - downRawY
-                    listener?.onDrag(dx, dy)
-                }
-                return true
+                if (!isDown) return true
+                // find index for active pointer
+                val pid = activePointerId
+                val idx = ev.findPointerIndex(pid)
+                if (idx < 0) return true
+                val curRawX = ev.getX(idx) + ev.rawX - ev.x // normalize to rawX
+                val curRawY = ev.getY(idx) + ev.rawY - ev.y
+                val dx = curRawX - lastRawX
+                val dy = curRawY - lastRawY
+                lastRawX = curRawX
+                lastRawY = curRawY
+                // physically move the view by same delta
+                x = x + dx
+                y = y + dy
+                // inform listener with raw delta
+                listener?.onDrag(dx, dy)
+                return true
             }
             MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
-                handler.removeCallbacks(holdRunnable)
-                if (!isHolding) {
-                    // TAP: user released before hold timer -> trigger tap
-                    listener?.onTap()
-                } else {
-                    // ended a hold/drag
-                    listener?.onHoldEnd()
-                }
-                isDown = false
-                isHolding = false
-                invalidate()
-                return true
+                // stop tracking, leave view where dragged
+                activePointerId = -1
+                isDown = false
+                listener?.onHoldEnd()
+                invalidate()
+                return true
             }
         }
         return super.onTouchEvent(ev)
     }
 }
*** End Patch