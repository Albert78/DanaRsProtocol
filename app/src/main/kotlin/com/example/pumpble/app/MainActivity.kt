package com.example.pumpble.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.History
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PumpManager.initialize(this)

        setContent {
            val rawViewModel: RawViewModel = viewModel()
            val userViewModel: UserViewModel = viewModel()
            val logViewModel: LogViewModel = viewModel()

            rawViewModel.permissionsGranted = hasBlePermissions()

            MainApp(rawViewModel, userViewModel, logViewModel)
        }
    }

    @Composable
    private fun MainApp(
        rawViewModel: RawViewModel,
        userViewModel: UserViewModel,
        logViewModel: LogViewModel
    ) {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            rawViewModel.permissionsGranted = hasBlePermissions()
        }

        var currentScreen by remember {
            mutableStateOf<AppScreen>(AppScreen.RAW_CONSOLE)
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
                            selected = currentScreen == AppScreen.RAW_CONSOLE,
                            onClick = { currentScreen = AppScreen.RAW_CONSOLE },
                            icon = { Icon(Icons.Default.DeveloperMode, contentDescription = null) },
                            label = { Text("Raw Console") },
                        )
                        NavigationBarItem(
                            selected = currentScreen == AppScreen.USER_CONTROL,
                            onClick = { currentScreen = AppScreen.USER_CONTROL },
                            icon = { Icon(Icons.Default.SettingsRemote, contentDescription = null) },
                            label = { Text("User Control") },
                        )
                        NavigationBarItem(
                            selected = currentScreen == AppScreen.LOGS,
                            onClick = { currentScreen = AppScreen.LOGS },
                            icon = { Icon(Icons.Default.History, contentDescription = null) },
                            label = { Text("Logs") },
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
                    when (currentScreen) {
                        AppScreen.RAW_CONSOLE -> RawConsoleView(
                            viewModel = rawViewModel,
                            onPermissionRequest = { permissionLauncher.launch(BLE_PERMISSIONS) }
                        )
                        AppScreen.USER_CONTROL -> UserControlView(viewModel = userViewModel)
                        AppScreen.LOGS -> LogScreen(viewModel = logViewModel)
                    }
                }
            }
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