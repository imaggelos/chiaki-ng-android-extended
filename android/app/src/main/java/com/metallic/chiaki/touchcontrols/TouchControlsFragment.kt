// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.touchcontrols

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.metallic.chiaki.common.Preferences
import com.metallic.chiaki.databinding.FragmentControlsBinding
import com.metallic.chiaki.lib.ControllerState
import io.reactivex.Observable
import io.reactivex.rxkotlin.Observables.combineLatest
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject

abstract class TouchControlsFragment : Fragment()
{
	protected var ownControllerState = ControllerState()
		set(value)
		{
			val diff = field != value
			field = value
			if(diff)
				ownControllerStateSubject.onNext(ownControllerState)
		}

	protected val ownControllerStateSubject: Subject<ControllerState>
			= BehaviorSubject.create<ControllerState>().also { it.onNext(ownControllerState) }

	// to delay attaching to the touchpadView until it's available
	protected val controllerStateProxy: Subject<Observable<ControllerState>>
			= BehaviorSubject.create<Observable<ControllerState>>().also { it.onNext(ownControllerStateSubject) }
	val controllerState: Observable<ControllerState> get() =
		controllerStateProxy.flatMap { it }

	var onScreenControlsEnabled: LiveData<Boolean>? = null
}

class DefaultTouchControlsFragment : TouchControlsFragment(), FireDragListener
{
	private var _binding: FragmentControlsBinding? = null
	private val binding get() = _binding!!

	private val motionHandler = Handler(Looper.getMainLooper())

	// Fire & Drag constants
	private val PUBLISH_INTERVAL_MS = 8L
	private val INACTIVITY_TIMEOUT_MS = 30L
	private val FIRE_SMOOTHING = 0.55f

	// Fire & Drag state
	private var isAimActive = false
	private var aimX = 0f
	private var aimY = 0f
	private var smoothedX = 0f
	private var smoothedY = 0f
	private var lastMoveTime = 0L
	private var publisherRunnable: Runnable? = null

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
		FragmentControlsBinding.inflate(inflater, container, false).let {
			_binding = it
			controllerStateProxy.onNext(
				combineLatest(ownControllerStateSubject, binding.touchpadView.controllerState) { a, b -> a or b }
			)
			it.root
		}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?)
	{
		super.onViewCreated(view, savedInstanceState)
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

		binding.l2ButtonView.buttonPressedCallback = { ownControllerState = ownControllerState.copy().apply { l2State = if(it) 255U else 0U } }
		binding.r2ButtonView.buttonPressedCallback = { ownControllerState = ownControllerState.copy().apply { r2State = if(it) 255U else 0U } }

		val quantizeStick = { f: Float ->
			(Short.MAX_VALUE * f).toInt().toShort()
		}

		binding.leftAnalogStickView.stateChangedCallback = { ownControllerState = ownControllerState.copy().apply {
			leftX = quantizeStick(it.x)
			leftY = quantizeStick(it.y)
		}}

		binding.rightAnalogStickView.stateChangedCallback = { ownControllerState = ownControllerState.copy().apply {
			rightX = quantizeStick(it.x)
			rightY = quantizeStick(it.y)
		}}

		// Fire & Drag
		binding.fireDragButton.listener = this

		onScreenControlsEnabled?.observe(viewLifecycleOwner, Observer {
			view.visibility = if(it) View.VISIBLE else View.GONE
		})
	}

	private fun dpadStateChanged(direction: DPadView.Direction?)
	{
		ownControllerState = ownControllerState.copy().apply {
			buttons = ((buttons
							and ControllerState.BUTTON_DPAD_LEFT.inv()
							and ControllerState.BUTTON_DPAD_RIGHT.inv()
							and ControllerState.BUTTON_DPAD_UP.inv()
							and ControllerState.BUTTON_DPAD_DOWN.inv())
					or when(direction)
					{
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
			buttons =
				if(pressed)
					buttons or buttonMask
				else
					buttons and buttonMask.inv()

		}
	}

	// === FireDragListener implementation ===

	override fun onHoldStart(rawX: Float, rawY: Float) {
		isAimActive = true
		aimX = 0f
		aimY = 0f
		smoothedX = 0f
		smoothedY = 0f
		lastMoveTime = SystemClock.uptimeMillis()
		// immediately press L2 and R2
		ownControllerState = ownControllerState.copy().apply {
			l2State = 255U
			r2State = 255U
			rightX = 0
			rightY = 0
		}
		ensurePublisherRunning()
	}

	override fun onDrag(dx: Float, dy: Float) {
		if (!isAimActive) return
		// read current sensitivity from preferences
		val ctx = context ?: return
		val prefs = Preferences(ctx)
		val sensitivity = prefs.fireDragSensitivity
		// accumulate aim
		aimX += dx * sensitivity
		aimY += dy * sensitivity
		// clamp
		aimX = aimX.coerceIn(-1f, 1f)
		aimY = aimY.coerceIn(-1f, 1f)
		// update last move time
		lastMoveTime = SystemClock.uptimeMillis()
	}

	override fun onHoldEnd() {
		stopPublisher()
		isAimActive = false
		aimX = 0f
		aimY = 0f
		smoothedX = 0f
		smoothedY = 0f
		// release L2 and R2
		ownControllerState = ownControllerState.copy().apply {
			l2State = 0U
			r2State = 0U
			rightX = 0
			rightY = 0
		}
	}

	private fun ensurePublisherRunning() {
		if (publisherRunnable != null) return
		publisherRunnable = object : Runnable {
			override fun run() {
				if (!isAimActive) return
				val now = SystemClock.uptimeMillis()
				val age = now - lastMoveTime
				if (age > INACTIVITY_TIMEOUT_MS) {
					// send neutral right stick
					smooothedX = 0f
					smoothedY = 0f
					ownControllerState = ownControllerState.copy().apply { rightX = 0.toShort(); rightY = 0.toShort() }
				} else {
					// smoothing towards target normalized aim
					val targetX = aimX.coerceIn(-1f, 1f)
					val targetY = aimY.coerceIn(-1f, 1f)
					smooothedX += (targetX - smoothedX) * FIRE_SMOOTHING
					smoothedY += (targetY - smoothedY) * FIRE_SMOOTHING
					val px = (smoothedX * Short.MAX_VALUE).toInt().toShort()
					val py = (smoothedY * Short.MAX_VALUE).toInt().toShort()
					ownControllerState = ownControllerState.copy().apply { rightX = px; rightY = py }
				}
				motionHandler.postDelayed(this, PUBLISH_INTERVAL_MS)
			}
		}
		motionHandler.post(publisherRunnable!!)
	}

	private fun stopPublisher() {
		publisherRunnable?.let { motionHandler.removeCallbacks(it) }
		publisherRunnable = null
	}
}
