*** Begin Patch
*** Update File: android/app/src/main/java/com/metallic/chiaki/touchcontrols/TouchControlsFragment.kt
@@
-import android.os.Bundle
-import android.os.Handler
-import android.os.Looper
+import android.os.Bundle
+import android.os.Handler
+import android.os.Looper
+import android.os.SystemClock
@@
 class DefaultTouchControlsFragment : TouchControlsFragment() {
@@
-    // Motion handling
-    private val motionHandler = Handler(Looper.getMainLooper())
-    // Pulse length (250ms) to let the streamer send multiple updates
+    // Motion handling
+    private val motionHandler = Handler(Looper.getMainLooper())
+    // Publish interval and inactivity timeouts for fire-drag aiming
+    private val PUBLISH_INTERVAL_MS = 8L
+    private val INACTIVITY_TIMEOUT_MS = 30L
@@
-    private var aimAccumulatorSmoothing = 0.55f
-    @Volatile
-    private var aimX = 0f
-    @Volatile
-    private var aimY = 0f
-    private var lastMoveTime = 0L
+    private val FIRE_PIXELS_FOR_FULL_STICK = 80f
+    private val FIRE_PIXEL_DEADZONE = 1.0f
+    private val FIRE_SMOOTHING = 0.55f
+
+    @Volatile
+    private var aimX = 0f
+    @Volatile
+    private var aimY = 0f
+    private var smoothedX = 0f
+    private var smoothedY = 0f
+    private var lastMoveTime = 0L
@@
-    private var publisherRunnable: Runnable? = null
+    private var publisherRunnable: Runnable? = null
@@
-    private fun ensurePublisherRunning() {
-        if (publisherRunnable != null) return
-        publisherRunnable = object : Runnable {
-            override fun run() {
-                // If last move is older than inactivity timeout, publish neutral right stick
-                val now = SystemClock.uptimeMillis()
-                val age = now - lastMoveTime
-                if (age > INACTIVITY_TIMEOUT_MS) {
-                    // publish neutral right stick without clearing aim accumulator while finger down
-                    ownControllerState = ownControllerState.copy().apply { rightX = 0.toShort(); rightY = 0.toShort() }
-                } else {
-                    // publish current aimX/aimY
-                    val px = (Short.MAX_VALUE * aimX).toInt().toShort()
-                    val py = (Short.MAX_VALUE * aimY).toInt().toShort()
-                    ownControllerState = ownControllerState.copy().apply { rightX = px; rightY = py }
-                }
-                motionHandler.postDelayed(this, PUBLISH_INTERVAL_MS)
-            }
-        }
-        motionHandler.post(publisherRunnable!!)
-    }
-
-    private fun stopPublisher() {
-        publisherRunnable?.let { motionHandler.removeCallbacks(it) }
-        publisherRunnable = null
-    }
+    private fun ensurePublisherRunning() {
+        if (publisherRunnable != null) return
+        publisherRunnable = object : Runnable {
+            override fun run() {
+                val now = SystemClock.uptimeMillis()
+                val age = now - lastMoveTime
+                if (age > INACTIVITY_TIMEOUT_MS) {
+                    // send neutral right stick
+                    smoothedX = 0f
+                    smoothedY = 0f
+                    ownControllerState = ownControllerState.copy().apply { rightX = 0.toShort(); rightY = 0.toShort() }
+                } else {
+                    // smoothing towards target normalized aim
+                    val targetX = aimX.coerceIn(-1f, 1f)
+                    val targetY = aimY.coerceIn(-1f, 1f)
+                    smoothedX += (targetX - smoothedX) * FIRE_SMOOTHING
+                    smoothedY += (targetY - smoothedY) * FIRE_SMOOTHING
+                    val px = (smoothedX * Short.MAX_VALUE).toInt().toShort()
+                    val py = (smoothedY * Short.MAX_VALUE).toInt().toShort()
+                    ownControllerState = ownControllerState.copy().apply { rightX = px; rightY = py }
+                }
+                motionHandler.postDelayed(this, PUBLISH_INTERVAL_MS)
+            }
+        }
+        motionHandler.post(publisherRunnable!!)
+    }
+
+    private fun stopPublisher() {
+        publisherRunnable?.let { motionHandler.removeCallbacks(it) }
+        publisherRunnable = null
+    }
*** End Patch