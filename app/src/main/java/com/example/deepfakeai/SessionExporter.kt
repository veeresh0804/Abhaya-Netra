package com.example.deepfakeai

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for exporting session data as JSON reports
 * Supports sharing reports via Android share sheet
 */
class SessionExporter {
    
    /**
     * Export session statistics as a JSON file
     * @param context Android context
     * @param stats Session statistics object
     * @param riskLevel Current risk level classification
     * @return File object pointing to the created JSON report
     */
    fun exportSessionAsJSON(
        context: Context,
        stats: MainActivity.SessionStats,
        riskLevel: MainActivity.RiskLevel,
        screenshotHelper: ScreenshotHelper? = null
    ): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "abhaya_netra_session_$timestamp.json"
        
        val json = JSONObject().apply {
            put("export_timestamp", System.currentTimeMillis())
            put("export_date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            put("app_version", "1.0.0")
            put("app_name", "Abhaya-Netra")
            put("report_type", "Evidence-Grade Deepfake Detection Report")
            
            put("session_data", JSONObject().apply {
                put("duration_ms", stats.sessionDurationMs)
                put("duration_seconds", stats.sessionDurationMs / 1000)
                put("frames_processed", stats.framesProcessed)
                put("faces_detected", stats.facesDetected)
                put("inference_calls", stats.inferenceCalls)
                
                put("risk_distribution", JSONObject().apply {
                    put("low_risk_count", stats.lowRiskCount)
                    put("suspicious_risk_count", stats.suspiciousRiskCount)
                    put("high_risk_count", stats.highRiskCount)
                })
                
                put("confidence_metrics", JSONObject().apply {
                    put("average_score", String.format("%.4f", stats.averageScore))
                    put("peak_score", String.format("%.4f", stats.peakScore))
                    put("average_percentage", (stats.averageScore * 100).toInt())
                    put("peak_percentage", (stats.peakScore * 100).toInt())
                })
                
                put("final_assessment", JSONObject().apply {
                    put("risk_level", riskLevel.displayName)
                    put("explanation", riskLevel.explanation)
                })
            })
            
            // Evidence section with screenshots
            put("evidence", JSONObject().apply {
                screenshotHelper?.let { helper ->
                    val screenshots = helper.getAllScreenshots(context)
                    put("screenshot_count", screenshots.size)
                    put("storage_size_mb", String.format("%.2f", helper.getStorageSize(context)))
                    
                    if (screenshots.isNotEmpty()) {
                       put("screenshots", org.json.JSONArray().apply {
                            screenshots.take(10).forEach { file -> // Limit to 10 most recent
                                put(JSONObject().apply {
                                    put("filename", file.name)
                                    put("path", file.absolutePath)
                                    put("size_kb", file.length() / 1024)
                                    put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                        .format(Date(file.lastModified())))
                                })
                            }
                        })
                    }
                }
            })
            
            put("metadata", JSONObject().apply {
                put("device_model", android.os.Build.MODEL)
                put("android_version", android.os.Build.VERSION.RELEASE)
                put("offline_mode", true) // Always true for Abhaya-Netra
                put("multi_modal_fusion", true)
                put("evidence_grade", true)
            })
        }
        
        // Write to app's external files directory (no permission needed)
        val file = File(context.getExternalFilesDir(null), fileName)
        file.writeText(json.toString(2)) // Pretty print with 2-space indent
        
        return file
    }
    
    /**
     * Share session report via Android share sheet
     * @param context Android context
     * @param file JSON report file to share
     */
    fun shareSessionReport(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Abhaya-Netra Deepfake Detection Report")
            putExtra(Intent.EXTRA_TEXT, "Deepfake detection session report from Abhaya-Netra")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(shareIntent, "Share Detection Report"))
    }
}
