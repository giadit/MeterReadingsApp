package com.example.meterreadingsapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.meterreadingsapp.repository.MeterRepository // Changed to MeterRepository

/**
 * Factory for creating instances of LocationViewModel.
 * This is necessary because LocationViewModel has a custom constructor that takes a repository.
 */
class LocationViewModelFactory(private val repository: MeterRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LocationViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
    