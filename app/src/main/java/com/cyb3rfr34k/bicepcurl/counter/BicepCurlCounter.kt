package com.cyb3rfr34k.bicepcurl.counter

class BicepCurlCounter {
    private var repCount = 0
    private var smoothedElbowAngle: Double? = null
    private var smoothedShoulderAngle: Double? = null
    private var candidateStage = CurlStage.UNKNOWN
    private var candidateStageFrames = 0
    private var stableStage = CurlStage.UNKNOWN
    private var hasReachedCurlTop = false
    private var framesSinceRep = MIN_FRAMES_BETWEEN_REPS
    private var goodRepFramesRemaining = 0

    fun update(
        elbowAngle: Double?,
        shoulderAngle: Double?,
        leftElbowAngle: Double? = elbowAngle,
        leftShoulderAngle: Double? = shoulderAngle,
        rightElbowAngle: Double? = null,
        rightShoulderAngle: Double? = null,
    ): RepCounterState {
        framesSinceRep += 1

        if (elbowAngle == null || shoulderAngle == null) {
            candidateStage = CurlStage.UNKNOWN
            candidateStageFrames = 0
            return currentState(
                elbowAngle = null,
                shoulderAngle = null,
                leftElbowAngle = leftElbowAngle,
                leftShoulderAngle = leftShoulderAngle,
                rightElbowAngle = rightElbowAngle,
                rightShoulderAngle = rightShoulderAngle,
                poseDetected = false,
                feedback = ExerciseFeedback("Show your upper body to the camera"),
            )
        }

        val elbow = smooth(smoothedElbowAngle, elbowAngle).also {
            smoothedElbowAngle = it
        }
        val shoulder = smooth(smoothedShoulderAngle, shoulderAngle).also {
            smoothedShoulderAngle = it
        }

        val detectedStage = classifyStage(elbow, shoulder)
        updateStageCandidate(detectedStage)

        var feedback = feedbackFor(stableStage, shoulder)

        if (candidateStage != CurlStage.UNKNOWN &&
            candidateStageFrames >= STABLE_STAGE_FRAME_COUNT &&
            candidateStage != stableStage
        ) {
            stableStage = candidateStage
            feedback = onStableStageChanged(stableStage)
        }

        if (goodRepFramesRemaining > 0) {
            goodRepFramesRemaining -= 1
            feedback = ExerciseFeedback("Good rep", isPositive = true)
        }

        return currentState(
            elbowAngle = elbow,
            shoulderAngle = shoulder,
            leftElbowAngle = leftElbowAngle,
            leftShoulderAngle = leftShoulderAngle,
            rightElbowAngle = rightElbowAngle,
            rightShoulderAngle = rightShoulderAngle,
            poseDetected = true,
            feedback = feedback,
        )
    }

    fun reset() {
        repCount = 0
        smoothedElbowAngle = null
        smoothedShoulderAngle = null
        candidateStage = CurlStage.UNKNOWN
        candidateStageFrames = 0
        stableStage = CurlStage.UNKNOWN
        hasReachedCurlTop = false
        framesSinceRep = MIN_FRAMES_BETWEEN_REPS
        goodRepFramesRemaining = 0
    }

    private fun smooth(previous: Double?, next: Double): Double {
        return if (previous == null) {
            next
        } else {
            (SMOOTHING_ALPHA * next) + ((1.0 - SMOOTHING_ALPHA) * previous)
        }
    }

    private fun classifyStage(elbowAngle: Double, shoulderAngle: Double): CurlStage {
        if (shoulderAngle < SHOULDER_FORM_THRESHOLD_DEGREES) {
            return CurlStage.UNKNOWN
        }

        return when {
            elbowAngle <= ELBOW_UP_THRESHOLD_DEGREES -> CurlStage.UP
            elbowAngle >= ELBOW_DOWN_THRESHOLD_DEGREES -> CurlStage.DOWN
            else -> CurlStage.UNKNOWN
        }
    }

    private fun updateStageCandidate(stage: CurlStage) {
        if (stage == candidateStage) {
            candidateStageFrames += 1
        } else {
            candidateStage = stage
            candidateStageFrames = 1
        }
    }

    private fun onStableStageChanged(stage: CurlStage): ExerciseFeedback {
        return when (stage) {
            CurlStage.UP -> {
                hasReachedCurlTop = true
                ExerciseFeedback("Lower down")
            }

            CurlStage.DOWN -> {
                if (hasReachedCurlTop && framesSinceRep >= MIN_FRAMES_BETWEEN_REPS) {
                    repCount += 1
                    framesSinceRep = 0
                    goodRepFramesRemaining = GOOD_REP_FEEDBACK_FRAMES
                    hasReachedCurlTop = false
                    ExerciseFeedback("Good rep", isPositive = true)
                } else {
                    ExerciseFeedback("Curl up")
                }
            }

            CurlStage.UNKNOWN -> ExerciseFeedback("Move through a full curl")
        }
    }

    private fun feedbackFor(stage: CurlStage, shoulderAngle: Double): ExerciseFeedback {
        if (shoulderAngle < SHOULDER_FORM_THRESHOLD_DEGREES) {
            return ExerciseFeedback("Keep upper arm steady")
        }

        return when (stage) {
            CurlStage.UP -> ExerciseFeedback("Lower down")
            CurlStage.DOWN -> ExerciseFeedback("Curl up")
            CurlStage.UNKNOWN -> ExerciseFeedback("Move through a full curl")
        }
    }

    private fun currentState(
        elbowAngle: Double?,
        shoulderAngle: Double?,
        leftElbowAngle: Double?,
        leftShoulderAngle: Double?,
        rightElbowAngle: Double?,
        rightShoulderAngle: Double?,
        poseDetected: Boolean,
        feedback: ExerciseFeedback,
    ): RepCounterState {
        return RepCounterState(
            repCount = repCount,
            elbowAngle = elbowAngle,
            shoulderAngle = shoulderAngle,
            leftElbowAngle = leftElbowAngle,
            leftShoulderAngle = leftShoulderAngle,
            rightElbowAngle = rightElbowAngle,
            rightShoulderAngle = rightShoulderAngle,
            countingMode = "two-arm average",
            stage = stableStage,
            feedback = feedback,
            poseDetected = poseDetected,
        )
    }

    companion object {
        // Python reference: np.interp(elbow_angle, (50, 160), (0, 100)).
        const val ELBOW_UP_THRESHOLD_DEGREES = 50.0
        const val ELBOW_DOWN_THRESHOLD_DEGREES = 160.0

        // Python reference: shoulder_angle > 150.
        const val SHOULDER_FORM_THRESHOLD_DEGREES = 150.0

        private const val SMOOTHING_ALPHA = 0.35
        private const val STABLE_STAGE_FRAME_COUNT = 3
        private const val MIN_FRAMES_BETWEEN_REPS = 8
        private const val GOOD_REP_FEEDBACK_FRAMES = 10
    }
}
