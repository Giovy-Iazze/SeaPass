package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.Embarkation
import com.example.data.EmbarkationRepository
import com.example.network.VesselFinderService
import com.example.network.VesselResponse
import com.example.network.WikidataVesselService
import com.example.ui.theme.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VesselLookupUiState {
    object Idle : VesselLookupUiState
    object Loading : VesselLookupUiState
    data class Success(val vessel: VesselResponse, val isDemoMode: Boolean) : VesselLookupUiState
    data class Error(val errorMessage: String) : VesselLookupUiState
}

data class SeaServiceSummary(
    val totalSeaDays: Int,
    val activeOnboardCount: Int,
    val embarkationList: List<Embarkation>
)

class EmbarkationViewModel(
    private val repository: EmbarkationRepository,
    application: android.app.Application
) : ViewModel() {

    private val sharedPrefs = application.getSharedPreferences("seapass_prefs", android.content.Context.MODE_PRIVATE)

    // Language state to support instantaneous translation switches
    private val _currentLanguage = MutableStateFlow(
        try {
            AppLanguage.valueOf(sharedPrefs.getString("app_lang", AppLanguage.EN.name) ?: AppLanguage.EN.name)
        } catch (e: Exception) {
            AppLanguage.EN
        }
    )
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language
        sharedPrefs.edit().putString("app_lang", language.name).apply()
    }

    // Theme mode state flow to support dynamic dark mode styles
    private val _themeMode = MutableStateFlow(sharedPrefs.getString("theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _customFolders = MutableStateFlow(
        sharedPrefs.getStringSet("custom_folders", emptySet())?.toList() ?: emptyList()
    )
    val customFolders: StateFlow<List<String>> = _customFolders.asStateFlow()

    fun addCustomFolder(folderName: String) {
        if (folderName.isNotBlank() && !_customFolders.value.contains(folderName)) {
            val updated = _customFolders.value + folderName
            _customFolders.value = updated
            sharedPrefs.edit().putStringSet("custom_folders", updated.toSet()).apply()
        }
    }

    fun removeCustomFolder(folderName: String) {
        val updated = _customFolders.value - folderName
        _customFolders.value = updated
        sharedPrefs.edit().putStringSet("custom_folders", updated.toSet()).apply()
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        sharedPrefs.edit().putString("theme_mode", mode).apply()
    }

    // Notification Days Before Expiry setting
    private val _daysBeforeExpiryAlert = MutableStateFlow(sharedPrefs.getInt("days_alert", 30))
    val daysBeforeExpiryAlert: StateFlow<Int> = _daysBeforeExpiryAlert.asStateFlow()

    fun setDaysBeforeExpiryAlert(days: Int) {
        if (days >= 0) {
            _daysBeforeExpiryAlert.value = days
            sharedPrefs.edit().putInt("days_alert", days).apply()
        }
    }

    // Default Role setting
    private val _defaultRole = MutableStateFlow(sharedPrefs.getString("default_role", "Coperta") ?: "Coperta")
    val defaultRole: StateFlow<String> = _defaultRole.asStateFlow()

    fun setDefaultRole(role: String) {
        _defaultRole.value = role
        sharedPrefs.edit().putString("default_role", role).apply()
    }

    // Google Account email
    private val _googleAccountEmail = MutableStateFlow(sharedPrefs.getString("google_account_email", "") ?: "")
    val googleAccountEmail: StateFlow<String> = _googleAccountEmail.asStateFlow()

    fun setGoogleAccountEmail(email: String) {
        _googleAccountEmail.value = email
        sharedPrefs.edit().putString("google_account_email", email).apply()
    }

    // Google Sync Enabled
    private val _isGoogleSyncEnabled = MutableStateFlow(sharedPrefs.getBoolean("google_sync_enabled", false))
    val isGoogleSyncEnabled: StateFlow<Boolean> = _isGoogleSyncEnabled.asStateFlow()

    fun setGoogleSyncEnabled(enabled: Boolean) {
        _isGoogleSyncEnabled.value = enabled
        sharedPrefs.edit().putBoolean("google_sync_enabled", enabled).apply()
    }

    // Reactive StateFlow for Sea Service summary and statistics
    val seaServiceSummary: StateFlow<SeaServiceSummary> = repository.allEmbarkations
        .map { list ->
            var totalDays = 0
            var activeCount = 0
            val currentTime = System.currentTimeMillis()

            list.forEach { embark ->
                totalDays += embark.calculateSeaDays(currentTime)
                if (embark.signOffDate == null) {
                    activeCount++
                }
            }

            SeaServiceSummary(
                totalSeaDays = totalDays,
                activeOnboardCount = activeCount,
                embarkationList = list
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SeaServiceSummary(0, 0, emptyList())
        )

    // VesselFinder Search / Lookup State
    private val _lookupState = MutableStateFlow<VesselLookupUiState>(VesselLookupUiState.Idle)
    val lookupState: StateFlow<VesselLookupUiState> = _lookupState.asStateFlow()

    fun clearLookupState() {
        _lookupState.value = VesselLookupUiState.Idle
    }

    /**
     * Look up a vessel using Wikidata or local dataset.
     */
    fun searchVessel(query: String) {
        if (query.isBlank()) {
            _lookupState.value = VesselLookupUiState.Error("Please enter a valid search query")
            return
        }

        _lookupState.value = VesselLookupUiState.Loading

        viewModelScope.launch {
            try {
                // Try looking up in Wikidata (Free, keyless AIS data source)
                val wikidataVessel = WikidataVesselService.queryVessel(query)
                if (wikidataVessel != null) {
                    _lookupState.value = VesselLookupUiState.Success(wikidataVessel, isDemoMode = false)
                } else {
                    // Try VesselFinder HTML scraper
                    val scrapedVessel = VesselFinderService.scrapeVessel(query)
                    if (scrapedVessel != null) {
                        _lookupState.value = VesselLookupUiState.Success(scrapedVessel, isDemoMode = false)
                    } else {
                        // Fallback to our local mock database if wikidata fails
                        val mockVessel = VesselFinderService.findMockVessel(query)
                        if (mockVessel != null) {
                           _lookupState.value = VesselLookupUiState.Success(mockVessel, isDemoMode = false)
                        } else {
                           _lookupState.value = VesselLookupUiState.Error("Vessel not found. Try a different MMSI or IMO.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("EmbarkationViewModel", "Lookup failed: ${e.message}", e)
                _lookupState.value = VesselLookupUiState.Error("Search service unavailable: ${e.message}")
            }
        }
    }

    fun saveEmbarkation(
        id: Int = 0,
        vesselName: String,
        vesselImo: String,
        vesselMmsi: String?,
        rank: String,
        vesselType: String?,
        vesselFlag: String?,
        signOnDate: Long,
        signOffDate: Long?,
        seaDaysOverride: Int?,
        signOnPort: String? = null,
        signOffPort: String? = null,
        grossTonnage: String? = null,
        vesselDimensions: String? = null,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val embarkation = Embarkation(
                id = id,
                vesselName = vesselName,
                vesselImo = vesselImo,
                vesselMmsi = vesselMmsi,
                rank = rank,
                vesselType = vesselType,
                vesselFlag = vesselFlag,
                signOnDate = signOnDate,
                signOffDate = signOffDate,
                seaDaysOverride = seaDaysOverride,
                signOnPort = signOnPort,
                signOffPort = signOffPort,
                grossTonnage = grossTonnage,
                vesselDimensions = vesselDimensions
            )
            repository.insert(embarkation)
            onComplete()
        }
    }

    fun registerSignOff(id: Int, signOffDate: Long, signOffPort: String?, onComplete: () -> Unit) {
        viewModelScope.launch {
            val embark = repository.getEmbarkationById(id)
            if (embark != null) {
                val updated = embark.copy(
                    signOffDate = signOffDate,
                    signOffPort = signOffPort?.trim()?.ifBlank { null }
                )
                repository.insert(updated)
            }
            onComplete()
        }
    }

    fun deleteEmbarkation(embarkation: Embarkation) {
        viewModelScope.launch {
            repository.delete(embarkation)
        }
    }
}

class EmbarkationViewModelFactory(
    private val repository: EmbarkationRepository,
    private val application: android.app.Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EmbarkationViewModel::class.java)) {
            return EmbarkationViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
