package com.example.data

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.R

class VoiceUIManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceUI"
        private const val BUBBLE_SIZE_DP = 60
        private const val EXPANDED_WIDTH_DP = 280
        private const val EXPANDED_HEIGHT_DP = 120
        private const val ANIMATION_DURATION = 300L
        private const val PULSE_DURATION = 800L
    }

    // ═══════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════

    data class VoiceUIState(
        val isVisible: Boolean = false,
        val isExpanded: Boolean = false,
        val isListening: Boolean = false,
        val partialText: String = "",
        val rmsLevel: Float = 0f,
        val statusText: String = "Tap to speak"
    )

    // ═══════════════════════════════════════════
    // Views
    // ═══════════════════════════════════════════

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubbleView: View? = null
    private var expandedView: View? = null
    private var bubbleContainer: FrameLayout? = null
    private var waveContainer: LinearLayout? = null
    private var textPreview: TextView? = null
    private var statusLabel: TextView? = null
    private var micIcon: ImageView? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isExpanded = false

    private val state = VoiceUIState()

    // Wave animation bars
    private val waveBars = mutableListOf<View>()
    private val waveAnimators = mutableListOf<ValueAnimator>()

    // ═══════════════════════════════════════════
    // Window Layout Params
    // ═══════════════════════════════════════════

    private val bubbleParams: WindowManager.LayoutParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 400
        }
    }

    // ═══════════════════════════════════════════
    // Callbacks
    // ═══════════════════════════════════════════

    var onMicPressed: (() -> Unit)? = null
    var onMicReleased: (() -> Unit)? = null
    var onBubbleTapped: (() -> Unit)? = null
    var onDismissRequested: (() -> Unit)? = null

    // ═══════════════════════════════════════════
    // Show / Hide
    // ═══════════════════════════════════════════

    fun show() {
        mainHandler.post {
            if (bubbleView == null) {
                createBubbleView()
            }
            bubbleView?.visibility = View.VISIBLE
            bubbleView?.alpha = 0f
            bubbleView?.animate()?.alpha(1f)?.setDuration(ANIMATION_DURATION)?.start()
            Log.d(TAG, "🎤 Voice bubble shown")
        }
    }

    fun hide() {
        mainHandler.post {
            bubbleView?.animate()?.alpha(0f)?.setDuration(ANIMATION_DURATION)?.withEndAction {
                bubbleView?.visibility = View.GONE
            }?.start()
            hideExpanded()
            stopWaveAnimation()
            Log.d(TAG, "🎤 Voice bubble hidden")
        }
    }

    fun remove() {
        mainHandler.post {
            try {
                stopWaveAnimation()
                bubbleView?.let { windowManager.removeView(it) }
                expandedView?.let { windowManager.removeView(it) }
                bubbleView = null
                expandedView = null
                Log.d(TAG, "💀 Voice UI destroyed")
            } catch (e: Exception) {
                Log.e(TAG, "Remove error", e)
            }
        }
    }

    // ═══════════════════════════════════════════
    // Update State
    // ═══════════════════════════════════════════

    fun updatePartialText(text: String) {
        mainHandler.post {
            textPreview?.text = text.ifBlank { "Listening..." }
            statusLabel?.text = if (text.isBlank()) "Speak now..." else "Listening..."
        }
    }

    fun updateRMSLevel(level: Float) {
        mainHandler.post {
            val normalizedLevel = (level / 10f).coerceIn(0f, 1f)
            updateWaveBars(normalizedLevel)
        }
    }

    fun setListeningState(isListening: Boolean) {
        mainHandler.post {
            if (isListening) {
                micIcon?.setImageResource(android.R.drawable.ic_btn_speak_now)
                showExpanded()
                startWaveAnimation()
                statusLabel?.text = "Listening..."
            } else {
                micIcon?.setImageResource(android.R.drawable.ic_dialog_dialer)
                hideExpanded()
                stopWaveAnimation()
                statusLabel?.text = "Tap to speak"
            }
        }
    }

    // ═══════════════════════════════════════════
    // Bubble View
    // ═══════════════════════════════════════════

    private fun createBubbleView() {
        val inflater = LayoutInflater.from(context)
        bubbleContainer = inflater.inflate(R.layout.voice_bubble, null) as? FrameLayout

        // Create bubble programmatically (no XML dependency)
        val bubble = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                (BUBBLE_SIZE_DP * context.resources.displayMetrics.density).toInt(),
                (BUBBLE_SIZE_DP * context.resources.displayMetrics.density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF1A73E8.toInt())
                setStroke(2, 0xFFFFFFFF.toInt())
            }
        }

        micIcon = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            setImageResource(android.R.drawable.ic_dialog_dialer)
            setColorFilter(0xFFFFFFFF.toInt())
        }

        bubble.addView(micIcon)
        bubbleContainer = FrameLayout(context)
        bubbleContainer?.addView(bubble)

        // Drag handling
        bubble.setOnTouchListener { view, event ->
            handleBubbleTouch(view, event)
        }

        // Click handling
        bubble.setOnClickListener {
            if (!isDragging) {
                onBubbleTapped?.invoke()
            }
        }

        // Long press → start listening
        bubble.setOnLongClickListener {
            onMicPressed?.invoke()
            true
        }

        bubbleView = bubbleContainer
        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun handleBubbleTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = bubbleParams.x
                initialY = bubbleParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                    isDragging = true
                    bubbleParams.x = initialX + dx
                    bubbleParams.y = initialY + dy
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    // Animate to edge
                    val screenWidth = context.resources.displayMetrics.widthPixels
                    val targetX = if (bubbleParams.x > screenWidth / 2) {
                        screenWidth - (BUBBLE_SIZE_DP * context.resources.displayMetrics.density).toInt() - 16
                    } else 16
                    animateBubbleTo(targetX, bubbleParams.y)
                }
                return true
            }
        }
        return false
    }

    private fun animateBubbleTo(targetX: Int, targetY: Int) {
        ValueAnimator.ofInt(bubbleParams.x, targetX).apply {
            duration = ANIMATION_DURATION
            interpolator = OvershootInterpolator()
            addUpdateListener {
                bubbleParams.x = it.animatedValue as Int
                try { windowManager.updateViewLayout(bubbleView, bubbleParams) } catch (e: Exception) {}
            }
            start()
        }
    }

    // ═══════════════════════════════════════════
    // Expanded View
    // ═══════════════════════════════════════════

    private fun showExpanded() {
        if (isExpanded) return
        isExpanded = true

        mainHandler.post {
            expandedView = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (EXPANDED_WIDTH_DP * context.resources.displayMetrics.density).toInt(),
                    (EXPANDED_HEIGHT_DP * context.resources.displayMetrics.density).toInt()
                )
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24f * context.resources.displayMetrics.density
                    setColor(0xDD1A1A2E.toInt())
                    setStroke(1, 0xFF4A4A7F.toInt())
                }
                setPadding(16, 16, 16, 16)
            }

            // Status label
            statusLabel = TextView(context).apply {
                text = "Listening..."
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
                gravity = Gravity.CENTER
            }

            // Text preview
            textPreview = TextView(context).apply {
                text = ""
                setTextColor(0xFFC0C0E0.toInt())
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 3
            }

            // Wave bars container
            waveContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                (0..6).forEach { _ ->
                    val bar = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(4, 20, 1f).apply {
                            setMargins(2, 0, 2, 0)
                        }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 2f * context.resources.displayMetrics.density
                            setColor(0xFF75B6FF.toInt())
                        }
                    }
                    addView(bar)
                    waveBars.add(bar)
                }
            }

            (expandedView as FrameLayout).apply {
                addView(statusLabel, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; topMargin = 8 })

                addView(textPreview, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER })

                addView(waveContainer, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = 8 })
            }
        }
    }

    private fun hideExpanded() {
        isExpanded = false
        mainHandler.post {
            try {
                expandedView?.let { windowManager.removeView(it) }
                expandedView = null
                waveBars.clear()
            } catch (e: Exception) {}
        }
    }

    // ═══════════════════════════════════════════
    // Wave Animation
    // ═══════════════════════════════════════════

    private fun startWaveAnimation() {
        waveBars.forEachIndexed { index, bar ->
            val animator = ValueAnimator.ofFloat(0.2f, 1f, 0.2f).apply {
                duration = PULSE_DURATION
                repeatCount = ValueAnimator.INFINITE
                startDelay = index * 80L
                addUpdateListener {
                    val scale = it.animatedValue as Float
                    bar.scaleY = scale
                    bar.alpha = 0.3f + scale * 0.7f
                }
                start()
            }
            waveAnimators.add(animator)
        }
    }

    private fun stopWaveAnimation() {
        waveAnimators.forEach { it.cancel() }
        waveAnimators.clear()
    }

    private fun updateWaveBars(level: Float) {
        waveBars.forEachIndexed { index, bar ->
            val offset = (index - 3) * 0.1f
            val scale = (level + offset).coerceIn(0.2f, 1f)
            bar.scaleY = scale
            bar.alpha = 0.3f + scale * 0.7f
        }
    }
}
