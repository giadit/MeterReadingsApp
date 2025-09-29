package com.example.meterreadingsapp.data

/**
 * Data class representing the JSON body for a login request.
 * Matches the structure needed by the Supabase auth endpoint.
 */
data class LoginRequest(
    val email: String,
    val password: String
)
