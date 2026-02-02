package com.example.deepfakeai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        
        var detectionCallback: ((Float, String, Bitmap?) -> Unit)? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var faceDetectorHelper: FaceDetectorHelper? = null
    private var tfliteInterpreter: org.tensorflow.lite.Interpreter? = null
    
    private val processingScope = CoroutineScope(Dispatchers.Default + Job())
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    private var lastCaptureTime = 0L
    private val captureInterval = 2000L // 2 seconds between captures (reduced from 1s for better performance)

    override fun onCreate() {
        super.onCreate()
        
        // Initialize face detector
        faceDetectorHelper = FaceDetectorHelper()
        
        // Initialize TFLite model
        try {
            val modelBuffer = loadModelFile("model.tflite")
            tfliteInterpreter = org.tensorflow.lite.Interpreter(modelBuffer)
            Log.i("SCREEN_CAPTURE", "Model loaded successfully")
        } catch (e: Exception) {
            Log.e("SCREEN_CAPTURE", "Failed to load model", e)
        }
        
        // Get screen metrics
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        Log.i("SCREEN_CAPTURE", "Screen: ${screenWidth}x${screenHeight} @ ${screenDensity}dpi")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                // Compatible way to get Parcelable
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                
                // CRITICAL: Call startForeground BEFORE accessing MediaProjection
                startForeground(2, createNotification())
                
                if (resultCode != -1 && resultData != null) {
                    startScreenCapture(resultCode, resultData)
                } else {
                    Log.e("SCREEN_CAPTURE", "Invalid result code or data: $resultCode")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopScreenCapture()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startScreenCapture(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
        
        // Create ImageReader for screen capture
        imageReader = ImageReader.newInstance(
            screenWidth / 2, // Half resolution for performance
            screenHeight / 2,
            PixelFormat.RGBA_8888,
            2
        )
        
        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            if (now - lastCaptureTime >= captureInterval) {
                lastCaptureTime = now
                processCapturedImage(reader)
            }
        }, mainHandler)
        
        // Create virtual display
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth / 2,
            screenHeight / 2,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
        
        Log.i("SCREEN_CAPTURE", "Screen capture started")
    }

    private fun processCapturedImage(reader: ImageReader) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image != null) {
                val bitmap = imageToBitmap(image)
                image.close()
                
                if (bitmap != null) {
                    processingScope.launch {
                        analyzeFrame(bitmap)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SCREEN_CAPTURE", "Error processing image", e)
            image?.close()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            
            // Crop to exact dimensions if needed
            if (rowPadding != 0) {
                Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e("SCREEN_CAPTURE", "Error converting image to bitmap", e)
            null
        }
    }

    private suspend fun analyzeFrame(screenBitmap: Bitmap) {
        withContext(Dispatchers.Main) {
            faceDetectorHelper?.detectFace(
                bitmap = screenBitmap,
                onSuccess = { bounds ->
                    if (bounds != null) {
                        Log.i("SCREEN_CAPTURE", "Face detected in screen capture")
                        processingScope.launch(Dispatchers.Default) {
                            val result = analyzeface(screenBitmap, bounds)
                            // Send update to SentinelOrb
                            sendSentinelUpdate(result.first, result.second)
                        }
                    } else {
                        // No face detected - send low risk update
                        sendSentinelUpdate(0f, "No Face Detected")
                    }
                },
                onError = { e ->
                    Log.e("SCREEN_CAPTURE", "Face detection error", e)
                }
            )
        }
    }


    private fun analyzeface(bitmap: Bitmap, boundingBox: android.graphics.Rect): Triple<Float, String, Bitmap?> {
        try {
            // Crop and preprocess face
            val left = boundingBox.left.coerceAtLeast(0)
            val top = boundingBox.top.coerceAtLeast(0)
            val width = boundingBox.width().coerceAtMost(bitmap.width - left)
            val height = boundingBox.height().coerceAtMost(bitmap.height - top)
            
            if (width <= 0 || height <= 0) return Triple(0f, "Invalid face region", null)
            
            val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)
            val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, 224, 224, true)
            
            // Prepare input buffer
            val inputBuffer = java.nio.ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4)
            inputBuffer.order(java.nio.ByteOrder.nativeOrder())
            
            val intValues = IntArray(224 * 224)
            scaledBitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)
            
            var pixel = 0
            for (i in 0 until 224) {
                for (j in 0 until 224) {
                    val input = intValues[pixel++]
                    inputBuffer.putFloat(((input shr 16 and 0xFF) / 255.0f))
                    inputBuffer.putFloat(((input shr 8 and 0xFF) / 255.0f))
                    inputBuffer.putFloat(((input and 0xFF) / 255.0f))
                }
            }
            
            // Run inference
            val outputBuffer = Array(1) { FloatArray(1) }
            var rawScore = 0.5f
            
            try {
                tfliteInterpreter?.run(inputBuffer, outputBuffer)
                // Invert score (model outputs high for real, we want high for fake)
                rawScore = 1.0f - outputBuffer[0][0]
            } catch (e: Exception) {
                Log.e("SCREEN_CAPTURE", "Inference error", e)
            }
            
            val riskLevel = when {
                rawScore >= 0.65f -> "High Risk"
                rawScore > 0.35f -> "Suspicious"
                else -> "Low Risk"
            }
            
            return Triple(rawScore, riskLevel, scaledBitmap)
            
        } catch (e: Exception) {
            Log.e("SCREEN_CAPTURE", "Analysis error", e)
            return Triple(0f, "Analysis failed", null)
        }
    }

    private fun stopScreenCapture() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        
        Log.i("SCREEN_CAPTURE", "Screen capture stopped")
    }

    private fun loadModelFile(modelPath: String): java.nio.MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelPath)
        val inputStream = java.io.FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun createNotification(): Notification {
        createNotificationChannel()
        
        return NotificationCompat.Builder(this, "screen_capture_channel")
            .setContentTitle("📺 Screen Capture Active")
            .setContentText("Analyzing on-screen video content")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "screen_capture_channel",
                "Screen Capture Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Analyzing video content on screen"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun sendSentinelUpdate(confidence: Float, riskLevel: String) {
        val intent = Intent(this, SentinelOrbService::class.java).apply {
            action = SentinelOrbService.ACTION_UPDATE_CONFIDENCE
            putExtra(SentinelOrbService.EXTRA_CONFIDENCE, confidence)
            putExtra(SentinelOrbService.EXTRA_RISK_LEVEL, riskLevel)
            putExtra(SentinelOrbService.EXTRA_FACES, if (confidence > 0f) 1 else 0)
            putExtra(SentinelOrbService.EXTRA_AUDIO_STATUS, "Screen Monitor")
        }
        try {
            startService(intent)
            Log.i("SCREEN_CAPTURE", "Sent Sentinel update: $confidence | $riskLevel")
        } catch (e: Exception) {
            Log.e("SCREEN_CAPTURE", "Failed to send Sentinel update", e)
        }
    }

    override fun onDestroy() {

        super.onDestroy()
        stopScreenCapture()
        processingScope.cancel()
        tfliteInterpreter?.close()
        faceDetectorHelper = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
