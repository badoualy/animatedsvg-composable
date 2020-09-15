package com.github.badoualy.animatedsvg

import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatPropKey
import androidx.compose.animation.core.IntPropKey
import androidx.compose.animation.core.TransitionDefinition
import androidx.compose.animation.core.transitionDefinition
import androidx.compose.animation.core.tween
import androidx.compose.animation.transition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.contentColor
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.drawCanvas
import androidx.compose.ui.onPositioned
import androidx.compose.ui.platform.DensityAmbient
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.ui.tooling.preview.Preview

private val StrokeIndex = IntPropKey()
private val StrokeProgress = FloatPropKey()

private val DEFAULT_SIZE = 42.dp

/**
 * Canvas based composable that sequentially draws the given paths with a circular filled indicator at the current position
 *
 * @param strokes list of path to animate sequentially (in the given order)
 * @param box bounding box of the underlying SVG
 * @param modifier modifiers applied to the canvas where the svg is drawn on
 * @param animationState state of the animation, isRunning is automatically set to false when animation ends
 * @param drawPlaceholder true to draw the placeholder below the animation, to preview the content
 * @param highlightedStrokes list of stroke index to highlight
 * @param strokeWidth strokeWidth to apply to the paint drawing the path
 * @param color stroke color for the actual strokes being animated
 * @param placeholderColor stroke color for the placeholder strokes
 * @param highlightColor stroke color for highlighted strokes
 * @param fingerRadius radius of the indicator at the current position (0 to hide)
 * @param fingerColor color of the circular indicator at the current position of the animation
 */
@Composable
fun AnimatedSvg(
    strokes: List<Path>,
    box: RectF,
    modifier: Modifier = Modifier.preferredSize(DEFAULT_SIZE),
    animationState: AnimationState = remember { AnimationState(false) },
    drawPlaceholder: Boolean = true,
    highlightedStrokes: IntArray = intArrayOf(),
    strokeWidth: Dp = 4.dp,
    color: Color = contentColor(),
    placeholderColor: Color = contentColor().copy(alpha = 0.25f),
    highlightColor: Color = MaterialTheme.colors.secondary,
    fingerRadius: Dp = 8.dp,
    fingerColor: Color = MaterialTheme.colors.primary
) {
    val strokePaint = remember {
        Paint().apply {
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
        }
    }
    val fingerPosition = remember { floatArrayOf(0f, 0f) }

    // Build matrix and scaled strokes to fill the viewport
    val defaultSizePx = with(DensityAmbient.current) { DEFAULT_SIZE.toIntPx() }
    var currentSize by remember { mutableStateOf(IntSize(defaultSizePx, defaultSizePx)) }
    val scaledStrokes = remember(currentSize) {
        strokes.scale(
            srcBox = box,
            dstBox = RectF(0f, 0f, currentSize.width.toFloat(), currentSize.height.toFloat())
        )
    }

    // Animation
    val definition = remember {
        buildTransition(
            pathMeasureList = strokes.map { PathMeasure(it.asAndroidPath(), false) }
        )
    }
    val state = if (animationState.isRunning) {
        transition(
            definition = definition,
            initState = AnimatedSvgState.START(0),
            toState = AnimatedSvgState.END(0)
        )
    } else {
        definition.getStateFor(AnimatedSvgState.END(strokes.lastIndex))
    }

    // Drawing
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .onPositioned { currentSize = it.size }
    ) {
        strokePaint.strokeWidth = strokeWidth.toPx()

        val animatedStrokeIndex = state[StrokeIndex]
        val progress = state[StrokeProgress]

        if (animationState.isRunning && animatedStrokeIndex == strokes.lastIndex && progress == 1.0f) {
            // Since we're using the nextState property to chain strokes, we can't currently
            // use the onStateChangeFinished callback
            // Once a better API is available to chain state transition, update this
            animationState.onAnimationEnd()
        }

        scaledStrokes.forEachIndexed { i, (strokePath, strokeMeasure) ->
            val isAnimatedStroke = i == animatedStrokeIndex && progress < 1f

            // Draw placeholder
            if (drawPlaceholder && (i > animatedStrokeIndex || isAnimatedStroke)) {
                strokePaint.color = placeholderColor
                strokePaint.nativePathEffect = null
                drawCanvas { canvas, _ -> canvas.drawPath(strokePath, strokePaint) }
            }
            if (i > animatedStrokeIndex) return@forEachIndexed

            // Draw animated stroke or full stroke
            strokePaint.color = if (i in highlightedStrokes) highlightColor else color
            strokePaint.nativePathEffect = if (isAnimatedStroke) {
                val length = strokeMeasure.length
                DashPathEffect(
                    floatArrayOf(length, length),
                    (1f - progress) * length
                )
            } else {
                null
            }
            drawCanvas { canvas, _ -> canvas.drawPath(strokePath, strokePaint) }

            // Draw finger
            val fingerRadiusPx = fingerRadius.toPx()
            if (isAnimatedStroke && progress > 0.0f && fingerRadiusPx > 0) {
                strokeMeasure.getPosTan(strokeMeasure.length * progress, fingerPosition, null)
                drawCircle(
                    color = fingerColor,
                    radius = fingerRadiusPx,
                    center = Offset(fingerPosition[0], fingerPosition[1])
                )
            }
        }
    }
}

