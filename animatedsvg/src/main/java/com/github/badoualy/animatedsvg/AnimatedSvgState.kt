package com.github.badoualy.animatedsvg

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy

@Stable
class AnimatedSvgState(
    animate: Boolean = false
) {
    var animate: Boolean by mutableStateOf(animate, structuralEqualityPolicy())
        private set
    var pulse: Int by mutableStateOf(0, structuralEqualityPolicy())
        private set

    fun start() {
        restart()
    }

    fun restart() {
        pulse++
        animate = true
    }
}
