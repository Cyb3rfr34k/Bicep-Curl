package com.cyb3rfr34k.bicepcurl.counter

enum class CurlStage(val label: String) {
    UP("up"),
    DOWN("down"),
    UNKNOWN("unknown"),
}

data class ExerciseFeedback(
    val message: String = "Show your upper body to the camera",
    val isPositive: Boolean = false,
)

data class RepCounterState(
    val repCount: Int = 0,
    val elbowAngle: Double? = null,
    val shoulderAngle: Double? = null,
    val leftElbowAngle: Double? = null,
    val rightElbowAngle: Double? = null,
    val leftShoulderAngle: Double? = null,
    val rightShoulderAngle: Double? = null,
    val countingMode: String = "two-arm average",
    val stage: CurlStage = CurlStage.UNKNOWN,
    val feedback: ExerciseFeedback = ExerciseFeedback(),
    val poseDetected: Boolean = false,
    val frameAccepted: Boolean = false,
    val frameStatus: String = "waiting for pose",
    val landmarkConfidence: Float? = null,
    val armAgreementDegrees: Double? = null,
)
