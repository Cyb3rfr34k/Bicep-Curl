package com.rushmash91.exercisecounter.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface as AndroidSurface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rushmash91.exercisecounter.counter.BicepCurlCounter
import com.rushmash91.exercisecounter.counter.RepCounterState
import com.rushmash91.exercisecounter.pose.PoseAnalyzer
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(context.hasCameraPermission())
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraContent(modifier = modifier)
    } else {
        CameraPermissionRequest(
            modifier = modifier,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        )
    }
}

@Composable
private fun CameraContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val bicepCurlCounter = remember { BicepCurlCounter() }
    var state by remember { mutableStateOf(RepCounterState()) }
    val poseAnalyzer = remember(context) {
        PoseAnalyzer(
            context = context.applicationContext,
            counter = bicepCurlCounter,
            onState = { nextState ->
                mainExecutor.execute {
                    state = nextState
                }
            },
            isFrontCamera = true,
        )
    }

    LaunchedEffect(lifecycleOwner, previewView) {
        bindCameraUseCases(
            context = context,
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            analyzer = poseAnalyzer,
            cameraExecutor = cameraExecutor,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            poseAnalyzer.close()
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
        )

        CounterOverlay(
            state = state,
            onReset = {
                bicepCurlCounter.reset()
                state = RepCounterState()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CounterOverlay(
    state: RepCounterState,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = "Front-facing curl prototype",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.68f),
            shape = MaterialTheme.shapes.small,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    MetricBlock(label = "Reps", value = state.repCount.toString())
                    MetricBlock(label = "Curl", value = state.elbowAngle.formatAngle())
                    MetricBlock(label = "Stage", value = state.stage.label)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(onClick = onReset) {
                    Text("Reset")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = state.feedback.message,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.feedback.isPositive) Color(0xFF7CFFB2) else Color.White,
                )

                Spacer(modifier = Modifier.height(8.dp))

                DebugPanel(state = state)

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Prototype only. Camera pose estimates can miscount reps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun DebugPanel(state: RepCounterState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DebugRow(label = "Pose", value = if (state.poseDetected) "detected" else "not detected")
        DebugRow(label = "Mode", value = state.countingMode)
        DebugRow(label = "Both-arm curl angle", value = state.elbowAngle.formatAngle())
        DebugRow(label = "Form angle", value = state.shoulderAngle.formatAngle())
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.68f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun MetricBlock(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.72f),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun CameraPermissionRequest(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Camera permission is needed to count curls.",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("Allow camera")
        }
    }
}

private fun bindCameraUseCases(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    analyzer: PoseAnalyzer,
    cameraExecutor: ExecutorService,
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener(
        {
            val cameraProvider = cameraProviderFuture.get()
            val targetRotation = previewView.display?.rotation ?: AndroidSurface.ROTATION_0

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(targetRotation)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(targetRotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, analyzer)
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageAnalysis,
            )
        },
        ContextCompat.getMainExecutor(context),
    )
}

private fun Context.hasCameraPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun Double?.formatAngle(): String {
    return if (this == null) {
        "--"
    } else {
        String.format(Locale.US, "%.0f deg", this)
    }
}
