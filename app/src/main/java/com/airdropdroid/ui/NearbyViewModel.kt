package com.airdropdroid.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airdropdroid.App
import com.airdropdroid.data.UriFiles
import com.airdropdroid.model.NearbyDevice
import com.airdropdroid.service.NearbyService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NearbyViewModel(app: Application) : AndroidViewModel(app) {

    // Servisle paylaşılan tek örnek; alım VM yaşam döngüsünden bağımsız sürer.
    private val repo = App.repository(app)

    val devices: StateFlow<List<NearbyDevice>> get() = repo.deviceList
    val transferState get() = repo.transferState
    val incoming get() = repo.incoming

    /** Paylaş menüsünden gelen, gönderilmeyi bekleyen dosyalar. */
    private val _pendingUris = MutableStateFlow<List<Uri>>(emptyList())
    val pendingUris: StateFlow<List<Uri>> = _pendingUris.asStateFlow()

    /** İzinler verildikten sonra çağrılır: alım servise, keşif UI'ye bağlı. */
    fun onPermissionsReady() {
        NearbyService.start(getApplication())
        repo.startDiscovery()
    }

    fun setOutgoing(uris: List<Uri>) {
        _pendingUris.value = uris
    }

    fun clearOutgoing() {
        _pendingUris.value = emptyList()
    }

    fun sendTo(device: NearbyDevice) {
        val uris = _pendingUris.value
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val items = UriFiles.toSendItems(getApplication(), uris)
            repo.send(device, items)
        }
    }

    fun respondToIncoming(accept: Boolean) = repo.respondToIncoming(accept)

    fun resetTransfer() = repo.resetTransferState()

    override fun onCleared() {
        // Yalnızca keşfi durdur; alım foreground servisle devam eder.
        repo.stopDiscovery()
        super.onCleared()
    }
}
