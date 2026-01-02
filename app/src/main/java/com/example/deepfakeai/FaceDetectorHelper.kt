package com.example.deepfakeai

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceDetectorHelper {
    private val detector: FaceDetector

    init {
        // High-accuracy landmark detection and face classification
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

        detector = FaceDetection.getClient(options)
    }

    fun detectFace(
        bitmap: Bitmap,
        onSuccess: (Rect?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    onSuccess(face.boundingBox)
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }
}
