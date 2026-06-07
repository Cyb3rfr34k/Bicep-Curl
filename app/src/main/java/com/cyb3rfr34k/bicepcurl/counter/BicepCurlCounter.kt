package com.cyb3rfr34k.bicepcurl.counter

import kotlin.math.abs

class BicepCurlCounter {
    private var repCount = 0
    private var smoothedElbowAngle: Double? = null
    private var smoothedShoulderAngle: Double? = null
    private var candidatePosition = CurlPosition.UNKNOWN
    private var candidatePositionFrames = 0
    private var stablePosition = CurlPosition.UNKNOWN
    private var phase = CurlPhase.WAITING_FOR_BOTTOM
    private var previousAcceptedAngle: Double? = null
    private var framesSinceRep = MIN_FRAMES_BETWEEN_REPS
    private var framesSinceTop = MIN_FRAMES_AFTER_TOP_BEFORE_REP
    private var rejectedFrameStreak = 0
    private var goodRepFramesRemaining = 0

    fun update(
        elbowAngle: Double?,
        shoulderAngle: Double?,
        leftElbowAngle: Double? = elbowAngle,
        leftShoulderAngle: Double? = shoulderAngle,
        rightElbowAngle: Double? = null,
        rightShoulderAngle: Double? = null,
        landmarkConfidence: Float? = null,
        armAgreementDegrees: Double? = calculateArmAgreement(leftElbowAngle, rightElbowAngle),
        poseDetected: Boolean = elbowAngle != null && shoulderAngle != null,
        frameStatus: String? = null,
    ): RepCounterState {
        framesSinceRep += 1
        framesSinceTop += 1

        if (elbowAngle == null || shoulderAngle == null) {
            return rejectedState(
                elbowAngle = null,
                shoulderAngle = null,
                leftElbowAngle = leftElbowAngle,
                leftShoulderAngle = leftShoulderAngle,
                rightElbowAngle = rightElbowAngle,
                rightShoulderAngle = rightShoulderAngle,
                poseDetected = poseDetected,
                frameStatus = frameStatus ?: "missing curl landmarks",
                landmarkConfidence = landmarkConfidence,
                armAgreementDegrees = armAgreementDegrees,
                feedback = ExerciseFeedback("Show both arms and upper body"),
            )
        }

        if (landmarkConfidence != null && landmarkConfidence < MIN_LANDMARK_CONFIDENCE) {
            return rejectedState(
                elbowAngle = smoothedElbowAngle,
                shoulderAngle = smoothedShoulderAngle,
                leftElbowAngle = leftElbowAngle,
                leftShoulderAngle = leftShoulderAngle,
                rightElbowAngle = rightElbowAngle,
                rightShoulderAngle = rightShoulderAngle,
                poseDetected = poseDetected,
                frameStatus = "low landmark confidence",
                landmarkConfidence = landmarkConfidence,
                armAgreementDegrees = armAgreementDegrees,
                feedback = ExerciseFeedback("Keep both arms visible"),
            )
        }

        if (armAgreementDegrees != null && armAgreementDegrees > MAX_ARM_ANGLE_DIFFERENCE_DEGREES) {
            return rejectedState(
                elbowAngle = smoothedElbowAngle,
                shoulderAngle = smoothedShoulderAngle,
                leftElbowAngle = leftElbowAngle,
                leftShoulderAngle = leftShoulderAngle,
                rightElbowAngle = rightElbowAngle,
                rightShoulderAngle = rightShoulderAngle,
                poseDetected = poseDetected,
                frameStatus = "arms out of sync",
                landmarkConfidence = landmarkConfidence,
                armAgreementDegrees = armAgreementDegrees,
                feedback = ExerciseFeedback("Move both arms together"),
            )
        }

        val elbow = smooth(smoothedElbowAngle, elbowAngle).also {
            smoothedElbowAngle = it
        }
        val shoulder = smooth(smoothedShoulderAngle, shoulderAngle).also {
            smoothedShoulderAngle = it
        }

        if (shoulder < SHOULDER_FORM_THRESHOLD_DEGREES) {
            return rejectedState(
                elbowAngle = elbow,
                shoulderAngle = shoulder,
                leftElbowAngle = leftElbowAngle,
                leftShoulderAngle = leftShoulderAngle,
                rightElbowAngle = rightElbowAngle,
                rightShoulderAngle = rightShoulderAngle,
                poseDetected = poseDetected,
                frameStatus = "upper arms moving too much",
                landmarkConfidence = landmarkConfidence,
                armAgreementDegrees = armAgreementDegrees,
                feedback = ExerciseFeedback("Keep upper arms steady"),
            )
        }

        rejectedFrameStreak = 0
        updateMovementPhase(elbow)

        val detectedPosition = classifyPosition(elbow)
        updatePositionCandidate(detectedPosition)

        var feedback = feedbackForPhase()
        if (candidatePositionFrames >= STABLE_POSITION_FRAME_COUNT &&
            candidatePosition != stablePosition
        ) {
            stablePosition = candidatePosition
            feedback = onStablePositionChanged(stablePosition)
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
            poseDetected = poseDetected,
            frameAccepted = true,
            frameStatus = phase.label,
            landmarkConfidence = landmarkConfidence,
            armAgreementDegrees = armAgreementDegrees,
            feedback = feedback,
        )
    }

