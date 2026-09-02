*** Begin Patch
*** Update File: android/app/src/main/java/com/metallic/chiaki/touchcontrols/TouchControlsFragment.kt
@@
-    // Fire & Drag - continuous velocity/accumulator aiming model
-    private val FIRE_AIM_SENSITIVITY = 0.015f
+    // Fire & Drag - continuous velocity/accumulator aiming model
+    // FIRE_AIM_SENSITIVITY is now read dynamically from Preferences.fireDragSensitivity per onDrag()
@@
     @Volatile
     private var aimX = 0f
@@
     private var publisherRunnable: Runnable? = null
+
+    private fun ensurePublisherRunning() {
+        if (publisherRunnable != null) return
+        publisherRunnable = object : Runnable {
+            override fun run() {
+                // If last move is older than inactivity timeout, publish neutral right stick
+                val now = SystemClock.uptimeMillis()
+                val age = now - lastMoveTime
+                if (age > INACTIVITY_TIMEOUT_MS) {
+                    // publish neutral right stick without clearing aim accumulator while finger down
+                    ownControllerState = ownControllerState.copy().apply { rightX = 0.toShort(); rightY = 0.toShort() }
+                } else {
+                    // publish current aimX/aimY
+                    val px = (Short.MAX_VALUE * aimX).toInt().toShort()
+                    val py = (Short.MAX_VALUE * aimY).toInt().toShort()
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
