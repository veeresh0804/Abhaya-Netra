package com.example.deepfakeai

import android.Manifest
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private enum class AppMode {
        NONE, VIDEO_MODE, CAMERA_MODE
    }

    private var selectedMode: AppMode = AppMode.NONE
    private var tfliteInterpreter: Interpreter? = null
    private var faceDetectorHelper: FaceDetectorHelper? = null

    // CameraX
    private lateinit var cameraExecutor: ExecutorService
    private var lastAnalyzedTimestamp = 0L

    // Coroutine scope for background processing
    private val processingScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentProcessingJob: Job? = null

    // Permission Launcher
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Log.e("DeepfakeAI", "Camera permission denied")
            findViewById<TextView>(R.id.statusTextView).text = "Camera permission granted ❌"
        }
    }

    // Video Picker Launcher
    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            Log.i("DeepfakeAI", "Video selected: $uri")
            processVideo(uri)
        } else {
            Log.i("DeepfakeAI", "Video selection cancelled")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize Views
        val statusTextView = findViewById<TextView>(R.id.statusTextView)
        val btnSelectVideo = findViewById<android.widget.Button>(R.id.btnSelectVideo)
        val btnLiveCamera = findViewById<android.widget.Button>(R.id.btnLiveCamera)
        val viewFinder = findViewById<PreviewView>(R.id.viewFinder)

        // Initialize Face Detector Helper
        faceDetectorHelper = FaceDetectorHelper()

        // Initialize TFLite Interpreter
        try {
            val modelBuffer = loadModelFile("model.tflite")
            tfliteInterpreter = Interpreter(modelBuffer)
            Log.i("MODEL_STATUS", "TensorFlow Lite model loaded successfully 🎯")
            statusTextView.text = "Model Loaded Successfully ✅"
        } catch (e: Exception) {
            Log.e("MODEL_STATUS", "❌ Failed to load TFLite model", e)
            statusTextView.text = "Model Load Failed ❌" // Keep specific failure visible initially
        }

        // Set Click Listeners
        btnSelectVideo.setOnClickListener {
            selectedMode = AppMode.VIDEO_MODE
            currentProcessingJob?.cancel() // Stop video loop if running
            viewFinder.visibility = View.INVISIBLE // Hide camera
            statusTextView.text = "Video analysis mode selected. Choosing video..."
            Log.d("DeepfakeAI", "Mode changed to: $selectedMode")
            pickVideoLauncher.launch("video/*")
        }

        btnLiveCamera.setOnClickListener {
            selectedMode = AppMode.CAMERA_MODE
            currentProcessingJob?.cancel() // Stop video loop logic
            statusTextView.text = "Live camera detection mode selected"
            viewFinder.visibility = View.VISIBLE
            Log.d("DeepfakeAI", "Mode changed to: $selectedMode")
            
            checkCameraPermissionAndStart()
        }
    }

    private fun checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processCameraFrame(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                Log.i("DeepfakeAI", "Camera started successfully")
            } catch(exc: Exception) {
                Log.e("DeepfakeAI", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class) 
    private fun processCameraFrame(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        // Throttle: 1 FPS (1000ms)
        if (currentTimestamp - lastAnalyzedTimestamp >= 1000) {
            lastAnalyzedTimestamp = currentTimestamp
            
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                 val bitmap = imageProxy.toBitmap()
                
                 faceDetectorHelper?.detectFace(
                     bitmap = bitmap,
                     onSuccess = { bounds ->
                        runOnUiThread {
                            val statusTextView = findViewById<TextView>(R.id.statusTextView)
                            if (bounds != null) {
                                Log.i("FACE_DETECTION", "FACE DETECTED bbox=$bounds")
                                statusTextView.text = "Face detected ✅"
                                
                                // Preprocess off main thread
                                processingScope.launch(Dispatchers.Default) {
                                    preprocessFaceForModel(bitmap, bounds)
                                }
                            } else {
                                Log.i("FACE_DETECTION", "NO FACE DETECTED")
                                statusTextView.text = "No face detected ❌"
                            }
                        }
                     },
                     onError = { e ->
                        Log.e("FACE_DETECTION", "Error in detection", e)
                     }
                 )
                 
                 // Initial UI update
                 runOnUiThread {
                    findViewById<TextView>(R.id.statusTextView).text = "Analyzing Camera Frame..."
                 }
            }
        }
        imageProxy.close()
    }

    private fun processVideo(videoUri: Uri) {
        val statusTextView = findViewById<TextView>(R.id.statusTextView) // Re-fetch or use member if ViewBinding
        currentProcessingJob?.cancel() // Cancel previous job if any

        currentProcessingJob = processingScope.launch(Dispatchers.Default) {
             val retriever = MediaMetadataRetriever()
             try {
                 retriever.setDataSource(this@MainActivity, videoUri)
                 val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                 val durationMs = durationStr?.toLongOrNull() ?: 0L
                 
                 Log.i("DeepfakeAI", "Starting processing for video length: ${durationMs}ms")
                 
                 var currentTimeMs = 0L
                 val intervalMs = 300L // 300ms interval
                 
                 while (currentTimeMs < durationMs && isActive) {
                     // getFrameAtTime takes microseconds
                     val bitmap = retriever.getFrameAtTime(currentTimeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                     
                     if (bitmap != null) {
                         withContext(Dispatchers.Main) {
                             statusTextView.text = "Processing frame at ${currentTimeMs}ms..."
                             
                             faceDetectorHelper?.detectFace(
                                 bitmap = bitmap,
                                 onSuccess = { bounds ->
                                     if (bounds != null) {
                                         Log.i("FACE_DETECTION", "timestamp=${currentTimeMs}ms — FACE DETECTED bbox=$bounds")
                                         statusTextView.text = "Face detected at ${currentTimeMs}ms ✅"
                                         
                                         // Preprocess
                                         processingScope.launch(Dispatchers.Default) {
                                             preprocessFaceForModel(bitmap, bounds)
                                         }
                                     } else {
                                          Log.i("FACE_DETECTION", "timestamp=${currentTimeMs}ms — NO FACE")
                                          statusTextView.text = "No face detected at ${currentTimeMs}ms ❌"
                                     }
                                 },
                                 onError = { e ->
                                     Log.e("FACE_DETECTION", "Error detecting face at ${currentTimeMs}ms", e)
                                 }
                             )
                         }
                     }
                     
                     currentTimeMs += intervalMs
                     delay(10) // Small yield
                 }
                 
                 withContext(Dispatchers.Main) {
                     statusTextView.text = "Video Processing Complete ✅"
                 }
                 
             } catch (e: Exception) {
                 Log.e("DeepfakeAI", "Error extracting frames", e)
                 withContext(Dispatchers.Main) {
                     statusTextView.text = "Error processing video ❌"
                 }
             } finally {
                 retriever.release()
             }
        }
    }

    private fun preprocessFaceForModel(originalBitmap: Bitmap, boundingBox: android.graphics.Rect) {
        try {
            // 1. Clamp bounding box
            val left = boundingBox.left.coerceAtLeast(0)
            val top = boundingBox.top.coerceAtLeast(0)
            val width = boundingBox.width().coerceAtMost(originalBitmap.width - left)
            val height = boundingBox.height().coerceAtMost(originalBitmap.height - top)
            
            if (width <= 0 || height <= 0) {
                 Log.w("FACE_PREPROCESS", "Invalid face crop dimensions: $width x $height")
                 return
            }

            // 2. Crop
            val croppedBitmap = android.graphics.Bitmap.createBitmap(originalBitmap, left, top, width, height)
            
            // 3. Resize to 224x224
            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(croppedBitmap, 224, 224, true)
            
            // UI Verification: Show cropped face
            runOnUiThread {
                val facePreview = findViewById<android.widget.ImageView>(R.id.facePreview)
                facePreview.setImageBitmap(scaledBitmap)
                facePreview.visibility = View.VISIBLE
            }
            
            // 4. Normalize (0-1 float) & Convert to ByteBuffer
            // Allocate Direct ByteBuffer: 1 * 224 * 224 * 3 * 4 (float)
            val inputBuffer = java.nio.ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4)
            inputBuffer.order(java.nio.ByteOrder.nativeOrder())
            
            val intValues = IntArray(224 * 224)
            scaledBitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)
            
            var pixel = 0
            for (i in 0 until 224) {
                for (j in 0 until 224) {
                    val input = intValues[pixel++]
                    
                    // Normalize to 0.0 - 1.0 (assuming model expects this range)
                    // If -1 to 1: ((val and 0xFF) - 127.5f) / 127.5f
                    // We'll use 0-1 for now as per plan
                    inputBuffer.putFloat(((input shr 16 and 0xFF) / 255.0f))
                    inputBuffer.putFloat(((input shr 8 and 0xFF) / 255.0f))
                    inputBuffer.putFloat(((input and 0xFF) / 255.0f))
                }
            }
            
            Log.i("PREPROCESS_FACE", "Valid Crop: ${width}x${height} | Resized: 224x224 | TensorBuffer Created")
            
        } catch (e: Exception) {
            Log.e("PREPROCESS_FACE", "Error preprocessing face", e)
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
