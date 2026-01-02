package com.example.deepfakeai

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import android.util.Log

import android.view.View
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private enum class AppMode {
        NONE, VIDEO_MODE, CAMERA_MODE
    }

    private var selectedMode: AppMode = AppMode.NONE
    private var tfliteInterpreter: Interpreter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Views
        val statusTextView = findViewById<android.widget.TextView>(R.id.statusTextView)
        val btnSelectVideo = findViewById<android.widget.Button>(R.id.btnSelectVideo)
        val btnLiveCamera = findViewById<android.widget.Button>(R.id.btnLiveCamera)

        // Initialize TFLite Interpreter
        try {
            val modelBuffer = loadModelFile("model.tflite")
            tfliteInterpreter = Interpreter(modelBuffer)
            Log.i("MODEL_STATUS", "TensorFlow Lite model loaded successfully 🎯")
            statusTextView.text = "Model Loaded Successfully ✅"
        } catch (e: Exception) {
            Log.e("MODEL_STATUS", "❌ Failed to load TFLite model", e)
            statusTextView.text = "Model Load Failed ❌"
        }

        // Test Face Detection with a dummy bitmap
        val dummyBitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        val faceHelper = FaceDetectorHelper(
            onSuccess = { bounds ->
                if (bounds != null) {
                    Log.i("FaceTest", "Success: Face found at $bounds")
                } else {
                    Log.i("FaceTest", "Success: No face found (as expected for blank bitmap)")
                }
            },
            onError = { e ->
                Log.e("FaceTest", "Error during face detection", e)
            }
        )
        faceHelper.detectFace(dummyBitmap)

        // Set Click Listeners
        btnSelectVideo.setOnClickListener {
            selectedMode = AppMode.VIDEO_MODE
            statusTextView.text = "Video analysis mode selected"
            Log.d("DeepfakeAI", "Mode changed to: $selectedMode")
        }

        btnLiveCamera.setOnClickListener {
            selectedMode = AppMode.CAMERA_MODE
            statusTextView.text = "Live camera detection mode selected"
            Log.d("DeepfakeAI", "Mode changed to: $selectedMode")
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
}
