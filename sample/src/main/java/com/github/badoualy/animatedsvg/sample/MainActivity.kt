package com.github.badoualy.animatedsvg.sample

import android.graphics.RectF
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposePath
import com.github.badoualy.animatedsvg.AnimatedSvg
import com.github.badoualy.animatedsvg.AnimatedSvgState
import com.github.badoualy.animatedsvg.SvgHelper

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colors.background) {
                    AnimatedSvgComposableSample()
                }
            }
        }
    }
}

@Composable
fun AnimatedSvgComposableSample() {
    // 月
    val svg = """
    M34.25,16.25c1,1,1.48,2.38,1.5,4c0.38,33.62,2.38,59.38-11,73.25
    M36.25,19c4.12-0.62,31.49-4.78,33.25-5c4-0.5,5.5,1.12,5.5,4.75c0,2.76-0.5,49.25-0.5,69.5c0,13-6.25,4-8.75,1.75
    M37.25,38c10.25-1.5,27.25-3.75,36.25-4.5
    M37,58.25c8.75-1.12,27-3.5,36.25-4
    """.trimIndent()

    val strokes = remember { svg.lines().map { SvgHelper.buildPath(it).asComposePath() } }
    val state = remember { AnimatedSvgState(animate = false) }
    var step by remember { mutableStateOf(0) }
    AnimatedSvg(
        strokes = strokes,
        box = RectF(0f, 0f, 109f, 109f),
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = {
                when (step) {
                    0 -> state.start()
                    1 -> state.pause()
                    2 -> state.resume()
                    3 -> state.stop()
                }
                step = (step + 1) % 4
            }),
        state = state
    )
}
