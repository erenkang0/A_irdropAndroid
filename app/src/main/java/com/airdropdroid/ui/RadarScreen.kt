package com.airdropdroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airdropdroid.model.DiscoverySource
import com.airdropdroid.model.NearbyDevice

/** AirDrop tarzı yakındaki cihaz listesi. Bir cihaza dokununca gönderim başlar. */
@Composable
fun RadarScreen(
    devices: List<NearbyDevice>,
    hasFilesToSend: Boolean,
    onDeviceClick: (NearbyDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = if (hasFilesToSend) "Göndermek için bir cihaz seç" else "Yakındaki cihazlar",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Alım arka planda da açık — bildirimden yönetebilirsin",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        if (devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Aranıyor…\nKarşı cihazda da uygulama açık olmalı.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices, key = { it.id }) { device ->
                    DeviceRow(device, hasFilesToSend, onDeviceClick)
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: NearbyDevice,
    clickable: Boolean,
    onClick: (NearbyDevice) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (clickable) it.clickable { onClick(device) } else it }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (device.source) {
                        DiscoverySource.WIFI_DIRECT -> Icons.Filled.Wifi
                        else -> Icons.Filled.Smartphone
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column {
                    Text(device.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = when (device.source) {
                            DiscoverySource.LAN -> "Aynı Wi-Fi ağı"
                            DiscoverySource.WIFI_DIRECT -> "Wi-Fi Direct"
                            DiscoverySource.BLE -> "Yakında"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
