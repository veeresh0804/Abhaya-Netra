package com.example.deepfakeai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper class for capturing screenshots as evidence
 * Automatically captures screen on high-risk detections
 */
class ScreenshotHelper {

    companion object {
        private const val SCREENSHOTS_DIR = "screenshots"
        private const val MAX_SCREENSHOTS = 50 // Prevent storage overflow
    }

    /**
     * Capture screenshot of a view
     */
    fun captureView(view: View, context: Context, prefix: String = "evidence"): File? {
        try {
            // Enable drawing cache
            view.isDrawingCacheEnabled = true
            view.buildDrawingCache(true)
            
            // Get bitmap
            val bitmap = Bitmap.createBitmap(view.drawingCache)
            view.isDrawingCacheEnabled = false
            
            // Save to file
            return saveBitmap(bitmap, context, prefix)
        } catch (e: Exception) {
            Log.e("SCREENSHOT", "Error capturing view", e)
            return null
        }
    }

    /**
     * Capture entire screen using window decor view
     */
    fun captureScreen(activity: android.app.Activity, prefix: String = "evidence"): File? {
        try {
            val rootView = activity.window.decorView.rootView
            rootView.isDrawingCacheEnabled = true
            rootView.buildDrawingCache(true)
            
            val bitmap = Bitmap.createBitmap(rootView.drawingCache)
            rootView.isDrawingCacheEnabled = false
            
            return saveBitmap(bitmap, activity, prefix)
        } catch (e: Exception) {
            Log.e("SCREENSHOT", "Error capturing screen", e)
            return null
        }
    }

    /**
     * Save bitmap to file
     */
    private fun saveBitmap(bitmap: Bitmap, context: Context, prefix: String): File? {
        try {
            // Create screenshots directory
            val screenshotsDir = File(context.getExternalFilesDir(null), SCREENSHOTS_DIR)
            if (!screenshotsDir.exists()) {
                screenshotsDir.mkdirs()
            }
            
            // Clean up old screenshots if too many
            cleanupOldScreenshots(screenshotsDir)
            
            // Generate filename with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
            val filename = "${prefix}_${timestamp}.jpg"
            val file = File(screenshotsDir, filename)
            
            // Save bitmap
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.flush()
            }
            
            Log.i("SCREENSHOT", "Screenshot saved: ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e("SCREENSHOT", "Error saving bitmap", e)
            return null
        }
    }

    /**
     * Clean up old screenshots to prevent storage overflow
     */
    private fun cleanupOldScreenshots(directory: File) {
        try {
            val files = directory.listFiles()?.sortedByDescending { it.lastModified() } ?: return
            
            // Delete oldest files if exceeding limit
            if (files.size > MAX_SCREENSHOTS) {
                files.drop(MAX_SCREENSHOTS).forEach { file ->
                    file.delete()
                    Log.d("SCREENSHOT", "Deleted old screenshot: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e("SCREENSHOT", "Error cleaning up screenshots", e)
        }
    }

    /**
     * Get all captured screenshots
     */
    fun getAllScreenshots(context: Context): List<File> {
        val screenshotsDir = File(context.getExternalFilesDir(null), SCREENSHOTS_DIR)
        if (!screenshotsDir.exists()) return emptyList()
        
        return screenshotsDir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() 
            ?: emptyList()
    }

    /**
     * Delete all screenshots
     */
    fun clearAllScreenshots(context: Context): Boolean {
        try {
            val screenshotsDir = File(context.getExternalFilesDir(null), SCREENSHOTS_DIR)
            if (screenshotsDir.exists()) {
                screenshotsDir.deleteRecursively()
                Log.i("SCREENSHOT", "All screenshots cleared")
                return true
            }
        } catch (e: Exception) {
            Log.e("SCREENSHOT", "Error clearing screenshots", e)
        }
        return false
    }

    /**
     * Get screenshot storage size in MB
     */
    fun getStorageSize(context: Context): Float {
        val screenshotsDir = File(context.getExternalFilesDir(null), SCREENSHOTS_DIR)
        if (!screenshotsDir.exists()) return 0f
        
        var totalSize = 0L
        screenshotsDir.listFiles()?.forEach { file ->
            totalSize += file.length()
        }
        
        return totalSize / (1024f * 1024f) // Convert to MB
    }
}
