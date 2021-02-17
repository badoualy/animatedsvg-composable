package com.github.badoualy.animatedsvg

import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toComposePathEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private val DEFAULT_SIZE = 42.dp

/**
 * Canvas based composable that sequentially draws the given paths with a circular filled indicator at the current position
 *
 * @param strokes list of path to animate sequentially (in the given order)
 * @param box bounding box of the underlying SVG
 * @param modifier modifiers applied to the canvas where the svg is drawn on
 * @param animate true if the animation should run, false otherwise
 * @param initialDelay initial delay in ms before the first stroke of the animation starts
 * @param delayBetweenStrokes delay in ms between each animated stroke
 * @param drawPlaceholder true to draw the placeholder below the animation, to preview the content
 * @param animatedStrokes list of indices from [strokes] to animate (the rest will be drawn as placeholder if enabled)
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
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    initialDelay: Int = 250,
    delayBetweenStrokes: Int = 0,
    drawPlaceholder: Boolean = true,
    animatedStrokes: List<Int> = strokes.indices.toList(),
    highlightedStrokes: IntArray = intArrayOf(),
    strokeWidth: Dp = 4.dp,
    color: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
    placeholderColor: Color = LocalContentColor.current.copy(alpha = 0.25f),
    highlightColor: Color = MaterialTheme.colors.secondary,
    fingerRadius: Dp = 8.dp,
    fingerColor: Color = MaterialTheme.colors.primary
) {
    // Build matrix and scaled strokes to fill the viewport
    val defaultSizePx = with(LocalDensity.current) { DEFAULT_SIZE.roundToPx() }
    var currentSize by remember { mutableStateOf(IntSize(defaultSizePx, defaultSizePx)) }
    val scaledStrokes = remember(currentSize) {
        strokes.scale(
            srcBox = box,
            dstBox = RectF(0f, 0f, currentSize.width.toFloat(), currentSize.height.toFloat())
        )
    }

    // Animation declaration
    val strokeMeasures = remember(strokes) {
        strokes.map { PathMeasure(it.asAndroidPath(), false) }
    }
    var transitionState by remember(strokes, animate, animatedStrokes) {
        val initialState = if (animate && animatedStrokes.isNotEmpty()) {
            AnimationState.Start(strokeIndex = animatedStrokes.first(), strokeAnimationIndex = 0)
        } else {
            AnimationState.End(strokeIndex = strokes.lastIndex, strokeAnimationIndex = 0)
        }
        mutableStateOf(MutableTransitionState(initialState))
    }
    val transition = updateTransition(transitionState, "AnimatedSvg")
    val currentState = transitionState.currentState

    // Animation values
    val animatedStrokeIndex = currentState.strokeIndex
    val animatedStrokeAnimationIndex = currentState.strokeAnimationIndex
    val animatedStrokeProgress by transition.animateFloat(
        transitionSpec = {
            if (initialState is AnimationState.Start && targetState is AnimationState.End) {
                tween(
                    durationMillis = (strokeMeasures[initialState.strokeIndex].length * 10).toInt(),
                    delayMillis = if (initialState.strokeAnimationIndex == 0) initialDelay else delayBetweenStrokes,
                    easing = FastOutSlowInEasing
                )
            } else {
                snap()
            }
        }
    ) {
        if (it is AnimationState.Start) 0f else 1f
    }

    // Animation state update
    if (currentState is AnimationState.Start) {
        transitionState.targetState = AnimationState.End(
            strokeIndex = animatedStrokeIndex,
            strokeAnimationIndex = animatedStrokeAnimationIndex
        )
    } else if (currentState is AnimationState.End && animatedStrokeAnimationIndex < animatedStrokes.lastIndex) {
        transitionState.targetState = AnimationState.Start(
            strokeIndex = animatedStrokes[animatedStrokeAnimationIndex + 1],
            strokeAnimationIndex = animatedStrokeAnimationIndex + 1
        )
    }

    // Drawing
    val strokePaint = remember {
        Paint().apply {
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
        }
    }
    val fingerPosition = remember { floatArrayOf(0f, 0f) }
    Canvas(
        modifier = modifier
            .run {
                if (animate && animatedStrokes.isNotEmpty()) {
                    clickable {
                        // Restart animation on click
                        transitionState = MutableTransitionState(
                            AnimationState.Start(
                                strokeIndex = animatedStrokes.first(),
                                strokeAnimationIndex = 0
                            )
                        )
                    }
                } else {
                    this
                }
            }
            .preferredSize(DEFAULT_SIZE)
            .aspectRatio(1f)
            .onSizeChanged { currentSize = it }
    ) {
        strokePaint.strokeWidth = strokeWidth.toPx()

        scaledStrokes.forEachIndexed { i, (strokePath, strokeMeasure) ->
            val isAnimatedStroke = i == animatedStrokeIndex && animatedStrokeProgress < 1f

            // Draw placeholder
            if (drawPlaceholder && (i !in animatedStrokes || i > animatedStrokeIndex || isAnimatedStroke)) {
                strokePaint.color = placeholderColor
                strokePaint.pathEffect = null
                drawIntoCanvas { canvas -> canvas.drawPath(strokePath, strokePaint) }
            }
            if (i > animatedStrokeIndex || i !in animatedStrokes) return@forEachIndexed

            // Draw animated stroke or full stroke
            strokePaint.color = if (i in highlightedStrokes) highlightColor else color
            strokePaint.pathEffect = if (isAnimatedStroke) {
                val length = strokeMeasure.length
                DashPathEffect(
                    floatArrayOf(length, length),
                    (1f - animatedStrokeProgress) * length
                ).toComposePathEffect()
            } else {
                null
            }
            drawIntoCanvas { canvas -> canvas.drawPath(strokePath, strokePaint) }

            // Draw finger
            val fingerRadiusPx = fingerRadius.toPx()
            if (isAnimatedStroke && animatedStrokeProgress > 0.0f && fingerRadiusPx > 0) {
                strokeMeasure.getPosTan(
                    strokeMeasure.length * animatedStrokeProgress,
                    fingerPosition,
                    null
                )
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

private sealed class AnimationState {

    /** Index of the stroke in the input [Path] list */
    abstract val strokeIndex: Int

    /**
     * Index of the stroke in the animation. It can differ from [strokeIndex] if we're animating
     * only a subset of the input [Path] list
     */
    abstract val strokeAnimationIndex: Int

    data class Start(
        override val strokeIndex: Int,
        override val strokeAnimationIndex: Int = strokeIndex
    ) : AnimationState()

    data class End(
        override val strokeIndex: Int,
        override val strokeAnimationIndex: Int = strokeIndex
    ) : AnimationState()
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
        val strokes = remember { svg.lines().map { SvgHelper.buildPath(it).asComposePath() } }
        AnimatedSvg(
            strokes = strokes,
            box = RectF(0f, 0f, 109f, 109f),
            modifier = Modifier.fillMaxSize()
        )
    }
}
