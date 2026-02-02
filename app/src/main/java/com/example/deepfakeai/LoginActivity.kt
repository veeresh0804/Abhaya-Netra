package com.example.deepfakeai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etPin = findViewById<TextInputEditText>(R.id.etPin)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvError = findViewById<TextView>(R.id.tvError)

        btnLogin.setOnClickListener {
            val pin = etPin.text.toString()
            if (isValidPin(pin)) {
                tvError.visibility = View.INVISIBLE
                navigateToMain()
            } else {
                tvError.visibility = View.VISIBLE
                etPin.text?.clear()
            }
        }
    }

    private fun isValidPin(pin: String): Boolean {
        // Hardcoded PIN for Demo
        return pin == "1234"
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        // Clear back stack so user can't go back to login by pressing back
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
