package com.github.badoualy.animatedsvg

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy

@Stable
class AnimationState(
    isRunningInitially: Boolean = false
) {
    var isRunning: Boolean by mutableStateOf(isRunningInitially, structuralEqualityPolicy())
        private set

    fun onAnimationEnd(){
        isRunning = false
    }

    fun restart() {
        isRunning = true
    }
}
