package com.mobileftp

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
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

    /**
     * Receives the runtime permission grant result. We show a Toast for any
     * permission the user denied so they're not left wondering why a feature
     * (like the foreground notification) isn't working.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { granted -> !granted }.keys
        if (denied.isNotEmpty()) {
            val labels = denied.joinToString(", ") { humanLabelFor(it) }
            Toast.makeText(
                this,
                "Permission not allowed: $labels. Some features will be limited.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val needed = PermissionUtils.runtimePermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
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

    /**
     * Map a raw Android permission constant to a short human-readable label
     * so Toasts say "Notifications" instead of "android.permission.POST_NOTIFICATIONS".
     */
    private fun humanLabelFor(permission: String): String = when (permission) {
        android.Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
        android.Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage"
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Storage"
        else -> permission.substringAfterLast('.').replace('_', ' ').lowercase()
            .replaceFirstChar { it.titlecase() }
    }
}