    fun reset() {
        repCount = 0
        resetTrackingState()
        framesSinceRep = MIN_FRAMES_BETWEEN_REPS
        goodRepFramesRemaining = 0
    }

    private fun rejectedState(
        elbowAngle: Double?,
        shoulderAngle: Double?,
        leftElbowAngle: Double?,
        leftShoulderAngle: Double?,
        rightElbowAngle: Double?,
        rightShoulderAngle: Double?,
        poseDetected: Boolean,
        frameStatus: String,
        landmarkConfidence: Float?,
        armAgreementDegrees: Double?,
        feedback: ExerciseFeedback,
    ): RepCounterState {
        clearPositionCandidate()
        rejectedFrameStreak += 1

        if (rejectedFrameStreak >= REJECTED_FRAME_RESET_COUNT) {
            resetTrackingState()
        }

        return currentState(
            elbowAngle = elbowAngle,
            shoulderAngle = shoulderAngle,
            leftElbowAngle = leftElbowAngle,
            leftShoulderAngle = leftShoulderAngle,
            rightElbowAngle = rightElbowAngle,
            rightShoulderAngle = rightShoulderAngle,
            poseDetected = poseDetected,
            frameAccepted = false,
            frameStatus = frameStatus,
            landmarkConfidence = landmarkConfidence,
            armAgreementDegrees = armAgreementDegrees,
            feedback = feedback,
        )
    }

    private fun smooth(previous: Double?, next: Double): Double {
        return if (previous == null) {
            next
        } else {
            (SMOOTHING_ALPHA * next) + ((1.0 - SMOOTHING_ALPHA) * previous)
        }
    }

    private fun classifyPosition(elbowAngle: Double): CurlPosition {
        return when {
            elbowAngle <= ELBOW_UP_THRESHOLD_DEGREES -> CurlPosition.TOP
            elbowAngle >= ELBOW_DOWN_THRESHOLD_DEGREES -> CurlPosition.BOTTOM
            else -> CurlPosition.MID
        }
    }

    private fun updatePositionCandidate(position: CurlPosition) {
        if (position == candidatePosition) {
            candidatePositionFrames += 1
        } else {
            candidatePosition = position
            candidatePositionFrames = 1
        }
    }

    private fun updateMovementPhase(currentAngle: Double) {
        val previousAngle = previousAcceptedAngle
        previousAcceptedAngle = currentAngle

        if (previousAngle == null) {
            return
        }

        val delta = currentAngle - previousAngle
        if (abs(delta) < MOVEMENT_DELTA_THRESHOLD_DEGREES) {
            return
        }

        when {
            delta < 0.0 && phase == CurlPhase.READY_AT_BOTTOM -> {
                phase = CurlPhase.CURLING_UP
            }

            delta > 0.0 && phase == CurlPhase.AT_TOP -> {
                phase = CurlPhase.LOWERING_DOWN
            }
        }
    }

    private fun onStablePositionChanged(position: CurlPosition): ExerciseFeedback {
        return when (position) {
            CurlPosition.BOTTOM -> onStableBottom()
            CurlPosition.TOP -> onStableTop()
            CurlPosition.MID,
            CurlPosition.UNKNOWN,
            -> feedbackForPhase()
        }
    }

    private fun onStableBottom(): ExerciseFeedback {
        if (phase == CurlPhase.AT_TOP || phase == CurlPhase.LOWERING_DOWN) {
            phase = CurlPhase.READY_AT_BOTTOM

            if (framesSinceTop >= MIN_FRAMES_AFTER_TOP_BEFORE_REP &&
                framesSinceRep >= MIN_FRAMES_BETWEEN_REPS
            ) {
                repCount += 1
                framesSinceRep = 0
                goodRepFramesRemaining = GOOD_REP_FEEDBACK_FRAMES
                return ExerciseFeedback("Good rep", isPositive = true)
            }
        } else {
            phase = CurlPhase.READY_AT_BOTTOM
        }

        return ExerciseFeedback("Curl up")
    }