/**
 * Scales the list of path by applying the result of [Matrix.setRectToRect] function on each path
 */
private fun List<Path>.scale(srcBox: RectF, dstBox: RectF): List<Pair<Path, PathMeasure>> {
    // Compute matrix
    val matrix = Matrix().apply {
        setRectToRect(
            srcBox,
            dstBox,
            Matrix.ScaleToFit.CENTER
        )
    }

    return map { strokePath ->
        // asAndroidPath is returning the backing property, so we can update it directly
        val path = Path().apply {
            strokePath.asAndroidPath().transform(matrix, asAndroidPath())
        }
        val measure = PathMeasure(path.asAndroidPath(), false)
        path to measure
    }
}

private sealed class AnimatedSvgState {
    data class START(val strokeIndex: Int) : AnimatedSvgState()
    data class END(val strokeIndex: Int) : AnimatedSvgState()
}

/**
 * Builds a transition definition for the given path measures.
 * Animations will be played sequentially.
 * The animation duration is proportional to the path's length, with a formula tested and approved.
 * Each stroke is animated with its own interpolator.
 *
 * Each stroke has a [AnimatedSvgState.START] and [AnimatedSvgState.END] state.
 * At the end of start to end transition, we execute a snap transition from end(i) to start(i+1)
 * to trigger the next stroke state transition.
 */
private fun buildTransition(
    pathMeasureList: List<PathMeasure>,
    initialDelay: Int = 250,
    delayBetweenStrokes: Int = 0
): TransitionDefinition<AnimatedSvgState> {
    return transitionDefinition {
        pathMeasureList.forEachIndexed { i, pathMeasure ->
            val startState = AnimatedSvgState.START(i)
            state(startState) {
                this[StrokeIndex] = i
                this[StrokeProgress] = 0f
            }
            val endState = AnimatedSvgState.END(i)
            state(endState) {
                this[StrokeIndex] = i
                this[StrokeProgress] = 1f
            }

            val isLastPath = i == pathMeasureList.lastIndex
            transition(startState to endState) {
                StrokeProgress using tween(
                    delayMillis = if (i == 0) initialDelay else delayBetweenStrokes,
                    durationMillis = (pathMeasure.length * 10L).toInt(), // Panoramix' formula
                    easing = FastOutSlowInEasing
                )
                if (!isLastPath) {
                    // Chain
                    nextState = AnimatedSvgState.START(i + 1)
                }
            }

            if (!isLastPath) {
                snapTransition(
                    endState to AnimatedSvgState.START(i + 1),
                    nextState = AnimatedSvgState.END(i + 1)
                )
            }
        }
    }
}

@Preview(widthDp = 42, heightDp = 42, showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AnimatedSvgPreview() {
    // 月
    val svg = """
    M34.25,16.25c1,1,1.48,2.38,1.5,4c0.38,33.62,2.38,59.38-11,73.25
    M36.25,19c4.12-0.62,31.49-4.78,33.25-5c4-0.5,5.5,1.12,5.5,4.75c0,2.76-0.5,49.25-0.5,69.5c0,13-6.25,4-8.75,1.75
    M37.25,38c10.25-1.5,27.25-3.75,36.25-4.5
    M37,58.25c8.75-1.12,27-3.5,36.25-4
    """.trimIndent()

    MaterialTheme {
        val strokes = remember { svg.lines().map { SVGHelper.buildPath(it).asComposePath() } }
        AnimatedSvg(
            strokes = strokes,
            box = RectF(0f, 0f, 109f, 109f),
            modifier = Modifier.fillMaxSize()
        )
    }
}
