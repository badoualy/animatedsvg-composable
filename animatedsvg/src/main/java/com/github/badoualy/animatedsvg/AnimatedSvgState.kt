package com.github.badoualy.animatedsvg

import android.graphics.PathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
    internal var pulse: Int by mutableStateOf(0)
        private set

    private val strokeAnimation = Animatable(0f)
    internal val strokeAnimationValue get() = strokeAnimation.value
    internal var animatedStrokeIndex by mutableStateOf(-1)
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
     * Reset state at the given stroke index
     * @param index stroke index at which to position the cursor
     */
    internal suspend fun resetAnimationState() {
        strokeAnimation.snapTo(0f)
        animatedStrokeIndex = -1
    }

    suspend fun animateStrokes(
        indices: List<Int>,
        strokeMeasures: List<PathMeasure>
    ) {
        indices
            .drop(indices.indexOf(animatedStrokeIndex).takeIf { it >= 0 } ?: 0)
            .forEachIndexed { i, index ->
                if (i > 0) {
                    strokeAnimation.snapTo(0f)
                }
                animatedStrokeIndex = index

                // TODO: when paused, durationMillis should take in account for distance already done
                strokeAnimation.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = (strokeMeasures[index].length * 10).toInt(),
                        delayMillis = if (i == 0) initialDelay else delayBetweenStrokes,
                        easing = FastOutSlowInEasing
                    )
                )
            }
    }
}
