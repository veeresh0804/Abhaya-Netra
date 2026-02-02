package com.example.deepfakeai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import android.database.Cursor
import android.provider.ContactsContract
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var isIncomingCall = false
        private var callNumber: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val state = when (stateStr) {
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            else -> return
        }

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // Incoming call detected
                isIncomingCall = true
                callNumber = number
                
                val isUnknown = !isNumberInContacts(context, number)
                
                Log.i("CALL_DETECTION", "Incoming call from: ${number ?: "Unknown"} | Unknown: $isUnknown")
                
                if (isUnknown) {
                    // Show notification or prepare for monitoring
                    Log.w("CALL_THREAT", "⚠️ UNKNOWN CALLER DETECTED: $number")
                }
            }
            
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call answered
                if (lastState == TelephonyManager.CALL_STATE_RINGING && isIncomingCall) {
                    val isUnknown = !isNumberInContacts(context, callNumber)
                    
                    Log.i("CALL_DETECTION", "Call answered | Unknown: $isUnknown")
                    
                    if (isUnknown) {
                        // Start monitoring service
                        startCallMonitoring(context, callNumber)
                    }
                }
            }
            
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended
                if (lastState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    Log.i("CALL_DETECTION", "Call ended")
                    stopCallMonitoring(context)
                }
                isIncomingCall = false
                callNumber = null
            }
        }

        lastState = state
    }

    private fun isNumberInContacts(context: Context, number: String?): Boolean {
        if (number.isNullOrEmpty()) return false
        
        // Check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            return false // Treat as unknown if no permission
        }

        var cursor: Cursor? = null
        try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            
            cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            
            if (cursor != null && cursor.moveToFirst()) {
                val name = cursor.getString(0)
                Log.i("CALL_DETECTION", "Contact found: $name")
                return true
            }
        } catch (e: Exception) {
            Log.e("CALL_DETECTION", "Error checking contacts", e)
        } finally {
            cursor?.close()
        }
        
        return false
    }

    private fun startCallMonitoring(context: Context, number: String?) {
        val intent = Intent(context, CallMonitorService::class.java).apply {
            putExtra("caller_number", number ?: "Unknown")
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        
        Log.i("CALL_MONITORING", "Started monitoring service for unknown caller")
    }

    private fun stopCallMonitoring(context: Context) {
        val intent = Intent(context, CallMonitorService::class.java)
        context.stopService(intent)
        Log.i("CALL_MONITORING", "Stopped monitoring service")
    }
}
