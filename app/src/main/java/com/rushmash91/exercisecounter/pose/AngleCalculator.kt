package com.rushmash91.exercisecounter.pose

import kotlin.math.atan2

data class PosePoint(
    val x: Float,
    val y: Float,
)

object AngleCalculator {
    /**
     * Kotlin translation of PoseModule.findAngle().
     *
     * The Python version calculates atan2(p3 - p2) - atan2(p1 - p2), converts
     * to degrees, then wraps negative values into the 0..360 range.
     */
    fun calculateAngle(first: PosePoint, middle: PosePoint, last: PosePoint): Double {
        var angle = Math.toDegrees(
            atan2(
                (last.y - middle.y).toDouble(),
                (last.x - middle.x).toDouble(),
            ) - atan2(
                (first.y - middle.y).toDouble(),
                (first.x - middle.x).toDouble(),
            ),
        )

        if (angle < 0.0) {
            angle += FULL_ROTATION_DEGREES
        }

        return angle
    }

    fun calculateJointAngle(first: PosePoint, middle: PosePoint, last: PosePoint): Double {
        val directedAngle = calculateAngle(first, middle, last)
        return if (directedAngle > HALF_ROTATION_DEGREES) {
            FULL_ROTATION_DEGREES - directedAngle
        } else {
            directedAngle
        }
    }

    private const val FULL_ROTATION_DEGREES = 360.0
    private const val HALF_ROTATION_DEGREES = 180.0
}
