package com.example.deepfakeai

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class CallMonitorService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    
    private var audioHelper: AudioHelper? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    // UI Elements
    private var riskIndicatorBar: View? = null
    private var tvCallRiskStatus: TextView? = null
    private var tvCallConfidence: TextView? = null
    private var btnEmergencyEndCall: Button? = null
    
    private var callerNumber: String = "Unknown"

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        createOverlayView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(2, createNotification())
        
        callerNumber = intent?.getStringExtra("caller_number") ?: "Unknown"
        Log.i("CALL_MONITOR", "Monitoring call from: $callerNumber")
        
        // Start audio analysis
        startAudioMonitoring()
        
        return START_STICKY
    }

    @SuppressLint("InflateParams")
    private fun createOverlayView() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.call_overlay_layout, null)
        
        // Get UI references
        riskIndicatorBar = overlayView?.findViewById(R.id.riskIndicatorBar)
        tvCallRiskStatus = overlayView?.findViewById(R.id.tvCallRiskStatus)
        tvCallConfidence = overlayView?.findViewById(R.id.tvCallConfidence)
        btnEmergencyEndCall = overlayView?.findViewById(R.id.btnEmergencyEndCall)
        
        // Emergency button handler
        btnEmergencyEndCall?.setOnClickListener {
            Log.w("CALL_MONITOR", "Emergency call termination requested by user")
            // In a real implementation, you'd use Telecom API to end call
            // For demo, just show toast
            android.widget.Toast.makeText(this, "⚠️ High risk detected - Call should be ended", android.widget.Toast.LENGTH_LONG).show()
            
            // Stop monitoring
            stopSelf()
        }
        
        // Window parameters
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 0
        }
        
        try {
            windowManager.addView(overlayView, overlayParams)
            Log.i("CALL_MONITOR", "Call overlay displayed")
        } catch (e: Exception) {
            Log.e("CALL_MONITOR", "Error showing overlay", e)
        }
    }

    private fun startAudioMonitoring() {
        audioHelper = AudioHelper { score, risk ->
            // Update UI with real-time audio analysis
            updateOverlay(score, risk)
        }
        
        serviceScope.launch {
            audioHelper?.startListening()
        }
    }

    private fun updateOverlay(confidence: Float, riskLevel: MainActivity.RiskLevel) {
        // Update risk indicator bar color
        val color = when (riskLevel) {
            MainActivity.RiskLevel.LOW -> 0xFF4CAF50.toInt()      // Green
            MainActivity.RiskLevel.SUSPICIOUS -> 0xFFFFC107.toInt() // Yellow
            MainActivity.RiskLevel.HIGH -> 0xFFF44336.toInt()       // Red
        }
        riskIndicatorBar?.setBackgroundColor(color)
        
        // Update status text
        tvCallRiskStatus?.text = when (riskLevel) {
            MainActivity.RiskLevel.LOW -> "Voice authentication: Normal"
            MainActivity.RiskLevel.SUSPICIOUS -> "⚠️ Potential voice manipulation"
            MainActivity.RiskLevel.HIGH -> "🚨 HIGH RISK: Possible deepfake"
        }
        tvCallRiskStatus?.setTextColor(color)
        
        // Update confidence
        val percentage = (confidence * 100).toInt()
        tvCallConfidence?.text = "Deepfake Confidence: $percentage%"
        
        // Show emergency button for high risk
        if (riskLevel == MainActivity.RiskLevel.HIGH) {
            btnEmergencyEndCall?.visibility = View.VISIBLE
            
            // Vibrate to alert user
            val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
            
            Log.w("CALL_THREAT", "🚨 HIGH RISK call detected: $callerNumber | Confidence: $percentage%")
        } else {
            btnEmergencyEndCall?.visibility = View.GONE
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "call_monitor_channel",
                "Call Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoring active call for threats"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "call_monitor_channel")
            .setContentTitle("📞 Call Monitoring Active")
            .setContentText("Analyzing call from $callerNumber")
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Stop audio monitoring
        audioHelper?.stopListening()
        serviceScope.cancel()
        
        // Remove overlay
        try {
            overlayView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e("CALL_MONITOR", "Error removing overlay", e)
        }
        
        Log.i("CALL_MONITOR", "Call monitoring stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
