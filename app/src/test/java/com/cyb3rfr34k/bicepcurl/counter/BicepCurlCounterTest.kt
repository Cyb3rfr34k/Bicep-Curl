package com.cyb3rfr34k.bicepcurl.counter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BicepCurlCounterTest {
    @Test
    fun countsOneRepAfterFullTwoArmDownUpDownCycle() {
        val counter = BicepCurlCounter()
        var state = counter.feedBottom()

        state = counter.feedTop()
        assertEquals(0, state.repCount)
        assertEquals(CurlStage.UP, state.stage)

        state = counter.feedBottom()
        assertEquals(1, state.repCount)
        assertEquals(CurlStage.DOWN, state.stage)
        assertTrue(state.feedback.isPositive)
    }

    @Test
    fun doesNotCountWhenStartingAtTop() {
        val counter = BicepCurlCounter()

        counter.feedTop()
        val state = counter.feedBottom()

        assertEquals(0, state.repCount)
        assertEquals(CurlStage.DOWN, state.stage)
    }

    @Test
    fun rejectsUnsyncedTwoArmFrames() {
        val counter = BicepCurlCounter()
        counter.feedBottom()

        val state = counter.update(
            elbowAngle = 95.0,
            shoulderAngle = 170.0,
            leftElbowAngle = 45.0,
            rightElbowAngle = 150.0,
            leftShoulderAngle = 170.0,
            rightShoulderAngle = 170.0,
            landmarkConfidence = 0.95f,
            poseDetected = true,
        )

        assertFalse(state.frameAccepted)
        assertEquals("arms out of sync", state.frameStatus)
        assertEquals(0, state.repCount)
    }

    @Test
    fun rejectsLowConfidenceFrames() {
        val counter = BicepCurlCounter()

        val state = counter.update(
            elbowAngle = 165.0,
            shoulderAngle = 170.0,
            leftElbowAngle = 165.0,
            rightElbowAngle = 166.0,
            leftShoulderAngle = 170.0,
            rightShoulderAngle = 170.0,
            landmarkConfidence = 0.2f,
            poseDetected = true,
        )

        assertFalse(state.frameAccepted)
        assertEquals("low landmark confidence", state.frameStatus)
        assertEquals(0, state.repCount)
    }

    private fun BicepCurlCounter.feedBottom(): RepCounterState {
        var state = RepCounterState()
        repeat(12) {
            state = updateAcceptedFrame(elbowAngle = 166.0)
        }
        return state
    }

    private fun BicepCurlCounter.feedTop(): RepCounterState {
        var state = RepCounterState()
        repeat(12) {
            state = updateAcceptedFrame(elbowAngle = 45.0)
        }
        return state
    }

    private fun BicepCurlCounter.updateAcceptedFrame(elbowAngle: Double): RepCounterState {
        return update(
            elbowAngle = elbowAngle,
            shoulderAngle = 170.0,
            leftElbowAngle = elbowAngle,
            rightElbowAngle = elbowAngle + 1.0,
            leftShoulderAngle = 170.0,
            rightShoulderAngle = 170.0,
            landmarkConfidence = 0.95f,
            poseDetected = true,
        )
    }
}
