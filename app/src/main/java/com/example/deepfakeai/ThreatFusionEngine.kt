package com.example.deepfakeai

import android.util.Log
import kotlin.math.abs

/**
 * Multi-Modal Threat Fusion Engine
 * Combines video and audio deepfake scores with anomaly detection
 * for more robust threat assessment
 */
class ThreatFusionEngine {

    data class ThreatAssessment(
        val fusedScore: Float,          // Combined score (0.0 - 1.0)
        val anomalyDetected: Boolean,   // True if modalities disagree
        val confidence: Float,          // Confidence in the assessment
        val reasoning: String,          // Human-readable explanation
        val videoScore: Float,          // Individual video score
        val audioScore: Float           // Individual audio score
    )

    companion object {
        // Fusion weights (can be adjusted based on model performance)
        private const val VIDEO_WEIGHT = 0.6f
        private const val AUDIO_WEIGHT = 0.4f
        
        // Anomaly detection threshold (score difference)
        private const val ANOMALY_THRESHOLD = 0.4f
        
        // Temporal history for consistency checking
        private const val HISTORY_SIZE = 10
    }

    // Score history for temporal analysis
    private val videoHistory = ArrayDeque<Float>()
    private val audioHistory = ArrayDeque<Float>()
    private val fusedHistory = ArrayDeque<Float>()

    /**
     * Fuse video and audio scores into a single threat assessment
     */
    fun assessThreat(
        videoScore: Float,
        audioScore: Float,
        hasVideo: Boolean = true,
        hasAudio: Boolean = true
    ): ThreatAssessment {
        
        // Handle missing modalities
        val effectiveVideoScore = if (hasVideo) videoScore else 0.5f
        val effectiveAudioScore = if (hasAudio) audioScore else 0.5f
        
        // Weighted fusion
        val fusedScore = if (hasVideo && hasAudio) {
            (effectiveVideoScore * VIDEO_WEIGHT) + (effectiveAudioScore * AUDIO_WEIGHT)
        } else if (hasVideo) {
            effectiveVideoScore // Use only video
        } else if (hasAudio) {
            effectiveAudioScore // Use only audio
        } else {
            0.5f // No data
        }
        
        // Detect anomalies (cross-modal inconsistency)
        val anomalyDetected = if (hasVideo && hasAudio) {
            detectAnomaly(effectiveVideoScore, effectiveAudioScore)
        } else {
            false
        }
        
        // Calculate confidence based on agreement
        val confidence = if (hasVideo && hasAudio) {
            calculateConfidence(effectiveVideoScore, effectiveAudioScore)
        } else {
            0.7f // Lower confidence with single modality
        }
        
        // Update history
        updateHistory(effectiveVideoScore, effectiveAudioScore, fusedScore)
        
        // Generate reasoning
        val reasoning = generateReasoning(
            fusedScore,
            effectiveVideoScore,
            effectiveAudioScore,
            anomalyDetected,
            hasVideo,
            hasAudio
        )
        
        Log.i("FUSION_ENGINE", "Video: %.2f | Audio: %.2f | Fused: %.2f | Anomaly: %b".format(
            effectiveVideoScore, effectiveAudioScore, fusedScore, anomalyDetected
        ))
        
        return ThreatAssessment(
            fusedScore = fusedScore,
            anomalyDetected = anomalyDetected,
            confidence = confidence,
            reasoning = reasoning,
            videoScore = effectiveVideoScore,
            audioScore = effectiveAudioScore
        )
    }

    /**
     * Detect cross-modal anomalies
     * Returns true if video and audio scores significantly disagree
     */
    private fun detectAnomaly(videoScore: Float, audioScore: Float): Boolean {
        val difference = abs(videoScore - audioScore)
        
        // Anomaly patterns:
        // 1. High difference (one says real, other says fake)
        // 2. Video real but audio fake (likely audio deepfake overlay)
        // 3. Audio real but video fake (likely video manipulation)
        
        if (difference > ANOMALY_THRESHOLD) {
            val anomalyType = when {
                videoScore < 0.3f && audioScore > 0.7f -> "Audio-only manipulation suspected"
                videoScore > 0.7f && audioScore < 0.3f -> "Video-only manipulation suspected"
                else -> "Cross-modal inconsistency detected"
            }
            
            Log.w("FUSION_ENGINE", "⚠️ ANOMALY: $anomalyType | Diff: %.2f".format(difference))
            return true
        }
        
        return false
    }

    /**
     * Calculate confidence based on cross-modal agreement
     * Higher agreement = higher confidence
     */
    private fun calculateConfidence(videoScore: Float, audioScore: Float): Float {
        val agreement = 1.0f - abs(videoScore - audioScore)
        
        // Scale confidence: perfect agreement (diff=0) → 1.0, max disagreement (diff=1) → 0.5
        return 0.5f + (agreement * 0.5f)
    }

    /**
     * Generate human-readable reasoning for the assessment
     */
    private fun generateReasoning(
        fusedScore: Float,
        videoScore: Float,
        audioScore: Float,
        anomalyDetected: Boolean,
        hasVideo: Boolean,
        hasAudio: Boolean
    ): String {
        val parts = mutableListOf<String>()
        
        // Overall assessment
        parts.add(when {
            fusedScore > 0.7f -> "High manipulation probability"
            fusedScore > 0.4f -> "Moderate manipulation indicators"
            else -> "Low manipulation risk"
        })
        
        // Modality breakdown
        if (hasVideo && hasAudio) {
            parts.add("(Video: ${(videoScore * 100).toInt()}%, Audio: ${(audioScore * 100).toInt()}%)")
        } else if (hasVideo) {
            parts.add("(Video-only analysis)")
        } else if (hasAudio) {
            parts.add("(Audio-only analysis)")
        }
        
        // Anomaly warning
        if (anomalyDetected) {
            parts.add("⚠️ Cross-modal anomaly detected - verify manually")
        }
        
        return parts.joinToString(" ")
    }

    /**
     * Update temporal history for trend analysis
     */
    private fun updateHistory(videoScore: Float, audioScore: Float, fusedScore: Float) {
        videoHistory.addLast(videoScore)
        audioHistory.addLast(audioScore)
        fusedHistory.addLast(fusedScore)
        
        while (videoHistory.size > HISTORY_SIZE) videoHistory.removeFirst()
        while (audioHistory.size > HISTORY_SIZE) audioHistory.removeFirst()
        while (fusedHistory.size > HISTORY_SIZE) fusedHistory.removeFirst()
    }

    /**
     * Get temporal trend (rising, falling, stable)
     */
    fun getTrend(): String {
        if (fusedHistory.size < 3) return "Insufficient data"
        
        val recent = fusedHistory.takeLast(3)
        val avg = recent.average().toFloat()
        val first = recent.first()
        val last = recent.last()
        
        return when {
            last - first > 0.1f -> "⬆️ Rising threat"
            first - last > 0.1f -> "⬇️ Decreasing threat"
            else -> "➡️ Stable"
        }
    }

    /**
     * Reset history (call when changing modes or starting new session)
     */
    fun reset() {
        videoHistory.clear()
        audioHistory.clear()
        fusedHistory.clear()
        Log.i("FUSION_ENGINE", "History reset")
    }
}
