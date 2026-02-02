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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.android.material.progressindicator.CircularProgressIndicator

class SentinelOrbService : Service() {

    private lateinit var windowManager: WindowManager
    private var orbView: View? = null
    private var expandedView: View? = null
    
    private var orbParams: WindowManager.LayoutParams? = null
    private var expandedParams: WindowManager.LayoutParams? = null
    
    private var isExpanded = false
    
    // UI Elements
    private var confidenceRing: CircularProgressIndicator? = null
    private var orbIcon: ImageView? = null
    private var pulseOverlay: View? = null
    
    // Expanded panel elements
    private var tvFacesDetected: TextView? = null
    private var tvRiskLevel: TextView? = null
    private var tvAudioStatus: TextView? = null

    companion object {
        const val ACTION_UPDATE_CONFIDENCE = "UPDATE_CONFIDENCE"
        const val EXTRA_CONFIDENCE = "confidence"
        const val EXTRA_RISK_LEVEL = "risk_level"
        const val EXTRA_FACES = "faces"
        const val EXTRA_AUDIO_STATUS = "audio_status"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        createOrbView()
        createExpandedView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        
        // Handle updates from MainActivity
        intent?.let {
            when (it.action) {
                ACTION_UPDATE_CONFIDENCE -> {
                    val confidence = it.getFloatExtra(EXTRA_CONFIDENCE, 0f)
                    val riskLevel = it.getStringExtra(EXTRA_RISK_LEVEL) ?: "Low"
                    val faces = it.getIntExtra(EXTRA_FACES, 0)
                    val audioStatus = it.getStringExtra(EXTRA_AUDIO_STATUS) ?: "Inactive"
                    
                    updateUI(confidence, riskLevel, faces, audioStatus)
                }
            }
        }
        
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOrbView() {
        orbView = LayoutInflater.from(this).inflate(R.layout.sentinel_orb_layout, null)
        
        // Get UI references
        confidenceRing = orbView?.findViewById(R.id.confidenceRing)
        orbIcon = orbView?.findViewById(R.id.orbIcon)
        pulseOverlay = orbView?.findViewById(R.id.pulseOverlay)
        
        // Window parameters
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        orbParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }
        
        // Touch listener for dragging and tap detection
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoved = false
        
        orbView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = orbParams?.x ?: 0
                    initialY = orbParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isMoved = true
                        orbParams?.x = initialX + deltaX.toInt()
                        orbParams?.y = initialY + deltaY.toInt()
                        windowManager.updateViewLayout(orbView, orbParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoved) {
                        // Tap detected - toggle expanded view
                        toggleExpanded()
                    }
                    true
                }
                else -> false
            }
        }
        
        windowManager.addView(orbView, orbParams)
        Log.i("SENTINEL_ORB", "Orb view created and displayed")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createExpandedView() {
        expandedView = LayoutInflater.from(this).inflate(R.layout.sentinel_expanded_panel, null)
        
        // Get UI references
        tvFacesDetected = expandedView?.findViewById(R.id.tvFacesDetected)
        tvRiskLevel = expandedView?.findViewById(R.id.tvRiskLevel)
        tvAudioStatus = expandedView?.findViewById(R.id.tvAudioStatus)
        
        // Setup button listeners
        expandedView?.findViewById<Button>(R.id.btnQuickScanFromOrb)?.setOnClickListener {
            // Trigger quick scan (send broadcast to MainActivity)
            val intent = Intent("com.example.deepfakeai.QUICK_SCAN")
            sendBroadcast(intent)
            toggleExpanded()
        }
        
        expandedView?.findViewById<Button>(R.id.btnViewReports)?.setOnClickListener {
            // Open MainActivity and trigger export
            val intent = Intent("com.example.deepfakeai.VIEW_REPORTS")
            sendBroadcast(intent)
            toggleExpanded()
        }
        
        expandedView?.findViewById<Button>(R.id.btnOpenSettings)?.setOnClickListener {
            // Open MainActivity settings
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            toggleExpanded()
        }
        
        expandedView?.findViewById<Button>(R.id.btnMinimize)?.setOnClickListener {
            toggleExpanded()
        }
        
        // Window parameters (initially hidden)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        expandedParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200  // Below the orb
        }
    }

    private fun toggleExpanded() {
        isExpanded = !isExpanded
        
        if (isExpanded) {
            // Show expanded panel
            expandedParams?.x = orbParams?.x ?: 100
            expandedParams?.y = (orbParams?.y ?: 100) + 100
            
            try {
                windowManager.addView(expandedView, expandedParams)
            } catch (e: Exception) {
                Log.e("SENTINEL_ORB", "Error showing expanded view", e)
            }
        } else {
            // Hide expanded panel
            try {
                windowManager.removeView(expandedView)
            } catch (e: Exception) {
                Log.e("SENTINEL_ORB", "Error hiding expanded view", e)
            }
        }
    }

    private fun updateUI(confidence: Float, riskLevel: String, faces: Int, audioStatus: String) {
        val progressValue = (confidence * 100).toInt()
        confidenceRing?.progress = progressValue
        
        // Update ring color based on risk level
        val color = when {
            riskLevel.contains("High", ignoreCase = true) -> 0xFFF44336.toInt()       // Red
            riskLevel.contains("Suspicious", ignoreCase = true) -> 0xFFFFC107.toInt() // Yellow
            riskLevel.contains("Low", ignoreCase = true) -> 0xFF4CAF50.toInt()        // Green
            riskLevel.contains("No Face", ignoreCase = true) -> 0xFF9E9E9E.toInt()    // Gray
            else -> 0xFF4CAF50.toInt()
        }
        confidenceRing?.setIndicatorColor(color)
        
        // Update expanded panel if visible
        tvFacesDetected?.text = "👤 Faces: $faces"
        tvRiskLevel?.text = "⚠️ Risk: $riskLevel"
        tvRiskLevel?.setTextColor(color)
        tvAudioStatus?.text = "🎤 Audio: $audioStatus"
        
        // Pulse animation for high risk
        if (riskLevel.contains("High", ignoreCase = true)) {
            startPulseAnimation()
        } else {
            stopPulseAnimation()
        }
        
        Log.i("SENTINEL_ORB", "UI updated: confidence=$progressValue%, risk=$riskLevel, faces=$faces")
    }


    private fun startPulseAnimation() {
        pulseOverlay?.visibility = View.VISIBLE
        pulseOverlay?.alpha = 0f
        pulseOverlay?.animate()
            ?.alpha(1f)
            ?.setDuration(500)
            ?.withEndAction {
                pulseOverlay?.animate()?.alpha(0f)?.setDuration(500)?.withEndAction {
                    if (pulseOverlay?.visibility == View.VISIBLE) {
                        startPulseAnimation() // Loop
                    }
                }?.start()
            }?.start()
    }

    private fun stopPulseAnimation() {
        pulseOverlay?.visibility = View.GONE
        pulseOverlay?.clearAnimation()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "sentinel_channel",
                "Sentinel Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Abhaya-Netra Sentinel is monitoring for threats"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "sentinel_channel")
            .setContentTitle("🛡️ Sentinel Active")
            .setContentText("Monitoring for deepfake threats")
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            orbView?.let { windowManager.removeView(it) }
            if (isExpanded) {
                expandedView?.let { windowManager.removeView(it) }
            }
        } catch (e: Exception) {
            Log.e("SENTINEL_ORB", "Error removing views", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
