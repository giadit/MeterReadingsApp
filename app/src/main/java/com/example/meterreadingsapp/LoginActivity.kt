package com.example.meterreadingsapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.api.RetrofitClient
import com.example.meterreadingsapp.api.SessionManager // CORRECT IMPORT
import com.example.meterreadingsapp.data.LoginRequest
import com.example.meterreadingsapp.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager
    private val apiService: ApiService by lazy {
        // Pass the application context to initialize RetrofitClient correctly
        RetrofitClient.getService(ApiService::class.java, applicationContext)
    }

    // SharedPreferences for "Remember Me" functionality
    private val PREFS_NAME = "LoginPrefs"
    private val KEY_USERNAME = "username"
    private val KEY_PASSWORD = "password"
    private val KEY_REMEMBER_ME = "rememberMe"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

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
                remove(KEY_USERNAME)
                remove(KEY_PASSWORD)
            }
            apply()
        }
    }

    private fun performLogin() {
        val email = binding.usernameEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()
        val rememberMe = binding.rememberMeCheckBox.isChecked

        if (email.isEmpty() || password.isEmpty()) {
            // Using a string resource is better practice, but this works for now
            Toast.makeText(this, "Please enter both email and password.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.loginProgressBar.isVisible = true
        binding.loginButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = apiService.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    // This will now work because AuthResponse is corrected
                    val authToken = response.body()!!.access_token
                    sessionManager.saveAuthToken(authToken)
                    saveCredentials(email, password, rememberMe)

                    Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Login failed"
                    Toast.makeText(this@LoginActivity, "Login failed: $errorMsg", Toast.LENGTH_LONG).show()
                    sessionManager.clearAuthToken()
                }
            } catch (e: IOException) {
                Toast.makeText(this@LoginActivity, "Network error. Please check connection.", Toast.LENGTH_LONG).show()
                sessionManager.clearAuthToken()
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "An unexpected error occurred: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                sessionManager.clearAuthToken()
            } finally {
                binding.loginProgressBar.isVisible = false
                binding.loginButton.isEnabled = true
            }
        }
    }
}

