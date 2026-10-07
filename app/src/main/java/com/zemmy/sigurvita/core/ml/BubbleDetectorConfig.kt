package com.zemmy.sigurvita.core.ml

data class BubbleDetectorConfig(
    val confThreshold: Float = 0.10f,
    val iouThreshold: Float = 0.70f,
    val maxDetections: Int = 50,
    val maskThreshold: Float = 0.5f
)
