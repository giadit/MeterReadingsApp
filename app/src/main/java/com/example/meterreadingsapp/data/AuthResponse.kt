package com.example.meterreadingsapp.data

import com.google.gson.annotations.SerializedName

// This data class models the JSON response from the login API
data class AuthResponse(
    // The @SerializedName annotation tells Gson to map the JSON key "access_token"
    // to this property, resolving the error.
    @SerializedName("access_token") val access_token: String,

    @SerializedName("token_type") val token_type: String,
    @SerializedName("expires_in") val expires_in: Int,
    @SerializedName("refresh_token") val refresh_token: String,
    @SerializedName("user") val user: User
)

data class User(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String
    // You can add other user properties here if you need them
)

