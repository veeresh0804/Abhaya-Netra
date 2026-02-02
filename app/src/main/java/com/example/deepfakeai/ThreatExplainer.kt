package com.example.deepfakeai

import android.util.Log

/**
 * Threat Explanation Engine
 * Generates detailed, human-readable explanations for deepfake detections
 */
class ThreatExplainer {

    data class ThreatExplanation(
        val summary: String,
        val technicalDetails: List<String>,
        val indicators: List<String>,
        val recommendations: List<String>,
        val confidence: String
    )

    /**
     * Generate comprehensive explanation for a threat assessment
     */
    fun explainThreat(
        videoScore: Float,
        audioScore: Float,
        fusedScore: Float,
        anomalyDetected: Boolean,
        hasVideo: Boolean,
        hasAudio: Boolean
    ): ThreatExplanation {
        
        val indicators = mutableListOf<String>()
        val technicalDetails = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        // Analyze video indicators
        if (hasVideo) {
            when {
                videoScore > 0.75f -> {
                    indicators.add("🔴 High facial manipulation probability")
                    technicalDetails.add("Visual Analysis: ${(videoScore * 100).toInt()}% manipulation likelihood detected")
                    technicalDetails.add("Common indicators: Unnatural eye movements, skin texture inconsistencies, lighting mismatches")
                }
                videoScore > 0.5f -> {
                    indicators.add("🟡 Moderate visual artifacts detected")
                    technicalDetails.add("Visual Analysis: ${(videoScore * 100).toInt()}% confidence - some suspicious patterns")
                }
                else -> {
                    indicators.add("🟢 Visual authenticity checks passed")
                }
            }
        }
        
        // Analyze audio indicators
        if (hasAudio) {
            when {
                audioScore > 0.75f -> {
                    indicators.add("🔴 High voice synthesis probability")
                    technicalDetails.add("Audio Analysis: ${(audioScore * 100).toInt()}% AI-generated voice likelihood")
                    technicalDetails.add("Common indicators: Unnatural prosody, frequency anomalies, timing inconsistencies")
                }
                audioScore > 0.5f -> {
                    indicators.add("🟡 Suspicious audio patterns detected")
                    technicalDetails.add("Audio Analysis: ${(audioScore * 100).toInt()}% confidence - voice irregularities present")
                }
                else -> {
                    indicators.add("🟢 Audio authenticity checks passed")
                }
            }
        }
        
        // Cross-modal analysis
        if (anomalyDetected && hasVideo && hasAudio) {
            indicators.add("⚠️ CRITICAL: Audio-Visual Mismatch Detected")
            technicalDetails.add("Cross-Modal Anomaly: Video and audio scores disagree significantly")
            
            if (audioScore > videoScore + 0.3f) {
                technicalDetails.add("Pattern: Likely audio deepfake overlay on authentic video")
                recommendations.add("Focus on verifying audio source - possible voice cloning attack")
            } else if (videoScore > audioScore + 0.3f) {
                technicalDetails.add("Pattern: Likely video manipulation with authentic audio")
                recommendations.add("Focus on visual verification - possible face swap or synthetic video")
            }
        }
        
        // Generate recommendations
        when {
            fusedScore > 0.7f -> {
                recommendations.add("⚠️ DO NOT ACT on this content without verification")
                recommendations.add("Cross-reference with trusted sources")
                recommendations.add("Contact the alleged source directly through known channels")
                recommendations.add("Report to appropriate authorities if impersonation suspected")
            }
            fusedScore > 0.4f -> {
                recommendations.add("⚠️ Exercise caution - treat as potentially manipulated")
                recommendations.add("Verify through alternative channels before acting")
                recommendations.add("Look for corroborating evidence from trusted sources")
            }
            else -> {
                recommendations.add("✅ Low risk detected, but remain vigilant")
                recommendations.add("Always verify sensitive information through multiple sources")
            }
        }
        
        // Build summary
        val summary = buildSummary(fusedScore, anomalyDetected, hasVideo, hasAudio)
        
        // Confidence statement
        val confidence = when {
            hasVideo && hasAudio -> "High Confidence (Multi-modal analysis)"
            hasVideo || hasAudio -> "Medium Confidence (Single-modal analysis)"
            else -> "Low Confidence (Insufficient data)"
        }
        
        Log.i("THREAT_EXPLAINER", "Generated explanation: ${indicators.size} indicators, ${recommendations.size} recommendations")
        
        return ThreatExplanation(
            summary = summary,
            technicalDetails = technicalDetails,
            indicators = indicators,
            recommendations = recommendations,
            confidence = confidence
        )
    }

    private fun buildSummary(score: Float, anomaly: Boolean, hasVideo: Boolean, hasAudio: Boolean): String {
        val level = when {
            score > 0.7f -> "HIGH RISK"
            score > 0.4f -> "MODERATE RISK"
            else -> "LOW RISK"
        }
        
        val modality = when {
            hasVideo && hasAudio -> "both video and audio analysis"
            hasVideo -> "video analysis only"
            hasAudio -> "audio analysis only"
            else -> "limited analysis"
        }
        
        val anomalyNote = if (anomaly) " with cross-modal inconsistencies detected" else ""
        
        return "$level of manipulation detected through $modality$anomalyNote."
    }

    /**
     * Get quick threat tip based on score
     */
    fun getQuickTip(fusedScore: Float): String {
        return when {
            fusedScore > 0.8f -> "🚨 Very high threat - likely synthetic media. Trust but verify through official channels."
            fusedScore > 0.6f -> "⚠️ High threat - significant manipulation indicators. Verify before sharing or acting."
            fusedScore > 0.4f -> "⚡ Moderate threat - some suspicious patterns. Exercise caution and verify claims."
            else -> "✅ Low threat detected. Continue with normal awareness and verify sensitive information."
        }
    }
}
