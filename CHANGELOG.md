# Changelog

## 0.2.0

- Added smarter two-arm bicep curl state-machine counting.
- Added landmark confidence filtering before frames can affect rep state.
- Added left/right elbow agreement checks for synchronized two-arm curls.
- Added rejection reasons to the debug overlay: frame status, confidence, and arm sync.
- Added JVM unit tests for the pure Kotlin rep counter.

## 0.1.0

- Created the first native Android MVP.
- Added CameraX live preview.
- Added MediaPipe Pose Landmarker frame analysis.
- Added two-arm curl angle averaging.
- Added simple rep count, stage, feedback, and reset UI.
