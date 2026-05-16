package com.example.pumpble.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: PumpViewModel = viewModel()
            viewModel.permissionsGranted = hasBlePermissions()
            
            PumpConsole(viewModel)
        }
    }

    @Composable
    private fun PumpConsole(viewModel: PumpViewModel) {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            viewModel.permissionsGranted = hasBlePermissions()
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
                            selected = viewModel.currentScreen == AppScreen.RAW_CONSOLE,
                            onClick = { viewModel.currentScreen = AppScreen.RAW_CONSOLE },
                            icon = { Icon(Icons.Default.DeveloperMode, contentDescription = null) },
                            label = { Text("Raw Console") },
                        )
                        NavigationBarItem(
                            selected = viewModel.currentScreen == AppScreen.USER_CONTROL,
                            onClick = { viewModel.currentScreen = AppScreen.USER_CONTROL },
                            icon = { Icon(Icons.Default.SettingsRemote, contentDescription = null) },
                            label = { Text("User Control") },
                        )
                    }
                },
            ) { paddingValues ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    when (viewModel.currentScreen) {
                        AppScreen.RAW_CONSOLE -> RawConsoleView(
                            viewModel = viewModel,
                            onPermissionRequest = { permissionLauncher.launch(BLE_PERMISSIONS) }
                        )
                        AppScreen.USER_CONTROL -> UserControlView()
                    }
                }
            }
        }
    }

    @Composable
    private fun UserControlView() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.SettingsRemote,
                contentDescription = null,
                modifier = Modifier.height(64.dp).width(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "User Control Screen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Hier wird die Nutzeroberfläche entstehen.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
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