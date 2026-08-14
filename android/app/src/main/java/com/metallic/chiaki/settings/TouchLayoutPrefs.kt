// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.settings

import android.content.Context
import kotlin.math.max
import kotlin.math.min

object TouchLayoutPrefs {
    private const val PREFS_NAME = "touch_layout_prefs"

    fun getButtonVisible(context: Context, id: String, default: Boolean = true): Boolean {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return p.getBoolean("${id}_visible", default)
    }

    fun setButtonVisible(context: Context, id: String, visible: Boolean) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        p.edit().putBoolean("${id}_visible", visible).apply()
    }

    // store position as fraction (0f..1f) of parent width/height for device independence
    fun getButtonPos(context: Context, id: String): Pair<Float, Float> {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val x = p.getFloat("${id}_x", -1f)
        val y = p.getFloat("${id}_y", -1f)
        return Pair(x, y)
    }

    fun setButtonPos(context: Context, id: String, xFraction: Float, yFraction: Float) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cx = min(1f, max(0f, xFraction))
        val cy = min(1f, max(0f, yFraction))
        p.edit().putFloat("${id}_x", cx).putFloat("${id}_y", cy).apply()
    }
}
