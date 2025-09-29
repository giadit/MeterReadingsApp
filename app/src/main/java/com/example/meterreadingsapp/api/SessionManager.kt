package com.example.meterreadingsapp.api

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages the user's authentication token using SharedPreferences.
 */
class SessionManager(context: Context) {
    private var prefs: SharedPreferences = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

    companion object {
        const val AUTH_TOKEN = "auth_token"
    }

    /**
     * Saves the authentication token to SharedPreferences.
     */
    fun saveAuthToken(token: String) {
        val editor = prefs.edit()
        editor.putString(AUTH_TOKEN, token)
        editor.apply()
    }

    /**
     * Fetches the authentication token from SharedPreferences.
     */
    fun fetchAuthToken(): String? {
        return prefs.getString(AUTH_TOKEN, null)
    }

    /**
     * Clears the authentication token from SharedPreferences.
     */
    fun clearAuthToken() {
        val editor = prefs.edit()
        editor.remove(AUTH_TOKEN)
        editor.apply()
    }
}
