package com.rushmash91.exercisecounter.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.rushmash91.exercisecounter.counter.BicepCurlCounter
import com.rushmash91.exercisecounter.counter.RepCounterState

class PoseAnalyzer(
    context: Context,
    private val counter: BicepCurlCounter,
    private val onState: (RepCounterState) -> Unit,
    private val isFrontCamera: Boolean = true,
) : ImageAnalysis.Analyzer {
    private val poseLandmarker: PoseLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setDelegate(Delegate.CPU)
            .setModelAssetPath(MODEL_ASSET_PATH)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(MIN_POSE_DETECTION_CONFIDENCE)
            .setMinPosePresenceConfidence(MIN_POSE_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
            .setResultListener(::handleResult)
            .setErrorListener { onState(counter.update(null, null)) }
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
    }

    override fun analyze(imageProxy: ImageProxy) {
        val frameTime = SystemClock.uptimeMillis()

        try {
            val bitmap = imageProxy.toBitmapForPoseDetection(isFrontCamera)
            val image = BitmapImageBuilder(bitmap).build()
            poseLandmarker.detectAsync(image, frameTime)
        } finally {
            imageProxy.close()
        }
    }

    fun close() {
        poseLandmarker.close()
    }

    private fun handleResult(result: PoseLandmarkerResult, @Suppress("UNUSED_PARAMETER") input: com.google.mediapipe.framework.image.MPImage) {
        val pose = result.landmarks().firstOrNull()
        if (pose == null) {
            onState(counter.update(null, null))
            return
        }

        val measurement = extractBicepCurlMeasurement(pose)
        if (measurement?.twoArmElbowAngle == null || measurement.combinedShoulderAngle == null) {
            onState(counter.update(null, null))
            return
        }

        onState(
            counter.update(
                elbowAngle = measurement.twoArmElbowAngle,
                shoulderAngle = measurement.combinedShoulderAngle,
                leftElbowAngle = measurement.left?.elbowAngle,
                leftShoulderAngle = measurement.left?.shoulderAngle,
                rightElbowAngle = measurement.right?.elbowAngle,
                rightShoulderAngle = measurement.right?.shoulderAngle,
            ),
        )
    }

    private fun extractBicepCurlMeasurement(
        pose: List<NormalizedLandmark>,
    ): BicepCurlFrameMeasurement? {
        if (pose.size <= LEFT_HIP) {
            return null
        }

        val left = pose.extractArmMeasurement(
            shoulderIndex = LEFT_SHOULDER,
            elbowIndex = LEFT_ELBOW,
            wristIndex = LEFT_WRIST,
            hipIndex = LEFT_HIP,
        )

        val right = pose.extractArmMeasurement(
            shoulderIndex = RIGHT_SHOULDER,
            elbowIndex = RIGHT_ELBOW,
            wristIndex = RIGHT_WRIST,
            hipIndex = RIGHT_HIP,
        )

        return BicepCurlFrameMeasurement(
            left = left,
            right = right,
            twoArmElbowAngle = averageOrNull(left?.elbowAngle, right?.elbowAngle),
            combinedShoulderAngle = maxOrNull(left?.shoulderAngle, right?.shoulderAngle),
        )
    }

    private fun List<NormalizedLandmark>.extractArmMeasurement(
        shoulderIndex: Int,
        elbowIndex: Int,
        wristIndex: Int,
        hipIndex: Int,
    ): ArmMeasurement? {
        val maxIndex = maxOf(shoulderIndex, elbowIndex, wristIndex, hipIndex)
        if (size <= maxIndex) {
            return null
        }

        val shoulder = point(shoulderIndex)
        val elbow = point(elbowIndex)
        val wrist = point(wristIndex)
        val hip = point(hipIndex)

        return ArmMeasurement(
            elbowAngle = AngleCalculator.calculateJointAngle(shoulder, elbow, wrist),
            shoulderAngle = AngleCalculator.calculateAngle(hip, shoulder, elbow),
        )
    }

    private fun averageOrNull(first: Double?, second: Double?): Double? {
        return if (first == null || second == null) {
            null
        } else {
            (first + second) / 2.0
        }
    }

    private fun maxOrNull(first: Double?, second: Double?): Double? {
        return when {
            first == null -> second
            second == null -> first
            else -> maxOf(first, second)
        }
    }

    private fun List<NormalizedLandmark>.point(index: Int): PosePoint {
        val landmark = this[index]
        return PosePoint(x = landmark.x(), y = landmark.y())
    }

    private fun ImageProxy.toBitmapForPoseDetection(frontCamera: Boolean): Bitmap {
        val sourceBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val buffer = planes[0].buffer
        buffer.rewind()
        sourceBitmap.copyPixelsFromBuffer(buffer)

        val rotationDegrees = imageInfo.rotationDegrees.toFloat()
        val transform = Matrix().apply {
            postRotate(rotationDegrees)

            if (frontCamera) {
                postScale(-1f, 1f, width.toFloat(), height.toFloat())
            }
        }

        return Bitmap.createBitmap(
            sourceBitmap,
            0,
            0,
            sourceBitmap.width,
            sourceBitmap.height,
            transform,
            true,
        )
    }

    private data class BicepCurlFrameMeasurement(
        val left: ArmMeasurement?,
        val right: ArmMeasurement?,
        val twoArmElbowAngle: Double?,
        val combinedShoulderAngle: Double?,
    )

    private data class ArmMeasurement(
        val elbowAngle: Double,
        val shoulderAngle: Double,
    )

    companion object {
        private const val MODEL_ASSET_PATH = "pose_landmarker_lite.task"
        private const val MIN_POSE_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_POSE_PRESENCE_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f

        private const val LEFT_SHOULDER = 11
        private const val LEFT_ELBOW = 13
        private const val LEFT_WRIST = 15
        private const val LEFT_HIP = 23

        private const val RIGHT_SHOULDER = 12
        private const val RIGHT_ELBOW = 14
        private const val RIGHT_WRIST = 16
        private const val RIGHT_HIP = 24
    }
}
