package com.example.eyeswipe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Runs the front camera in the background (no preview UI), feeds every frame
 * to ML Kit's on-device face detector, and watches the head's pitch angle
 * (headEulerAngleX — positive means the face is tilted/looking upward).
 * When it crosses the user-set threshold, it asks SwipeAccessibilityService
 * to inject an upward swipe gesture.
 *
 * We use head pitch rather than true iris/pupil gaze tracking: reliable
 * eye-only gaze estimation from a phone's front camera (with the head free
 * to move) is a much harder, flakier problem. Tilting your head up is what
 * most real "hands-free scroll" apps actually detect.
 */
class EyeTrackingService : LifecycleService() {

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    private var lastTriggerAtMs = 0L
    private var wasLookingUp = false

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
        startForegroundNotification()
        startCamera()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startForegroundNotification() {
        val channelId = "eyeswipe_tracking"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Eye tracking", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("EyeSwipe is running")
            .setContentText("Look up to trigger a swipe. Tap to open the app.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor) { imageProxy -> analyzeFrame(imageProxy) } }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @ExperimentalGetImage
    private fun analyzeFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val face = faces.firstOrNull() ?: return@addOnSuccessListener
                val pitch = face.headEulerAngleX // positive = looking up
                val threshold = Prefs.getThresholdDegrees(this)
                val lookingUpNow = pitch > threshold

                if (lookingUpNow && !wasLookingUp) {
                    maybeTriggerSwipe()
                }
                wasLookingUp = lookingUpNow
            }
            .addOnFailureListener { Log.e(TAG, "Face detection failed", it) }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun maybeTriggerSwipe() {
        val now = System.currentTimeMillis()
        if (now - lastTriggerAtMs < COOLDOWN_MS) return
        lastTriggerAtMs = now
        SwipeAccessibilityService.instance?.performSwipeUp()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        detector.close()
    }

    companion object {
        private const val TAG = "EyeTrackingService"
        private const val NOTIFICATION_ID = 42
        private const val COOLDOWN_MS = 900L
    }
}
