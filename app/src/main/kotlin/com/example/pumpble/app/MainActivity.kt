package com.example.pumpble.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PumpManager.initialize(this)

        setContent {
            val userViewModel: UserViewModel = viewModel()
            val historyViewModel: HistoryViewModel = viewModel()
            val logViewModel: LogViewModel = viewModel()

            MainApp(userViewModel, historyViewModel, logViewModel)
        }
    }

    @Composable
    private fun MainApp(
        userViewModel: UserViewModel,
        historyViewModel: HistoryViewModel,
        logViewModel: LogViewModel
    ) {
        var currentScreen by remember {
            mutableStateOf(AppScreen.USER_CONTROL)
        }

        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Color(0xFF006B5C),
                secondary = Color(0xFF2B5C88),
                tertiary = Color(0xFF9A5B00),
                error = Color(0xFFB3261E),
                surface = Color(0xFFF7FAF8),
                surfaceVariant = Color(0xFFE4ECE8),
            ),
        ) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentScreen == AppScreen.USER_CONTROL,
                            onClick = { currentScreen = AppScreen.USER_CONTROL },
                            icon = { Icon(Icons.Default.SettingsRemote, contentDescription = null) },
                            label = { Text("User Control") },
                        )
                        NavigationBarItem(
                            selected = currentScreen == AppScreen.HISTORY,
                            onClick = { currentScreen = AppScreen.HISTORY },
                            icon = { Icon(Icons.Default.History, contentDescription = null) },
                            label = { Text("History") },
                        )
                        NavigationBarItem(
                            selected = currentScreen == AppScreen.LOGS,
                            onClick = { currentScreen = AppScreen.LOGS },
                            icon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null) },
                            label = { Text("Logs") },
                        )
                    }
                },
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Permanent Header (Connection & Status)
                    StatusDashboard(userViewModel)

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        when (currentScreen) {
                            AppScreen.USER_CONTROL -> UserControlView(viewModel = userViewModel)
                            AppScreen.HISTORY -> HistoryScreen(viewModel = historyViewModel)
                            AppScreen.LOGS -> LogScreen(viewModel = logViewModel)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusDashboard(viewModel: UserViewModel) {
        val context = LocalContext.current

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = 2.dp
        ) {
            Column {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = viewModel.selectedDevice?.name ?: (PumpManager.getLastStoredName() ?: "No Device Selected"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            if (viewModel.selectedDevice == null && viewModel.isSearchingLastDevice) {
                                Text("Searching for last pump...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (viewModel.selectedDevice != null) {
                                Button(
                                    onClick = { viewModel.toggleConnection(context) },
                                    enabled = viewModel.activeCommand == null,
                                    colors = if (viewModel.sessionReady)
                                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                        else ButtonDefaults.buttonColors()
                                ) {
                                    if (viewModel.activeCommand == "Connecting" || viewModel.activeCommand == "Handshake") {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Text(if (viewModel.sessionReady) "Disconnect" else "Connect")
                                    }
                                }
                            }
                        }
                    }

                    if (viewModel.sessionReady) {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusItem(
                                icon = Icons.Default.BatteryStd,
                                label = "Battery",
                                value = viewModel.pumpStatus?.batteryRemainingPercent?.let { "$it%" } ?: "--"
                            )
                            StatusItem(
                                icon = Icons.Default.WaterDrop,
                                label = "Reservoir",
                                value = viewModel.pumpStatus?.reservoirRemainingUnits?.let { "%.1f U".format(it) } ?: "--"
                            )
                            StatusItem(
                                icon = Icons.Default.BluetoothConnected,
                                label = "Last Sync",
                                value = viewModel.lastSyncTime?.let {
                                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(Date(it))
                                } ?: "--"
                            )

                            OutlinedButton(
                                onClick = { viewModel.refreshAllStatus() },
                                enabled = viewModel.activeCommand == null,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                if (viewModel.activeCommand == "Syncing Status") {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Bitte verbinden Sie sich zuerst mit der Pumpe.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    @Composable
    private fun StatusItem(icon: ImageVector, label: String, value: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }

    private fun hasBlePermissions(): Boolean {
        return BLE_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private companion object {
        val BLE_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    }
}