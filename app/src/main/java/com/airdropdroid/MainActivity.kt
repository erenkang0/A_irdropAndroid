package com.airdropdroid

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdropdroid.model.TransferState
import com.airdropdroid.ui.IncomingRequestDialog
import com.airdropdroid.ui.NearbyViewModel
import com.airdropdroid.ui.RadarScreen
import com.airdropdroid.ui.TransferProgressCard

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: NearbyViewModel = viewModel()
                    AppRoot(vm, intent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

private val REQUIRED_PERMISSIONS = buildList {
    add(Manifest.permission.BLUETOOTH_ADVERTISE)
    add(Manifest.permission.BLUETOOTH_SCAN)
    add(Manifest.permission.BLUETOOTH_CONNECT)
    add(Manifest.permission.NEARBY_WIFI_DEVICES)
    add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@androidx.compose.runtime.Composable
private fun AppRoot(vm: NearbyViewModel, intent: Intent) {
    val devices by vm.devices.collectAsState()
    val transferState by vm.transferState.collectAsState()
    val incoming by vm.incoming.collectAsState()
    val pendingUris by vm.pendingUris.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onPermissionsReady() }

    LaunchedEffect(Unit) {
        extractSharedUris(intent)?.let { vm.setOutgoing(it) }
        permissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RadarScreen(
            devices = devices,
            hasFilesToSend = pendingUris.isNotEmpty(),
            onDeviceClick = { vm.sendTo(it) },
            modifier = Modifier.fillMaxSize(),
        )

        incoming?.let { request ->
            IncomingRequestDialog(
                request = request,
                onAccept = { vm.respondToIncoming(true) },
                onReject = { vm.respondToIncoming(false) },
            )
        }

        if (transferState != TransferState.Idle) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                TransferProgressCard(
                    state = transferState,
                    onDismiss = {
                        vm.resetTransfer()
                        vm.clearOutgoing()
                    },
                )
            }
        }
    }
}

/** Paylaş menüsünden (ACTION_SEND / SEND_MULTIPLE) gelen dosya Uri'lerini çıkarır. */
private fun extractSharedUris(intent: Intent): List<Uri>? = when (intent.action) {
    Intent.ACTION_SEND -> {
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        uri?.let { listOf(it) }
    }
    Intent.ACTION_SEND_MULTIPLE -> {
        @Suppress("DEPRECATION")
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
    }
    else -> null
}
