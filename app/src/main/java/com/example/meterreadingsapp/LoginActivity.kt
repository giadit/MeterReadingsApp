package com.example.meterreadingsapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.meterreadingsapp.databinding.ActivityLoginBinding // Updated package for binding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    // SharedPreferences for "Remember Me" functionality
    private val PREFS_NAME = "LoginPrefs"
    private val KEY_USERNAME = "username"
    private val KEY_PASSWORD = "password"
    private val KEY_REMEMBER_ME = "rememberMe"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide system UI (status bar and navigation bar)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    )
        }

        // Load saved credentials if "Remember Me" was checked
        loadCredentials()

        binding.loginButton.setOnClickListener {
            performLogin()
        }
    }

    private fun loadCredentials() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false)
        binding.rememberMeCheckBox.isChecked = rememberMe

        if (rememberMe) {
            binding.usernameEditText.setText(prefs.getString(KEY_USERNAME, ""))
            binding.passwordEditText.setText(prefs.getString(KEY_PASSWORD, ""))
        }
    }

    private fun saveCredentials(username: String, password: String, rememberMe: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean(KEY_REMEMBER_ME, rememberMe)
            if (rememberMe) {
                putString(KEY_USERNAME, username)
                putString(KEY_PASSWORD, password)
            } else {
                // Clear saved credentials if remember me is unchecked
                remove(KEY_USERNAME)
                remove(KEY_PASSWORD)
            }
            apply()
        }
    }

    private fun performLogin() {
        val username = binding.usernameEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()
        val rememberMe = binding.rememberMeCheckBox.isChecked

        // --- Dummy Login Logic (Replace with actual authentication later) ---
        // For now, let's just check if fields are not empty
        if (username.isNotEmpty() && password.isNotEmpty()) {
            saveCredentials(username, password, rememberMe)
            Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()

            // Navigate to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Finish LoginActivity so user can't go back to it with back button
        } else {
            Toast.makeText(this, getString(R.string.login_error_empty_fields), Toast.LENGTH_SHORT).show()
        }

        // Suggestions for later:
        // - Integrate with an authentication service (e.g., Firebase Auth, Supabase Auth, your backend API)
        // - Hash passwords before storing (even locally, for "remember me")
        // - Implement password complexity rules
        // - Handle network errors during authentication
        // - Show loading spinner during login
    }
}
