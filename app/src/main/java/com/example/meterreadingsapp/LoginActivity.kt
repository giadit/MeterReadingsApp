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
import com.example.meterreadingsapp.databinding.ActivityLoginBinding
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth // CORRECTED IMPORT
import io.github.jan.supabase.gotrue.auth  // CORRECTED IMPORT
import io.github.jan.supabase.gotrue.providers.builtin.Email // CORRECTED IMPORT
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    // SharedPreferences for "Remember Me" functionality
    private val PREFS_NAME = "LoginPrefs"
    private val KEY_USERNAME = "username"
    private val KEY_PASSWORD = "password"
    private val KEY_REMEMBER_ME = "rememberMe"

    // --- Supabase Client Initialization ---
    companion object {
        private const val SUPABASE_URL = "https://database.berliner-e-agentur.de"
        private const val SUPABASE_ANON_KEY = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzdXBhYmFzZSIsImlhdCI6MTc1NjQ1ODY2MCwiZXhwIjo0OTEyMTMyMjYwLCJyb2xlIjoiYW5vbiJ9.yIY7ONDFIdlRFwa2Q-ksaGbTkB7z2iIPi7F-_FHKJKQ"

        // CORRECTED INITIALIZATION
        val supabase = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Auth) // Use Auth, not GoTrue
        }
    }
    // --- End of Supabase Client ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hide system UI (unchanged)
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
            Toast.makeText(this, getString(R.string.login_error_empty_fields), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                binding.loginButton.isEnabled = false
                binding.loginProgressBar.isVisible = true

                // CORRECTED API CALL
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                saveCredentials(email, password, rememberMe)
                Toast.makeText(this@LoginActivity, getString(R.string.login_success), Toast.LENGTH_SHORT).show()

                val intent = Intent(this@LoginActivity, MainActivity::class.java)
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.loginButton.isEnabled = true
                binding.loginProgressBar.isVisible = false
            }
        }
    }
}

