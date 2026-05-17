package com.mobileftp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.mobileftp.data.local.SettingsStore
import com.mobileftp.ui.MobileFtpApp
import com.mobileftp.ui.theme.MobileFtpTheme
import com.mobileftp.ui.theme.ThemePreference
import com.mobileftp.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsStore: SettingsStore

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* observed via PermissionUtils */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val needed = PermissionUtils.runtimePermissions().filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())

        setContent {
            val themePref by settingsStore.themeFlow.collectAsState(initial = ThemePreference.SYSTEM)
            MobileFtpTheme(themePreference = themePref) {
                MobileFtpApp(
                    themePreference = themePref,
                    onSetTheme = { newPref ->
                        lifecycleScope.launch { settingsStore.setTheme(newPref) }
                    }
                )
            }
        }
    }
}
