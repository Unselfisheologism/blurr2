package com.blurr.voice.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.blurr.voice.R

class DeltaSymbolView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var glowAnimator: ValueAnimator? = null
    // Convert 6dp to pixels for stroke width
    private val strokeWidthPx = 6f * resources.displayMetrics.density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // 1. Fixed: Changed style from FILL to STROKE for an outline effect
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = ContextCompat.getColor(context, R.color.delta_idle)
        strokeJoin = Paint.Join.ROUND // For smooth corners like in the CSS
    }

    private val path = Path()

    // Variable to hold the current radius since it can't be read from the Paint object
    private var currentGlowRadius = MIN_GLOW_RADIUS

    // Constants for the new programmatic animation
    private companion object {
        const val ANIMATION_DURATION = 1500L // 1.5s is half a cycle, total 3s
        const val MIN_GLOW_RADIUS = 10f
        const val MAX_GLOW_RADIUS = 30f
        const val START_SCALE = 1.0f
        const val END_SCALE = 1.05f
    }

    init {
        // 2. Fixed: Enabled software layer, which is necessary for the shadow/glow effect
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        path.reset()
        val width = w.toFloat()
        val height = h.toFloat()

        // Add padding to prevent the stroke and glow from being cut off at the edges
        val padding = strokeWidthPx / 2f + MAX_GLOW_RADIUS
        val topY = padding
        val bottomY = height - padding
        val leftX = padding
        val rightX = width - padding
        val midX = width / 2f

        path.moveTo(midX, topY)
        path.lineTo(leftX, bottomY)
        path.lineTo(rightX, bottomY)
        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // The paint object now contains the shadow layer (glow), so we just draw the path
        canvas.drawPath(path, paint)
    }

    fun setColor(color: Int) {
        paint.color = color
        // If the animation is running, update the glow color as well
        if (glowAnimator?.isRunning == true) {
            // FIX: Use our own state variable 'currentGlowRadius' to get the radius.
            paint.setShadowLayer(currentGlowRadius, 0f, 0f, color)
        }
        invalidate() // Redraw the view with the new color
    }

    /**
     * Starts the glowing animation programmatically.
     */
    fun startGlow() {
        if (glowAnimator?.isRunning == true) {
            return // Avoid restarting the animation
        }

        // 3. Replaced XML animation with a more powerful ValueAnimator
        glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE

            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float

                // Animate the glow radius using setShadowLayer
                val glowRadius = MIN_GLOW_RADIUS + (MAX_GLOW_RADIUS - MIN_GLOW_RADIUS) * fraction
                currentGlowRadius = glowRadius // Store the current radius
                paint.setShadowLayer(glowRadius, 0f, 0f, paint.color)

                // Animate the scale
                val scale = START_SCALE + (END_SCALE - START_SCALE) * fraction
                scaleX = scale
                scaleY = scale

                invalidate() // Redraw the view on each animation frame
            }
        }
        glowAnimator?.start()
    }

    /**
     * Stops the glowing animation.
     */
    fun stopGlow() {
        glowAnimator?.cancel()
        glowAnimator = null

        // Reset the view to its default state
        paint.clearShadowLayer()
        scaleX = 1.0f
        scaleY = 1.0f
        currentGlowRadius = MIN_GLOW_RADIUS // Reset the radius
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clean up animator to prevent memory leaks when the view is destroyed
        stopGlow()
    }
}

