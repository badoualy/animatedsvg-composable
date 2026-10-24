package com.github.badoualy.animatedsvg

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/** Animation duration in ms per unit of stroke length (in the SVG coordinate space) */
private const val DURATION_PER_LENGTH_UNIT = 10f

@Stable
class AnimatedSvgState(
    animate: Boolean = false,
    /** initial delay in ms before the first stroke of the animation starts */
    private val initialDelay: Int = 250,
    /** delay in ms between each animated stroke */
    private val delayBetweenStrokes: Int = 0
) {
    var animate: Boolean by mutableStateOf(animate)
        private set
    internal var pulse: Int by mutableIntStateOf(0)
        private set

    private val strokeAnimation = Animatable(0f)
    internal val strokeAnimationValue get() = strokeAnimation.value
    internal var animatedStrokeIndex by mutableIntStateOf(-1)
        private set

    /**
     * Start animating the strokes. Calling this function will restart the animation if already running.
     */
    fun start() {
        pulse++
        animate = true
    }

    /**
     * Pause the animation, conserving current state (can be resumed via [resume]
     */
    fun pause() {
        animate = false
    }

    /**
     * Resume a previously paused (via [pause]) animation from where it stopped
     */
    fun resume() {
        animate = true
    }

    /**
     * Stop the animation and reset animation state
     */
    fun stop() {
        animate = false
        pulse++
    }

    /**
     * Reset state before the first stroke
     */
    internal suspend fun resetAnimationState() {
        strokeAnimation.snapTo(0f)
        animatedStrokeIndex = -1
    }

    /**
     * Sequentially animate the given strokes, resuming from the current state if the animation was paused
     *
     * @param indices indices of the strokes to animate, in animation order
     * @param strokeLengths length of every stroke, used to compute each stroke animation duration
     */
    internal suspend fun animateStrokes(
        indices: List<Int>,
        strokeLengths: List<Float>
    ) {
        val startPosition = indices.indexOf(animatedStrokeIndex).coerceAtLeast(0)
        for (position in startPosition until indices.size) {
            val index = indices[position]
            if (index != animatedStrokeIndex) {
                strokeAnimation.snapTo(0f)
                animatedStrokeIndex = index
            }

            // When resuming a paused stroke, only animate the remaining part, without delay
            val remainingProgress = 1f - strokeAnimation.value
            strokeAnimation.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = (strokeLengths[index] * DURATION_PER_LENGTH_UNIT * remainingProgress).roundToInt(),
                    delayMillis = when {
                        remainingProgress < 1f -> 0
                        position == 0 -> initialDelay
                        else -> delayBetweenStrokes
                    },
                    easing = FastOutSlowInEasing
                )
            )
        }
    }
}
