package com.example.features.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.capabilities.DeviceCapabilityDetector
import com.example.core.discovery.NearbyDiscoveryEngine
import com.example.core.identity.DeviceIdentityManager
import com.example.core.permissions.DiscoveryPermissionManager
import com.example.core.datastore.PreferencesManager
import com.example.data.AppContainer
import com.example.domain.model.discovery.DeviceCapabilityReport
import com.example.domain.model.discovery.DeviceIdentity
import com.example.domain.model.discovery.DeviceTrustState
import com.example.domain.model.discovery.DiscoveryState
import com.example.domain.model.discovery.NearbyDevice
import com.example.domain.repository.NearbyDeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NearbyDevicesUiState(
    val localIdentity: DeviceIdentity? = null,
    val discoveryState: DiscoveryState = DiscoveryState.IDLE,
    val isDiscoveryEnabled: Boolean = false,
    val isDiscoverable: Boolean = false,
    val capabilities: DeviceCapabilityReport? = null,
    val missingPermissions: List<String> = emptyList(),
    val devices: List<NearbyDevice> = emptyList(),
    val selectedDevice: NearbyDevice? = null,
    val statusMessage: String = "Nearby discovery is turned off.",
    val errorMessage: String? = null
)

class NearbyDevicesViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    private val identityManager: DeviceIdentityManager = appContainer.deviceIdentityManager
    private val capabilityDetector: DeviceCapabilityDetector = appContainer.deviceCapabilityDetector
    private val permissionManager: DiscoveryPermissionManager = appContainer.discoveryPermissionManager
    private val preferencesManager: PreferencesManager = appContainer.preferencesManager
    private val discoveryEngine: NearbyDiscoveryEngine = appContainer.nearbyDiscoveryEngine
    private val repository: NearbyDeviceRepository = appContainer.nearbyDeviceRepository

    private val _uiState = MutableStateFlow(NearbyDevicesUiState())
    val uiState: StateFlow<NearbyDevicesUiState> = _uiState.asStateFlow()

    init {
        // Load initial identity and capabilities without blocking main thread
        viewModelScope.launch {
            val identity = identityManager.getOrCreateIdentity()
            val caps = capabilityDetector.detectCapabilities()
            val missing = permissionManager.getMissingPermissions()
            _uiState.update {
                it.copy(
                    localIdentity = identity,
                    capabilities = caps,
                    missingPermissions = missing
                )
            }
        }

        // Observe repository devices
        viewModelScope.launch {
            repository.observeDiscoveredDevices().collect { deviceList ->
                _uiState.update { currentState ->
                    val updatedSelected = currentState.selectedDevice?.let { selected ->
                        deviceList.find { it.deviceId == selected.deviceId } ?: selected
                    }
                    currentState.copy(
                        devices = deviceList,
                        selectedDevice = updatedSelected
                    )
                }
            }
        }

        // Observe discovery engine state
        viewModelScope.launch {
            discoveryEngine.discoveryState.collect { state ->
                _uiState.update { currentState ->
                    val message = when (state) {
                        DiscoveryState.IDLE -> "Nearby discovery is turned off."
                        DiscoveryState.STARTING -> "Preparing nearby discovery..."
                        DiscoveryState.DISCOVERING -> "Looking for nearby InternetStorer devices..."
                        DiscoveryState.DEVICE_FOUND -> "Nearby InternetStorer devices active."
                        DiscoveryState.STOPPING -> "Stopping nearby discovery..."
                        DiscoveryState.STOPPED -> "Nearby discovery stopped."
                        DiscoveryState.PERMISSION_REQUIRED -> "Nearby permission is required."
                        DiscoveryState.UNSUPPORTED -> "Nearby discovery is not supported on this device."
                        DiscoveryState.ERROR -> "Nearby discovery encountered a problem."
                    }
                    currentState.copy(
                        discoveryState = state,
                        statusMessage = message
                    )
                }
            }
        }

        // Observe preferences
        viewModelScope.launch {
            combine(
                preferencesManager.isNearbyDiscoveryEnabled,
                preferencesManager.isDeviceDiscoverable
            ) { discoveryEnabled, discoverable ->
                Pair(discoveryEnabled, discoverable)
            }.collect { (discoveryEnabled, discoverable) ->
                _uiState.update {
                    it.copy(
                        isDiscoveryEnabled = discoveryEnabled,
                        isDiscoverable = discoverable
                    )
                }
            }
        }
    }

    fun toggleDiscovery(enable: Boolean) {
        viewModelScope.launch {
            preferencesManager.setNearbyDiscoveryEnabled(enable)
            val missing = permissionManager.getMissingPermissions()
            _uiState.update { it.copy(missingPermissions = missing) }

            if (enable) {
                if (missing.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            discoveryState = DiscoveryState.PERMISSION_REQUIRED,
                            statusMessage = "Nearby permission is required."
                        )
                    }
                } else {
                    discoveryEngine.startDiscovery()
                }
            } else {
                discoveryEngine.stopDiscovery()
            }
        }
    }

    fun toggleDiscoverability(enable: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDeviceDiscoverable(enable)
        }
    }

    fun updateCustomDisplayName(name: String) {
        viewModelScope.launch {
            val updated = identityManager.updateCustomDisplayName(name)
            _uiState.update { it.copy(localIdentity = updated) }
        }
    }

    fun refreshCapabilities() {
        viewModelScope.launch {
            val caps = capabilityDetector.detectCapabilities()
            val missing = permissionManager.getMissingPermissions()
            _uiState.update {
                it.copy(
                    capabilities = caps,
                    missingPermissions = missing
                )
            }
        }
    }

    fun selectDevice(device: NearbyDevice?) {
        _uiState.update { it.copy(selectedDevice = device) }
    }

    fun selectDeviceById(deviceId: String) {
        viewModelScope.launch {
            val device = repository.getDevice(deviceId)
            _uiState.update { it.copy(selectedDevice = device) }
        }
    }

    fun updateDeviceTrust(deviceId: String, trustState: DeviceTrustState) {
        viewModelScope.launch {
            repository.updateTrustState(deviceId, trustState)
        }
    }

    fun blockDevice(deviceId: String) {
        viewModelScope.launch {
            repository.setDeviceBlocked(deviceId, true)
        }
    }

    fun forgetDevice(deviceId: String) {
        viewModelScope.launch {
            repository.forgetDevice(deviceId)
            if (_uiState.value.selectedDevice?.deviceId == deviceId) {
                _uiState.update { it.copy(selectedDevice = null) }
            }
        }
    }

    fun clearAllDevices() {
        viewModelScope.launch {
            repository.clearAllDevices()
            _uiState.update { it.copy(selectedDevice = null) }
        }
    }

    fun onPermissionsGranted() {
        viewModelScope.launch {
            val missing = permissionManager.getMissingPermissions()
            _uiState.update { it.copy(missingPermissions = missing) }
            if (missing.isEmpty() && _uiState.value.isDiscoveryEnabled) {
                discoveryEngine.startDiscovery()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Graceful lifecycle safety: stop active discovery if leaving ViewModel
        viewModelScope.launch {
            discoveryEngine.stopDiscovery()
        }
    }
}
