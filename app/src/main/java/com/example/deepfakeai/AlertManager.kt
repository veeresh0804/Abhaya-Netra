package com.example.deepfakeai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/**
 * Alert Manager for threat notifications
 * Handles sound alerts and enhanced vibration patterns
 */
class AlertManager(private val context: Context) {

    private val vibrator: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 80) // 80% volume
        } catch (e: Exception) {
            Log.e("ALERT_MANAGER", "Failed to initialize tone generator", e)
        }
    }

    /**
     * Trigger alert for risk level
     */
    fun triggerAlert(riskLevel: MainActivity.RiskLevel, enableSound: Boolean = true) {
        when (riskLevel) {
            MainActivity.RiskLevel.LOW -> {
                // No alert for low risk
            }
            MainActivity.RiskLevel.SUSPICIOUS -> {
                vibratePattern(PATTERN_SUSPICIOUS)
                if (enableSound) playWarningTone()
            }
            MainActivity.RiskLevel.HIGH -> {
                vibratePattern(PATTERN_HIGH_RISK)
                if (enableSound) playAlertTone()
            }
        }
    }

    /**
     * Play warning tone (short beep)
     */
    private fun playWarningTone() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200) // 200ms beep
            Log.d("ALERT_MANAGER", "Warning tone played")
        } catch (e: Exception) {
            Log.e("ALERT_MANAGER", "Error playing warning tone", e)
        }
    }

    /**
     * Play alert tone (urgent beeps)
     */
    private fun playAlertTone() {
        try {
            // Double beep pattern for high risk
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
            Log.d("ALERT_MANAGER", "Alert tone played")
        } catch (e: Exception) {
            Log.e("ALERT_MANAGER", "Error playing alert tone", e)
        }
    }

    /**
     * Vibrate with pattern
     */
    private fun vibratePattern(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.e("ALERT_MANAGER", "Error vibrating", e)
        }
    }

    /**
     * Single short vibration for feedback
     */
    fun vibrateShort() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        } catch (e: Exception) {
            Log.e("ALERT_MANAGER", "Error vibrating", e)
        }
    }

    /**
     * Cleanup resources
     */
    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e("ALERT_MANAGER", "Error releasing tone generator", e)
        }
    }

    companion object {
        // Vibration patterns: [delay, vibrate, pause, vibrate, ...]
        
        // Suspicious: Single long pulse
        private val PATTERN_SUSPICIOUS = longArrayOf(0, 400)
        
        // High Risk: Double pulse (urgent)
        private val PATTERN_HIGH_RISK = longArrayOf(0, 200, 100, 200)
        
        // Critical: Triple pulse (very urgent)
        private val PATTERN_CRITICAL = longArrayOf(0, 150, 80, 150, 80, 150)
    }
}
