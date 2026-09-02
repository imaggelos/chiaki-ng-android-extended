diff --git a/android/app/src/main/java/com/metallic/chiaki/common/Preferences.kt b/android/app/src/main/java/com/metallic/chiaki/common/Preferences.kt
index 0000000..0000000 100644
--- a/android/app/src/main/java/com/metallic/chiaki/common/Preferences.kt
+++ b/android/app/src/main/java/com/metallic/chiaki/common/Preferences.kt
@@
     val sharpnessIntensityKey get() = "preferences_sharpness_intensity"
     var sharpnessIntensity: Float
         get() = sharedPreferences.getInt(sharpnessIntensityKey, 0).toFloat() / 100f
         set(value) { sharedPreferences.edit().putInt(sharpnessIntensityKey, (value * 100f).toInt()).apply() }
+
+    // Fire & Drag sensitivity: stored as int representing thousandths (5..50 => 0.005..0.050), default 15 => 0.015
+    val fireDragSensitivityKey get() = "preferences_fire_drag_sensitivity"
+    var fireDragSensitivity: Float
+        get() = sharedPreferences.getInt(fireDragSensitivityKey, 15).toFloat() / 1000f
+        set(value) { sharedPreferences.edit().putInt(fireDragSensitivityKey, (value * 1000f).toInt()).apply() }
*** End Patch
