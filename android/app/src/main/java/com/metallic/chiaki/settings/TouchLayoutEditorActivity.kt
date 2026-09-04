// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.metallic.chiaki.settings

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.metallic.chiaki.R

class TouchLayoutEditorActivity : AppCompatActivity() {
    private val controlIds = listOf(
        "motionUpButton", "motionDownButton", "motionLeftButton", "motionRightButton",
        "crossButtonView", "boxButtonView", "pyramidButtonView", "moonButtonView"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_controls)

        controlIds.forEach { idStr ->
            val resId = resources.getIdentifier(idStr, "id", packageName)
            val view = findViewById<View?>(resId) ?: return@forEach
            makeDraggable(view, idStr)
            view.setOnLongClickListener {
                val visible = !TouchLayoutPrefs.getButtonVisible(this, idStr, true)
                TouchLayoutPrefs.setButtonVisible(this, idStr, visible)
                view.visibility = if (visible) View.VISIBLE else View.GONE
                true
            }
            val vis = TouchLayoutPrefs.getButtonVisible(this, idStr, true)
            view.visibility = if (vis) View.VISIBLE else View.GONE
            applySavedPosition(view, idStr)
        }
    }

    private fun makeDraggable(view: View, idStr: String) {
        var dX = 0f
        var dY = 0f
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = event.rawX + dX
                    val newY = event.rawY + dY
                    v.x = newX
                    v.y = newY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val parent = v.parent as View
                    val xFrac = v.x / parent.width.toFloat()
                    val yFrac = v.y / parent.height.toFloat()
                    TouchLayoutPrefs.setButtonPos(this, idStr, xFrac, yFrac)
                    true
                }
                else -> false
            }
        }
    }

    private fun applySavedPosition(v: View, idStr: String) {
        val (xFrac, yFrac) = TouchLayoutPrefs.getButtonPos(this, idStr)
        if (xFrac >= 0f && yFrac >= 0f) {
            v.post {
                val parent = v.parent as View
                v.x = xFrac * parent.width
                v.y = yFrac * parent.height
            }
        }
    }
}