    private fun onStableTop(): ExerciseFeedback {
        if (phase == CurlPhase.READY_AT_BOTTOM ||
            phase == CurlPhase.CURLING_UP ||
            phase == CurlPhase.LOWERING_DOWN
        ) {
            phase = CurlPhase.AT_TOP
            framesSinceTop = 0
            return ExerciseFeedback("Lower down")
        }

        return ExerciseFeedback("Lower arms to start")
    }

    private fun feedbackForPhase(): ExerciseFeedback {
        return when (phase) {
            CurlPhase.WAITING_FOR_BOTTOM -> ExerciseFeedback("Lower arms to start")
            CurlPhase.READY_AT_BOTTOM,
            CurlPhase.CURLING_UP,
            -> ExerciseFeedback("Curl up")

            CurlPhase.AT_TOP,
            CurlPhase.LOWERING_DOWN,
            -> ExerciseFeedback("Lower down")
        }
    }

    private fun resetTrackingState() {
        smoothedElbowAngle = null
        smoothedShoulderAngle = null
        clearPositionCandidate()
        stablePosition = CurlPosition.UNKNOWN
        phase = CurlPhase.WAITING_FOR_BOTTOM
        previousAcceptedAngle = null
        framesSinceTop = MIN_FRAMES_AFTER_TOP_BEFORE_REP
        rejectedFrameStreak = 0
    }

    private fun clearPositionCandidate() {
        candidatePosition = CurlPosition.UNKNOWN
        candidatePositionFrames = 0
    }

    private fun currentState(
        elbowAngle: Double?,
        shoulderAngle: Double?,
        leftElbowAngle: Double?,
        leftShoulderAngle: Double?,
        rightElbowAngle: Double?,
        rightShoulderAngle: Double?,
        poseDetected: Boolean,
        frameAccepted: Boolean,
        frameStatus: String,
        landmarkConfidence: Float?,
        armAgreementDegrees: Double?,
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
            countingMode = "two-arm state machine",
            stage = displayStage(frameAccepted),
            feedback = feedback,
            poseDetected = poseDetected,
            frameAccepted = frameAccepted,
            frameStatus = frameStatus,
            landmarkConfidence = landmarkConfidence,
            armAgreementDegrees = armAgreementDegrees,
        )
    }

    private fun displayStage(frameAccepted: Boolean): CurlStage {
        if (!frameAccepted) {
            return CurlStage.UNKNOWN
        }

        return when (phase) {
            CurlPhase.READY_AT_BOTTOM -> CurlStage.DOWN
            CurlPhase.AT_TOP -> CurlStage.UP
            CurlPhase.WAITING_FOR_BOTTOM,
            CurlPhase.CURLING_UP,
            CurlPhase.LOWERING_DOWN,
            -> CurlStage.UNKNOWN
        }
    }

    private enum class CurlPhase(val label: String) {
        WAITING_FOR_BOTTOM("waiting for bottom"),
        READY_AT_BOTTOM("ready at bottom"),
        CURLING_UP("curling up"),
        AT_TOP("at top"),
        LOWERING_DOWN("lowering down"),
    }

    private enum class CurlPosition {
        TOP,
        MID,
        BOTTOM,
        UNKNOWN,
    }

    companion object {
        // Python reference: np.interp(elbow_angle, (50, 160), (0, 100)).
        const val ELBOW_UP_THRESHOLD_DEGREES = 50.0
        const val ELBOW_DOWN_THRESHOLD_DEGREES = 160.0

        // Python reference: shoulder_angle > 150.
        const val SHOULDER_FORM_THRESHOLD_DEGREES = 150.0

        const val MIN_LANDMARK_CONFIDENCE = 0.55f
        const val MAX_ARM_ANGLE_DIFFERENCE_DEGREES = 30.0

        private const val SMOOTHING_ALPHA = 0.35
        private const val STABLE_POSITION_FRAME_COUNT = 3
        private const val MIN_FRAMES_BETWEEN_REPS = 10
        private const val MIN_FRAMES_AFTER_TOP_BEFORE_REP = 5
        private const val GOOD_REP_FEEDBACK_FRAMES = 10
        private const val MOVEMENT_DELTA_THRESHOLD_DEGREES = 1.5
        private const val REJECTED_FRAME_RESET_COUNT = 15

        private fun calculateArmAgreement(
            leftElbowAngle: Double?,
            rightElbowAngle: Double?,
        ): Double? {
            if (leftElbowAngle == null || rightElbowAngle == null) {
                return null
            }

            return abs(leftElbowAngle - rightElbowAngle)
        }
    }
}
