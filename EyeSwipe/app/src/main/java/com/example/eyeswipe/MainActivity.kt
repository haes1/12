package com.example.eyeswipe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.eyeswipe.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var testRunning = false
    private var trackingRunning = false
    private var destroyed = false

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .build()
    )

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshStatus()
        if (it) startFaceCheck()
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.btnCameraPermission.setOnClickListener {
            if (hasCameraPermission()) {
                Toast.makeText(this, "Камера уже разрешена", Toast.LENGTH_SHORT).show()
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnTestCamera.setOnClickListener {
            if (!hasCameraPermission()) {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            } else {
                if (testRunning) stopFaceCheck() else startFaceCheck()
            }
        }

        val savedProgress = Prefs.getThresholdProgress(this)
        binding.sensitivitySeekBar.progress = savedProgress
        updateThreshold(savedProgress)

        binding.sensitivitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateThreshold(progress)
                if (fromUser) Prefs.setThresholdProgress(this@MainActivity, progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnToggleTracking.setOnClickListener { toggleTracking() }
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (destroyed) return
        // The user can grant/revoke Accessibility while Settings is open.
        refreshStatus()
        if (trackingRunning && !isAccessibilityServiceEnabled()) {
            stopService(Intent(this, EyeTrackingService::class.java))
            trackingRunning = false
            binding.btnToggleTracking.text = getString(R.string.btn_start_tracking)
        }
    }

    private fun updateThreshold(progress: Int) {
        binding.thresholdValueText.text = getString(R.string.threshold_value, progress + 5)
    }

    private fun startFaceCheck() {
        if (testRunning) return
        testRunning = true
        binding.previewCard.visibility = View.VISIBLE
        binding.btnTestCamera.text = getString(R.string.btn_stop_test)
        binding.captureState.text = getString(R.string.capture_searching)
        binding.faceOverlay.clear()
        startPreviewCamera()
    }

    private fun stopFaceCheck() {
        testRunning = false
        cameraProvider?.unbindAll()
        binding.btnTestCamera.text = getString(R.string.btn_test_camera)
        binding.captureState.text = getString(R.string.capture_off)
        binding.faceOverlay.clear()
    }

    private fun startPreviewCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!testRunning || isFinishing) return@addListener
            val provider = try {
                future.get()
            } catch (e: Exception) {
                binding.captureState.text = getString(R.string.capture_camera_error)
                testRunning = false
                binding.btnTestCamera.text = getString(R.string.btn_test_camera)
                return@addListener
            }
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                analyzeTestFrame(imageProxy)
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                testRunning = false
                binding.captureState.post { binding.captureState.text = getString(R.string.capture_camera_error) }
                binding.btnTestCamera.post { binding.btnTestCamera.text = getString(R.string.btn_test_camera) }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeTestFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (!testRunning || destroyed || isFinishing) return@addOnSuccessListener

                if (face == null) {
                    binding.captureState.post { binding.captureState.text = getString(R.string.capture_searching) }
                    binding.faceOverlay.post { binding.faceOverlay.clear() }
                    return@addOnSuccessListener
                }

                val pitch = face.headEulerAngleX
                val threshold = Prefs.getThresholdDegrees(this)
                val lookingUp = pitch > threshold

                binding.captureState.post {
                    binding.captureState.text = if (lookingUp) {
                        getString(R.string.capture_trigger_ready)
                    } else {
                        getString(R.string.capture_face_found)
                    }
                    binding.pitchValue.text = getString(R.string.pitch_value, pitch)
                    binding.thresholdSmall.text = getString(R.string.threshold_small, threshold)
                }

                binding.faceOverlay.post {
                    binding.faceOverlay.setFace(
                        face.boundingBox,
                        imageProxy.width,
                        imageProxy.height,
                        imageProxy.imageInfo.rotationDegrees
                    )
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun toggleTracking() {
        if (!hasCameraPermission()) {
            Toast.makeText(this, "Сначала разрешите доступ к камере", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(
                this,
                "Сначала включите службу EyeSwipe в специальных возможностях",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (trackingRunning) {
            stopService(Intent(this, EyeTrackingService::class.java))
            trackingRunning = false
            binding.btnToggleTracking.text = getString(R.string.btn_start_tracking)
        } else {
            // Android 14+ requires the camera FGS to be started while the app is visible.
            try {
                ContextCompat.startForegroundService(this, Intent(this, EyeTrackingService::class.java))
                trackingRunning = true
                binding.btnToggleTracking.text = getString(R.string.btn_stop_tracking)
            } catch (e: SecurityException) {
                trackingRunning = false
                Toast.makeText(this, "Не удалось запустить отслеживание: проверьте разрешение камеры", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                trackingRunning = false
                Toast.makeText(this, "Не удалось запустить отслеживание", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${SwipeAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    private fun refreshStatus() {
        val camOk = hasCameraPermission()
        val accOk = isAccessibilityServiceEnabled()

        binding.cameraStatus.text = if (camOk) getString(R.string.status_ready) else getString(R.string.status_needed)
        binding.accessibilityStatus.text = if (accOk) getString(R.string.status_ready) else getString(R.string.status_needed)
        binding.cameraStatusDot.isSelected = camOk
        binding.accessibilityStatusDot.isSelected = accOk
        binding.btnCameraPermission.text =
            if (camOk) getString(R.string.btn_camera_granted) else getString(R.string.btn_camera_permission)
    }

    override fun onDestroy() {
        destroyed = true
        testRunning = false
        cameraProvider?.unbindAll()
        cameraExecutor.shutdownNow()
        detector.close()
        super.onDestroy()
    }
}
