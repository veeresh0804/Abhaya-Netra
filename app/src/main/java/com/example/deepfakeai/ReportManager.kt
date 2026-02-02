package com.example.deepfakeai

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages saving and loading detection reports
 */
class ReportManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ReportManager"
        private const val REPORTS_FILE = "detection_reports.json"
        private const val MAX_REPORTS = 100 // Keep only last 100 reports
    }
    
    /**
     * Save a detection report
     */
    fun saveReport(report: DetectionReport) {
        try {
            val reports = loadAllReports().toMutableList()
            reports.add(0, report) // Add to beginning (most recent first)
            
            // Keep only MAX_REPORTS
            if (reports.size > MAX_REPORTS) {
                reports.subList(MAX_REPORTS, reports.size).clear()
            }
            
            // Save to file
            val jsonArray = JSONArray()
            reports.forEach { jsonArray.put(reportToJson(it)) }
            
            val file = File(context.filesDir, REPORTS_FILE)
            file.writeText(jsonArray.toString(2))
            
            Log.i(TAG, "Report saved successfully. Total reports: ${reports.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save report", e)
        }
    }
    
    /**
     * Load all saved reports
     */
    fun loadAllReports(): List<DetectionReport> {
        try {
            val file = File(context.filesDir, REPORTS_FILE)
            if (!file.exists()) {
                return emptyList()
            }
            
            val jsonString = file.readText()
            val jsonArray = JSONArray(jsonString)
            val reports = mutableListOf<DetectionReport>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                reports.add(jsonToReport(jsonObject))
            }
            
            return reports
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load reports", e)
            return emptyList()
        }
    }
    
    /**
     * Get summary statistics
     */
    fun getStatistics(): Map<String, Any> {
        val reports = loadAllReports()
        if (reports.isEmpty()) {
            return mapOf(
                "totalReports" to 0,
                "averageConfidence" to 0f,
                "highRiskCount" to 0,
                "lowRiskCount" to 0
            )
        }
        
        val highRiskCount = reports.count { it.riskLevel.contains("High", ignoreCase = true) }
        val lowRiskCount = reports.count { it.riskLevel.contains("Low", ignoreCase = true) }
        val avgConfidence = reports.map { it.confidence }.average().toFloat()
        
        return mapOf(
            "totalReports" to reports.size,
            "averageConfidence" to avgConfidence,
            "highRiskCount" to highRiskCount,
            "lowRiskCount" to lowRiskCount,
            "suspiciousCount" to (reports.size - highRiskCount - lowRiskCount),
            "totalFacesDetected" to reports.sumOf { it.facesDetected },
            "totalSessionTime" to reports.sumOf { it.sessionDuration }
        )
    }
    
    /**
     * Export reports to shareable file
     */
    fun exportReports(): Intent? {
        try {
            val reports = loadAllReports()
            if (reports.isEmpty()) {
                return null
            }
            
            // Create export file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "abhaya_netra_reports_$timestamp.json"
            val exportFile = File(context.cacheDir, fileName)
            
            // Build JSON content
            val jsonObject = JSONObject()
            jsonObject.put("appName", "Abhaya-Netra")
            jsonObject.put("exportDate", SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(Date()))
            jsonObject.put("totalReports", reports.size)
            jsonObject.put("statistics", JSONObject(getStatistics()))
            
            val reportsArray = JSONArray()
            reports.forEach { reportsArray.put(reportToJson(it)) }
            jsonObject.put("reports", reportsArray)
            
            exportFile.writeText(jsonObject.toString(2))
            
            // Create share intent
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile
            )
            
            return Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Abhaya-Netra Detection Reports")
                putExtra(Intent.EXTRA_TEXT, "Detection reports from Abhaya-Netra deepfake detection app")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export reports", e)
            return null
        }
    }
    
    /**
     * Clear all reports
     */
    fun clearAllReports() {
        try {
            val file = File(context.filesDir, REPORTS_FILE)
            file.delete()
            Log.i(TAG, "All reports cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear reports", e)
        }
    }
    
    // Helper functions for JSON serialization
    private fun reportToJson(report: DetectionReport): JSONObject {
        return JSONObject().apply {
            put("timestamp", report.timestamp)
            put("mode", report.mode)
            put("confidence", report.confidence)
            put("riskLevel", report.riskLevel)
            put("facesDetected", report.facesDetected)
            put("sessionDuration", report.sessionDuration)
            put("averageScore", report.averageScore)
            put("peakScore", report.peakScore)
            put("framesProcessed", report.framesProcessed)
            put("inferenceCalls", report.inferenceCalls)
        }
    }
    
    private fun jsonToReport(json: JSONObject): DetectionReport {
        return DetectionReport(
            timestamp = json.getLong("timestamp"),
            mode = json.getString("mode"),
            confidence = json.getDouble("confidence").toFloat(),
            riskLevel = json.getString("riskLevel"),
            facesDetected = json.getInt("facesDetected"),
            sessionDuration = json.getLong("sessionDuration"),
            averageScore = json.getDouble("averageScore").toFloat(),
            peakScore = json.getDouble("peakScore").toFloat(),
            framesProcessed = json.getInt("framesProcessed"),
            inferenceCalls = json.getInt("inferenceCalls")
        )
    }
}
