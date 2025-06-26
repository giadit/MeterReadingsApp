package com.example.meterreadingsapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.meterreadingsapp.repository.MeterRepository

/**
 * Factory class for creating instances of [LocationViewModel].
 * This allows us to pass a [MeterRepository] instance to the ViewModel's constructor,
 * which is not directly supported by the default ViewModel creation mechanism.
 *
 * @param repository The [MeterRepository] instance to be injected into the [LocationViewModel].
 */
class LocationViewModelFactory(private val repository: MeterRepository) : ViewModelProvider.Factory {

    /**
     * Creates a new instance of the given [Class].
     *
     * @param modelClass The Class of the ViewModel to create.
     * @param extras A [ViewModelProvider.Factory.CreationExtras] object (used by default factory).
     * @return A new ViewModel instance.
     * @throws IllegalArgumentException If the modelClass is not assignable from [LocationViewModel].
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // The ViewModel will no longer need the Application context for filtering,
            // so we revert to the simpler constructor.
            return LocationViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
