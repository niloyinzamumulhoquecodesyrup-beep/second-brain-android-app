package com.secondbrain.lock

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.secondbrain.lock.data.AppLimit
import com.secondbrain.lock.data.AppLimitRepository
import com.secondbrain.lock.data.InstalledAppInfo
import com.secondbrain.lock.data.InstalledAppsRepository
import com.secondbrain.lock.service.MonitorService
import com.secondbrain.lock.ui.screens.AddAppScreen
import com.secondbrain.lock.ui.screens.DashboardRow
import com.secondbrain.lock.ui.screens.DashboardScreen
import com.secondbrain.lock.ui.screens.OnboardingScreen
import com.secondbrain.lock.ui.screens.PermissionStep
import com.secondbrain.lock.ui.theme.SecondBrainLockTheme
import com.secondbrain.lock.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecondBrainLockTheme {
                RootApp()
            }
        }
    }
}

private enum class Screen { DASHBOARD, ADD_APP }

@Composable
private fun RootApp() {
    val context = LocalContext.current
    val repo = remember { AppLimitRepository(context) }
    val scope = rememberCoroutineScope()

    var permissionsGranted by remember { mutableStateOf(Permissions.allGranted(context)) }
    var screen by remember { mutableStateOf(Screen.DASHBOARD) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionsGranted = Permissions.allGranted(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = Permissions.allGranted(context)
                permissionsGranted = granted
                if (granted) MonitorService.start(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!permissionsGranted) {
        val steps = listOf(
            PermissionStep(
                title = "Usage access",
                description = "Lets the app see which app is open right now and how long you've used it today.",
                granted = Permissions.hasUsageAccess(context),
                onRequest = { context.startActivity(Permissions.usageAccessIntent(context)) }
            ),
            PermissionStep(
                title = "Display over other apps",
                description = "Needed to show the hard-lock screen on top of an app once its time is up.",
                granted = Permissions.hasOverlay(context),
                onRequest = { context.startActivity(Permissions.overlayIntent(context)) }
            ),
            PermissionStep(
                title = "Notifications",
                description = "So you get a heads-up before an app locks, not just after.",
                granted = Permissions.hasNotifications(context),
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        )
        OnboardingScreen(
            steps = steps,
            allGranted = steps.all { it.granted },
            onContinue = {
                permissionsGranted = true
                MonitorService.start(context)
            }
        )
        return
    }

    when (screen) {
        Screen.DASHBOARD -> {
            val limits by repo.observeAll().collectAsState(initial = emptyList())
            var rows by remember { mutableStateOf<List<DashboardRow>>(emptyList()) }

            LaunchedEffect(limits) {
                while (isActive) {
                    rows = withContext(Dispatchers.Default) {
                        limits.map { DashboardRow(it, repo.todaysUsageMillis(it.packageName)) }
                    }
                    delay(3000)
                }
            }

            DashboardScreen(
                rows = rows,
                onAdd = { screen = Screen.ADD_APP },
                onRemove = { limit: AppLimit -> scope.launch(Dispatchers.IO) { repo.remove(limit) } }
            )
        }

        Screen.ADD_APP -> {
            var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
            LaunchedEffect(Unit) {
                apps = withContext(Dispatchers.IO) {
                    InstalledAppsRepository.listLaunchableApps(context.packageManager, context.packageName)
                }
            }
            AddAppScreen(
                apps = apps,
                onBack = { screen = Screen.DASHBOARD },
                onConfirm = { app: InstalledAppInfo, minutes: Int ->
                    scope.launch(Dispatchers.IO) { repo.add(app.packageName, app.label, minutes) }
                    screen = Screen.DASHBOARD
                }
            )
        }
    }
}
