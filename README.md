# Bicep Curl Counter

Native Android prototype for counting front-facing, two-arm bicep curls with the phone camera.

This is a small MVP focused on one exercise only:

- Kotlin
- Jetpack Compose UI
- CameraX live camera preview and image analysis
- MediaPipe Pose Landmarker
- Offline-only
- No login, cloud backend, ads, or account system

Prototype disclaimer: this app uses camera-based pose estimates and simple thresholds. It may miscount reps, especially with poor lighting, partial body visibility, camera motion, or unusual viewing angles.

## How To Test

The current prototype works best with this setup:

1. Put the phone about 3 feet away in portrait orientation.
2. Use the front camera.
3. Face the camera directly.
4. Keep your upper body, shoulders, elbows, wrists, and hips visible.
5. Curl both arms together.

The app treats one two-arm curl as one rep. It averages the left and right elbow joint angles into a single curl signal, then counts a rep when that signal completes a full curl cycle.

## Run

Open the repository root in Android Studio, let Gradle sync, then run the `app` configuration on a physical Android device.

From the command line:

```bash
./gradlew :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## How It Works

The runtime pipeline is:

```text
CameraX frame
-> MediaPipe Pose Landmarker
-> shoulder/elbow/wrist/hip landmarks
-> elbow and shoulder angle calculations
-> two-arm curl angle average
-> up/down stage detection
-> rep counter
-> Compose overlay
```

MediaPipe landmarks used:

- Left shoulder: `11`
- Right shoulder: `12`
- Left elbow: `13`
- Right elbow: `14`
- Left wrist: `15`
- Right wrist: `16`
- Left hip: `23`
- Right hip: `24`

Thresholds are intentionally plain constants in `BicepCurlCounter.kt` so they can be tuned later:

- Curled/up threshold: elbow angle `<= 50`
- Lowered/down threshold: elbow angle `>= 160`
- Shoulder/form threshold: shoulder angle `>= 150`

The debug overlay is temporary instrumentation. It is there to make the development loop visible while testing: pose detected, counting mode, curl angle, form angle, stage, feedback, and reset.

## Attribution

This Android MVP was inspired by the public Python MediaPipe/OpenCV exercise-counter project by Arush Sharma (`rushmash91/Exercise-Counter`). The Android implementation here is a fresh Kotlin/CameraX/Compose/MediaPipe Pose Landmarker project focused specifically on two-arm bicep curls.

## Project Files

- `app/src/main/java/com/cyb3rfr34k/bicepcurl/MainActivity.kt`
- `app/src/main/java/com/cyb3rfr34k/bicepcurl/ui/CameraScreen.kt`
- `app/src/main/java/com/cyb3rfr34k/bicepcurl/pose/PoseAnalyzer.kt`
- `app/src/main/java/com/cyb3rfr34k/bicepcurl/pose/AngleCalculator.kt`
- `app/src/main/java/com/cyb3rfr34k/bicepcurl/counter/BicepCurlCounter.kt`
- `app/src/main/java/com/cyb3rfr34k/bicepcurl/counter/RepCounterState.kt`
- `app/src/main/assets/pose_landmarker_lite.task`

## Next Milestones

- Hide/show debug overlay with a debug-mode toggle.
- Add a simple skeleton overlay for shoulders, elbows, and wrists.
- Add calibration for personal top/bottom curl angles.
- Add session summary: reps, duration, average rep time.
- Add unit tests for `BicepCurlCounter`.
