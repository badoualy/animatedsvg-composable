package com.github.badoualy.animatedsvg

import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
 * @param state current animation state (controller used to start/restart/stop animation)
 * @param drawPlaceholder true to draw the placeholder below the animation, to preview the content
 * @param drawPlaceholderHighlights true to draw the placeholder with the highlight color if appliable
 * @param animatedStrokes indices from [strokes] to animate (the rest will be drawn as placeholder if enabled)
 * @param highlightedStrokes indices from [strokes] to highlight
 * @param strokeWidth strokeWidth to apply to the paint drawing the path
 * @param fingerRadius radius of the indicator at the current position (0 to hide)
 * @param color stroke color for the actual strokes being animated
 * @param placeholderColor stroke color for the placeholder strokes
 * @param highlightColor stroke color for highlighted strokes
 * @param fingerColor color of the circular indicator at the current position of the animation
 */
@Composable
fun AnimatedSvg(
    strokes: List<Path>,
    box: RectF,
    modifier: Modifier = Modifier,
    state: AnimatedSvgState = remember { AnimatedSvgState(false) },
    drawPlaceholder: Boolean = true,
    drawPlaceholderHighlights: Boolean = false,
    animatedStrokes: List<Int> = strokes.indices.toList(),
    highlightedStrokes: List<Int> = emptyList(),
    strokeWidth: Dp = 4.dp,
    fingerRadius: Dp = 8.dp,
    color: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
    placeholderColor: Color = color.copy(alpha = 0.25f),
    highlightColor: Color = MaterialTheme.colors.secondary,
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
    val strokeMeasures = remember(strokes) {
        strokes.map { PathMeasure(it.asAndroidPath(), false) }
    }

    // Animation
    // TODO: We might want to use scaledStrokes as key instead
    //  and defer draw/animation until size is computed
    LaunchedEffect(strokes, animatedStrokes, state.pulse) {
        // Reset animation when content or pulse changes (stop or start was called)
        state.resetAnimationState()
    }
    if (state.animate) {
        LaunchedEffect(strokes, animatedStrokes, state.pulse) {
            state.animateStrokes(
                indices = animatedStrokes,
                strokeMeasures = strokeMeasures
            )
        }
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
            .size(DEFAULT_SIZE)
            .aspectRatio(1f)
            .onSizeChanged { currentSize = it }
    ) {
        val animatedStrokeProgress = state.strokeAnimationValue
        val animatedStrokeIndex = state.animatedStrokeIndex
        strokePaint.strokeWidth = strokeWidth.toPx()

        scaledStrokes.forEachIndexed { i, (strokePath, strokeMeasure) ->
            val isAnimatedStroke = i == animatedStrokeIndex && animatedStrokeProgress < 1f

            // Draw placeholder (if not animated, not YET animated or not fully animated)
            // When drawing currently animated stroke, we draw placeholder AND animated path over
            if (drawPlaceholder && (i !in animatedStrokes || i > animatedStrokeIndex || isAnimatedStroke)) {
                strokePaint.color = if (drawPlaceholderHighlights && i in highlightedStrokes) {
                    highlightColor
                } else {
                    placeholderColor
                }
                strokePaint.pathEffect = null
                drawIntoCanvas { canvas -> canvas.drawPath(strokePath, strokePaint) }
            }

            // Stroke won't be animated now, skip
            if (i !in animatedStrokes || i > animatedStrokeIndex) return@forEachIndexed

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
