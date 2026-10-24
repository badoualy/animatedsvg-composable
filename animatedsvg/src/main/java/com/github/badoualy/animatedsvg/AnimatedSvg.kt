package com.github.badoualy.animatedsvg

import android.graphics.Matrix
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

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
 * @param animatedStrokes indices from [strokes] to animate, in animation order (the rest will be drawn as placeholder if enabled)
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
    color: Color,
    placeholderColor: Color = color.copy(alpha = 0.25f),
    highlightColor: Color,
    fingerColor: Color,
) {
    val strokeLengths = remember(strokes) { strokes.map { it.asAndroidPath().length() } }
    val scaledStrokesCache = remember(strokes, box) { ScaledStrokesCache(strokes, box) }
    val animatedStrokesPositions = remember(animatedStrokes) {
        animatedStrokes.withIndex().associate { (position, index) -> index to position }
    }
    val highlightedStrokesSet = remember(highlightedStrokes) { highlightedStrokes.toSet() }

    // Animation
    LaunchedEffect(strokes, animatedStrokes, state.pulse) {
        // Reset animation when content or pulse changes (stop or start was called)
        state.resetAnimationState()
        snapshotFlow { state.animate }.collectLatest { animate ->
            if (animate) {
                state.animateStrokes(
                    indices = animatedStrokes,
                    strokeLengths = strokeLengths
                )
            }
        }
    }

    // Drawing
    val strokePaint = remember {
        Paint().apply {
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
        }
    }
    val animatedSegment = remember { Path() }
    val fingerPosition = remember { FloatArray(2) }
    Canvas(
        modifier = modifier
            .size(DEFAULT_SIZE)
            .aspectRatio(1f)
    ) {
        // Scaling is done at draw time to always use the actual canvas size, even on the first frame
        val scaledStrokes = scaledStrokesCache.get(size)
        if (scaledStrokes.isEmpty()) return@Canvas

        val animatedStrokeProgress = state.strokeAnimationValue
        val animatedStrokePosition = animatedStrokesPositions[state.animatedStrokeIndex] ?: -1
        val fingerRadiusPx = fingerRadius.toPx()
        strokePaint.strokeWidth = strokeWidth.toPx()

        drawIntoCanvas { canvas ->
            scaledStrokes.forEachIndexed { i, stroke ->
                val position = animatedStrokesPositions[i]
                val isAnimatedStroke = position != null &&
                    position == animatedStrokePosition &&
                    animatedStrokeProgress < 1f
                val isDrawnStroke = position != null && position <= animatedStrokePosition && !isAnimatedStroke

                // Draw placeholder (if not animated, not YET animated or not fully animated)
                // When drawing currently animated stroke, we draw placeholder AND animated path over
                if (drawPlaceholder && !isDrawnStroke) {
                    strokePaint.color = if (drawPlaceholderHighlights && i in highlightedStrokesSet) {
                        highlightColor
                    } else {
                        placeholderColor
                    }
                    canvas.drawPath(stroke.path, strokePaint)
                }

                strokePaint.color = if (i in highlightedStrokesSet) highlightColor else color
                if (isDrawnStroke) {
                    canvas.drawPath(stroke.path, strokePaint)
                } else if (isAnimatedStroke && animatedStrokeProgress > 0f && stroke.contours.isNotEmpty()) {
                    // Extract the animated part of the stroke, contour by contour
                    var remainingLength = stroke.length * animatedStrokeProgress
                    var lastContour = stroke.contours.first()
                    var lastContourLength = 0f
                    animatedSegment.rewind()
                    for (contour in stroke.contours) {
                        lastContour = contour
                        lastContourLength = minOf(remainingLength, contour.length)
                        contour.getSegment(0f, lastContourLength, animatedSegment.asAndroidPath(), true)
                        remainingLength -= lastContourLength
                        if (remainingLength <= 0f) break
                    }
                    canvas.drawPath(animatedSegment, strokePaint)

                    // Draw finger
                    if (fingerRadiusPx > 0f) {
                        lastContour.getPosTan(lastContourLength, fingerPosition, null)
                        drawCircle(
                            color = fingerColor,
                            radius = fingerRadiusPx,
                            center = Offset(fingerPosition[0], fingerPosition[1])
                        )
                    }
                }
            }
        }
    }
}

private class ScaledStroke(
    val path: Path,
    /** measures of each contour of [path], only contours with a length > 0 are kept */
    val contours: List<PathMeasure>,
) {
    val length = contours.sumOf { it.length.toDouble() }.toFloat()
}

/**
 * Caches the strokes scaled for the last drawn size, to avoid recomputing them on every frame
 */
private class ScaledStrokesCache(private val strokes: List<Path>, private val box: RectF) {

    private var size = Size.Unspecified
    private var scaledStrokes = emptyList<ScaledStroke>()

    fun get(size: Size): List<ScaledStroke> {
        if (size != this.size) {
            this.size = size
            scaledStrokes = if (size.isEmpty() || box.isEmpty) {
                emptyList()
            } else {
                strokes.scale(srcBox = box, dstBox = RectF(0f, 0f, size.width, size.height))
            }
        }
        return scaledStrokes
    }
}

/**
 * Scales the list of path by applying the result of [Matrix.setRectToRect] function on each path
 */
private fun List<Path>.scale(srcBox: RectF, dstBox: RectF): List<ScaledStroke> {
    // Compute matrix
    val matrix = Matrix().apply {
        setRectToRect(
            srcBox,
            dstBox,
            Matrix.ScaleToFit.CENTER
        )
    }

    return map { strokePath ->
        val path = android.graphics.Path()
        strokePath.asAndroidPath().transform(matrix, path)
        ScaledStroke(path = path.asComposePath(), contours = path.contours())
    }
}

/** Splits this path into one [PathMeasure] per non-empty contour */
private fun android.graphics.Path.contours(): List<PathMeasure> {
    val measure = PathMeasure(this, false)
    val contours = mutableListOf<PathMeasure>()
    do {
        val length = measure.length
        if (length > 0f) {
            val contour = android.graphics.Path()
            measure.getSegment(0f, length, contour, true)
            contours += PathMeasure(contour, false)
        }
    } while (measure.nextContour())
    return contours
}

/** Total length of all the contours of this path */
private fun android.graphics.Path.length(): Float {
    val measure = PathMeasure(this, false)
    var length = 0f
    do {
        length += measure.length
    } while (measure.nextContour())
    return length
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

    Box(modifier = Modifier.background(Color.Black)) {
        val strokes = remember { svg.lines().map { SvgHelper.buildPath(it).asComposePath() } }
        AnimatedSvg(
            strokes = strokes,
            box = RectF(0f, 0f, 109f, 109f),
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
            highlightColor = Color.Yellow,
            fingerColor = Color.Cyan,
        )
    }
}
