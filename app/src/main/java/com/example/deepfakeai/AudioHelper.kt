package com.example.deepfakeai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.log10
import kotlin.random.Random

class AudioHelper(private val onRiskUpdate: (Float, MainActivity.RiskLevel) -> Unit) {

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    @SuppressLint("MissingPermission")
    suspend fun startListening() = withContext(Dispatchers.IO) {
        if (isRecording.get()) return@withContext

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AUDIO_HELPER", "AudioRecord initialization failed")
                return@withContext
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            Log.i("AUDIO_HELPER", "Audio recording started")

            val buffer = ShortArray(bufferSize)
            
            while (isRecording.get() && isActive) {
                val readCount = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (readCount > 0) {
                    // Process audio chunk
                    val riskScore = analyzeAudioChunk(buffer, readCount)
                    
                    // Map score to risk level (using same thresholds as video for consistency)
                    // High risk > 0.65, Low Risk < 0.35
                    val riskLevel = when {
                         riskScore > 0.65f -> MainActivity.RiskLevel.HIGH
                         riskScore > 0.35f -> MainActivity.RiskLevel.SUSPICIOUS
                         else -> MainActivity.RiskLevel.LOW
                    }
                    
                    onRiskUpdate(riskScore, riskLevel)
                }
            }
        } catch (e: Exception) {
            Log.e("AUDIO_HELPER", "Error in audio recording loop", e)
        } finally {
            stopListening()
        }
    }

    fun stopListening() {
        if (!isRecording.getAndSet(false)) return
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.i("AUDIO_HELPER", "Audio recording stopped")
        } catch (e: Exception) {
            Log.e("AUDIO_HELPER", "Error stopping audio recorder", e)
        }
    }

    private fun analyzeAudioChunk(buffer: ShortArray, size: Int): Float {
        // Placeholder for actual Audio Deepfake Inference
        // Real implementation would convert to Spectrogram/MFCC and run TFLite model
        
        // Simulating logic:
        // 1. Calculate RMS (Volume) to detect silence
        var sum = 0.0
        for (i in 0 until size) {
            sum += buffer[i] * buffer[i]
        }
        val rms = kotlin.math.sqrt(sum / size)
        
        // If silence, return low risk
        if (rms < 100) return 0.1f
        
        // 2. Mock Interference/Artifact detection
        // In reality, deepfakes often have high-frequency artifacts or metadata anomalies
        // We'll simulate a risk score that fluctuates
        // This effectively just demonstrates the UI path working
        
        // Randomly generating a score between 0.1 and 0.4 (Low Risk) normally
        // Occasionally spike for demo purposes if desired, but default to safe for now
        // To make it testable: High volume + specific frequency patterns would trigger it
        
        // Return a safe baseline with slight jitter
        return 0.2f + (Random.nextFloat() * 0.1f)
    }
}
