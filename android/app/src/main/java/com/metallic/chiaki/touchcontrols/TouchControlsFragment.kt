// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.touchcontrols

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.metallic.chiaki.databinding.FragmentControlsBinding
import com.metallic.chiaki.lib.ControllerState
import com.metallic.chiaki.settings.TouchLayoutPrefs
import io.reactivex.Observable
import io.reactivex.rxkotlin.Observables.combineLatest
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject

private enum class MotionDir { UP, DOWN, LEFT, RIGHT }

abstract class TouchControlsFragment : Fragment() {
    protected var ownControllerState = ControllerState()
        set(value) {
            val diff = field != value
            field = value
            if (diff) ownControllerStateSubject.onNext(ownControllerState)
        }

    protected val ownControllerStateSubject: Subject<ControllerState> =
        BehaviorSubject.create<ControllerState>().also { it.onNext(ownControllerState) }

    protected val controllerStateProxy: Subject<Observable<ControllerState>> =
        BehaviorSubject.create<Observable<ControllerState>>().also { it.onNext(ownControllerStateSubject) }

    val controllerState: Observable<ControllerState> get() = controllerStateProxy.flatMap { it }
    var onScreenControlsEnabled: LiveData<Boolean>? = null
}

class DefaultTouchControlsFragment : TouchControlsFragment() {
    private var _binding: FragmentControlsBinding? = null
    private val binding get() = _binding!!

    // Motion handling
    private val motionHandler = Handler(Looper.getMainLooper())
    // Very short, sharp impulse so the game sees an instant flick
    private val MOTION_IMPULSE_MS = 40L
    private val MOTION_REPEAT_INTERVAL_MS = 160L
    private val motionRepeatRunnables = mutableMapOf<MotionDir, Runnable>()

    // Aggressive impulse magnitudes (increased 5x-10x over previous values)
    private val IMPULSE_ACCEL_DOWN = -40.0f
    private val IMPULSE_ACCEL_UP = 40.0f
    private val IMPULSE_ACCEL_LEFT = -30.0f
    private val IMPULSE_ACCEL_RIGHT = 30.0f
    private val NEUTRAL_GYRO = 0.0f
    private val NEUTRAL_ORIENT_W = 1.0f

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentControlsBinding.inflate(inflater, container, false).let {
            _binding = it
            controllerStateProxy.onNext(combineLatest(ownControllerStateSubject, binding.touchpadView.controllerState) { a, b -> a or b })
            it.root
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Existing wiring
        binding.dpadView.stateChangeCallback = this::dpadStateChanged
        binding.crossButtonView.buttonPressedCallback = buttonStateChanged(ControllerState.BUTTON_CROSS)
        binding.moonButtonView.buttonPressedCallback = buttonStateChanged(ControllerState.BUTTON_MOON)
        binding.pyramidButtonView.buttonPressedCallback = buttonStateChanged(ControllerState.BUTTON_PYRAMID)
        binding.boxButtonView.buttonPressedCallback = buttonStateChanged(ControllerState.BUTTON_BOX)
        binding.l1ButtonView.buttonPressedCallback = buttonStateChanged(ControllerState.BUTTON_L1)
        binding.r1ButtonView.buttonPressedCallback = buttonStateChanged(ControllerState.BUTTON_R1)
        binding.l3ButtonView.buttonPressedCallback = buttonStateChanged(ControllerState.BUTTON_L3)
        binding.r3ButtonView.buttonPressedCallback = buttonStateChanged(ControllerState.BUTTON_R3)
        binding.optionsButtonView.buttonPressedCallback = buttonStateChanged(ControllerState.BUTTON_OPTIONS)
        binding.shareButtonView.buttonPressedCallback = buttonStateChanged(ControllerState.BUTTON_SHARE)
        binding.psButtonView.buttonPressedCallback = buttonStateChanged(ControllerState.BUTTON_PS)

        binding.l2ButtonView.buttonPressedCallback = { ownControllerState = ownControllerState.copy().apply { l2State = if (it) 255U else 0U } }
        binding.r2ButtonView.buttonPressedCallback = { ownControllerState = ownControllerState.copy().apply { r2State = if (it) 255U else 0U } }

        val quantizeStick = { f: Float -> (Short.MAX_VALUE * f).toInt().toShort() }

        binding.leftAnalogStickView.stateChangedCallback = { ownControllerState = ownControllerState.copy().apply { leftX = quantizeStick(it.x); leftY = quantizeStick(it.y) } }
        binding.rightAnalogStickView.stateChangedCallback = { ownControllerState = ownControllerState.copy().apply { rightX = quantizeStick(it.x); rightY = quantizeStick(it.y) } }

        // Motion buttons (ensure layout IDs present)
        setupMotionButton(binding.motionUpButton, MotionDir.UP, "motionUpButton")
        setupMotionButton(binding.motionDownButton, MotionDir.DOWN, "motionDownButton")
        setupMotionButton(binding.motionLeftButton, MotionDir.LEFT, "motionLeftButton")
        setupMotionButton(binding.motionRightButton, MotionDir.RIGHT, "motionRightButton")

        // Apply saved positions & visibility
        applySavedLayoutToViews()

        onScreenControlsEnabled?.observe(viewLifecycleOwner, Observer {
            view.visibility = if (it) View.VISIBLE else View.GONE
        })
    }

