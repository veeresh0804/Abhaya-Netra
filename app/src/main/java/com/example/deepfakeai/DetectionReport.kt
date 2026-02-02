package com.example.deepfakeai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class representing a deepfake detection report
 */
data class DetectionReport(
    val timestamp: Long = System.currentTimeMillis(),
    val mode: String, // "Camera", "Video", "Screen Capture"
    val confidence: Float,
    val riskLevel: String,
    val facesDetected: Int,
    val sessionDuration: Long, // in milliseconds
    val averageScore: Float,
    val peakScore: Float,
    val framesProcessed: Int,
    val inferenceCalls: Int
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun getFormattedDuration(): String {
        val seconds = sessionDuration / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return if (minutes > 0) {
            "${minutes}m ${remainingSeconds}s"
        } else {
            "${remainingSeconds}s"
        }
    }
    
    fun toJson(): String {
        return """
{
  "timestamp": $timestamp,
  "date": "${getFormattedDate()}",
  "mode": "$mode",
  "confidence": $confidence,
  "riskLevel": "$riskLevel",
  "facesDetected": $facesDetected,
  "sessionDuration": "${getFormattedDuration()}",
  "averageScore": $averageScore,
  "peakScore": $peakScore,
  "framesProcessed": $framesProcessed,
  "inferenceCalls": $inferenceCalls
}
        """.trimIndent()
    }
}