    private fun applySavedLayoutToViews() {
        val mapping = mapOf(
            "motionUpButton" to binding.motionUpButton,
            "motionDownButton" to binding.motionDownButton,
            "motionLeftButton" to binding.motionLeftButton,
            "motionRightButton" to binding.motionRightButton
        )

        mapping.forEach { (idStr, v) ->
            val visible = TouchLayoutPrefs.getButtonVisible(requireContext(), idStr, true)
            v.visibility = if (visible) View.VISIBLE else View.GONE

            val (xFrac, yFrac) = TouchLayoutPrefs.getButtonPos(requireContext(), idStr)
            if (xFrac >= 0f && yFrac >= 0f) {
                v.post {
                    val parent = v.parent as View
                    v.x = xFrac * parent.width
                    v.y = yFrac * parent.height
                }
            }
        }
    }

    private fun setupMotionButton(button: View, dir: MotionDir, idStr: String) {
        // Short press = single impulse; hold = repeating pulses
        button.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> startMotionPulse(dir, singleShot = false)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopMotionPulse(dir)
            }
            true
        }
    }

    private fun startMotionPulse(dir: MotionDir, singleShot: Boolean) {
        // Emit a very sharp high-magnitude pulse and schedule immediate clearing
        emitPulse(dir)
        motionHandler.postDelayed({ clearMotionFields() }, MOTION_IMPULSE_MS)

        if (!singleShot) {
            stopMotionPulse(dir)
            val runnable = object : Runnable {
                override fun run() {
                    emitPulse(dir)
                    motionHandler.postDelayed({ clearMotionFields() }, MOTION_IMPULSE_MS)
                    motionHandler.postDelayed(this, MOTION_REPEAT_INTERVAL_MS)
                }
            }
            motionRepeatRunnables[dir] = runnable
            motionHandler.postDelayed(runnable, MOTION_REPEAT_INTERVAL_MS)
        }
    }

    private fun emitPulse(dir: MotionDir) {
        when (dir) {
            MotionDir.DOWN -> setMotionFields(0f, IMPULSE_ACCEL_DOWN, 0f, NEUTRAL_GYRO, NEUTRAL_GYRO, NEUTRAL_GYRO)
            MotionDir.UP -> setMotionFields(0f, IMPULSE_ACCEL_UP, 0f, NEUTRAL_GYRO, NEUTRAL_GYRO, NEUTRAL_GYRO)
            MotionDir.LEFT -> setMotionFields(IMPULSE_ACCEL_LEFT, 0f, 0f, NEUTRAL_GYRO, NEUTRAL_GYRO, NEUTRAL_GYRO)
            MotionDir.RIGHT -> setMotionFields(IMPULSE_ACCEL_RIGHT, 0f, 0f, NEUTRAL_GYRO, NEUTRAL_GYRO, NEUTRAL_GYRO)
        }
    }

    private fun stopMotionPulse(dir: MotionDir) {
        motionRepeatRunnables[dir]?.let { motionHandler.removeCallbacks(it); motionRepeatRunnables.remove(dir) }
        clearMotionFields()
    }

    private fun setMotionFields(accelX: Float, accelY: Float, accelZ: Float, gyroX: Float, gyroY: Float, gyroZ: Float) {
        ownControllerState = ownControllerState.copy().apply {
            this.accelX = accelX
            this.accelY = accelY
            this.accelZ = accelZ
            this.gyroX = gyroX
            this.gyroY = gyroY
            this.gyroZ = gyroZ
            this.orientX = 0.0f
            this.orientY = 0.0f
            this.orientZ = 0.0f
            this.orientW = NEUTRAL_ORIENT_W
        }
    }

    private fun clearMotionFields() {
        setMotionFields(0f, 0f, 0f, 0f, 0f, 0f)
    }

    private fun dpadStateChanged(direction: DPadView.Direction?) {
        ownControllerState = ownControllerState.copy().apply {
            buttons = ((buttons
                    and ControllerState.BUTTON_DPAD_LEFT.inv()
                    and ControllerState.BUTTON_DPAD_RIGHT.inv()
                    and ControllerState.BUTTON_DPAD_UP.inv()
                    and ControllerState.BUTTON_DPAD_DOWN.inv())
                    or when (direction) {
                DPadView.Direction.UP -> ControllerState.BUTTON_DPAD_UP
                DPadView.Direction.DOWN -> ControllerState.BUTTON_DPAD_DOWN
                DPadView.Direction.LEFT -> ControllerState.BUTTON_DPAD_LEFT
                DPadView.Direction.RIGHT -> ControllerState.BUTTON_DPAD_RIGHT
                DPadView.Direction.LEFT_UP -> ControllerState.BUTTON_DPAD_LEFT or ControllerState.BUTTON_DPAD_UP
                DPadView.Direction.LEFT_DOWN -> ControllerState.BUTTON_DPAD_LEFT or ControllerState.BUTTON_DPAD_DOWN
                DPadView.Direction.RIGHT_UP -> ControllerState.BUTTON_DPAD_RIGHT or ControllerState.BUTTON_DPAD_UP
                DPadView.Direction.RIGHT_DOWN -> ControllerState.BUTTON_DPAD_RIGHT or ControllerState.BUTTON_DPAD_DOWN
                null -> 0U
            })
        }
    }

    private fun buttonStateChanged(buttonMask: UInt) = { pressed: Boolean ->
        ownControllerState = ownControllerState.copy().apply {
            buttons = if (pressed) buttons or buttonMask else buttons and buttonMask.inv()
        }
    }
}
