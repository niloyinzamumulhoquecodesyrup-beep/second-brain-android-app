package com.secondbrain.lock

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.secondbrain.lock.data.AppLimit
import com.secondbrain.lock.data.AppLimitRepository
import com.secondbrain.lock.data.InstalledAppInfo
import com.secondbrain.lock.data.InstalledAppsRepository
import com.secondbrain.lock.data.LocalCache
import com.secondbrain.lock.data.SchedulePrefs
import com.secondbrain.lock.data.SecurePrefs
import com.secondbrain.lock.data.SleepPrefs
import com.secondbrain.lock.data.repo.ProfileRepository
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.service.MonitorService
import com.secondbrain.lock.ui.nav.ACCOUNT_SETTINGS_ROUTE
import com.secondbrain.lock.ui.nav.AppNavHost
import com.secondbrain.lock.ui.nav.BottomBar
import com.secondbrain.lock.ui.nav.Destination
import com.secondbrain.lock.ui.nav.MINDVERSE_ROOM_ROUTE
import com.secondbrain.lock.ui.nav.QuickAddChooserSheet
import com.secondbrain.lock.ui.nav.QuickAddTaskSheet
import com.secondbrain.lock.ui.nav.TopBar
import com.secondbrain.lock.ui.nav.VoiceListenOverlay
import com.secondbrain.lock.ui.screens.AccountSettingsScreen
import com.secondbrain.lock.ui.screens.AddAppScreen
import com.secondbrain.lock.ui.screens.DashboardRow
import com.secondbrain.lock.ui.screens.DashboardScreen
import com.secondbrain.lock.ui.screens.LoginScreen
import com.secondbrain.lock.ui.screens.OnboardingScreen
import com.secondbrain.lock.ui.screens.PermissionStep
import com.secondbrain.lock.ui.screens.RegisterScreen
import com.secondbrain.lock.ui.screens.SettingsScreen
import com.secondbrain.lock.ui.screens.organize.CaptureSheet
import com.secondbrain.lock.ui.theme.SecondBrainLockTheme
import com.secondbrain.lock.ui.wake.WakeFlowActivity
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

private enum class SettingsTab { DASHBOARD, ADD_APP, SETTINGS }

@Composable
private fun RootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var authToken by remember { mutableStateOf(SecurePrefs.getToken(context)) }
    var showRegister by remember { mutableStateOf(false) }
    var authLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ApiClient.onUnauthorized.collect { authToken = null }
    }

    if (authToken == null) {
        if (showRegister) {
            RegisterScreen(
                isLoading = authLoading,
                errorMessage = authError,
                onRegister = { email, password ->
                    authError = null
                    authLoading = true
                    scope.launch {
                        val result = ApiClient.register(email, password)
                            // register() only sets a cookie; log in right after to get the
                            // bearer token the native app actually stores.
                            .mapCatching { ApiClient.login(email, password).getOrThrow() }
                        authLoading = false
                        result.onSuccess { token -> authToken = token }
                        result.onFailure { error -> authError = error.message ?: "Registration failed" }
                    }
                },
                onNavigateToLogin = { authError = null; showRegister = false }
            )
        } else {
            LoginScreen(
                isLoading = authLoading,
                errorMessage = authError,
                onLogin = { email, password ->
                    authError = null
                    authLoading = true
                    scope.launch {
                        val result = ApiClient.login(email, password)
                        authLoading = false
                        result.onSuccess { token -> authToken = token }
                        result.onFailure { error -> authError = error.message ?: "Login failed" }
                    }
                },
                onNavigateToRegister = { authError = null; showRegister = true }
            )
        }
        return
    }

    val navController = rememberNavController()
    // Shared by the normal logout menu item and a successful account deactivation — both end
    // the session locally the same way.
    val onLogout: () -> Unit = {
        ApiClient.logout()
        ProfileRepository.clear()
        // A different account may log in next on this device — don't let its first
        // launch flash the previous account's cached data before its own refresh lands.
        scope.launch { LocalCache.clearAll() }
        authToken = null
    }
    // Built once and threaded down to every top-level tab so each one renders it as the first
    // item in its own scrollable column — it scrolls away with the page instead of staying
    // pinned.
    val topBar: @Composable () -> Unit = {
        TopBar(
            onOpenSettings = { navController.navigate(ACCOUNT_SETTINGS_ROUTE) },
            onOpenShield = { navController.navigate(Destination.SHIELD.route) },
            onLogout = onLogout
        )
    }

    var quickAddStep by remember { mutableStateOf<QuickAddStep?>(null) }

    // The Mindverse room is a full-screen call takeover (its own camera-grid/controls bottom
    // chrome) — showing the app's own bottom nav bar underneath it as well would double up on
    // bottom chrome, so it's hidden for exactly that one route.
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val inMindverseRoom = currentBackStackEntry?.destination?.route == MINDVERSE_ROOM_ROUTE

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (!inMindverseRoom) {
                    BottomBar(navController, onAddClick = { quickAddStep = QuickAddStep.CHOOSER })
                }
            }
        ) { padding ->
            AppNavHost(
                navController = navController,
                shieldContent = { shieldPadding -> SettingsFlow(shieldPadding, topBar) },
                accountSettingsContent = { accountPadding ->
                    AccountSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onDeactivated = onLogout,
                        contentPadding = accountPadding
                    )
                },
                topBar = topBar,
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding
            )
        }

        when (quickAddStep) {
            QuickAddStep.CHOOSER -> QuickAddChooserSheet(
                onDismiss = { quickAddStep = null },
                onPickTask = { quickAddStep = QuickAddStep.TASK },
                onPickCapture = { quickAddStep = QuickAddStep.CAPTURE }
            )
            QuickAddStep.TASK -> QuickAddTaskSheet(
                onDismiss = { quickAddStep = null },
                onCreated = { quickAddStep = null }
            )
            QuickAddStep.CAPTURE -> CaptureSheet(
                onDismiss = { quickAddStep = null },
                onCaptured = { quickAddStep = null }
            )
            null -> {}
        }

        VoiceListenOverlay()
    }
}

private enum class QuickAddStep { CHOOSER, TASK, CAPTURE }

/** The native onboarding + app-limit dashboard flow, now living under the Shield tab. */
@Composable
private fun SettingsFlow(contentPadding: PaddingValues = PaddingValues(), topBar: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val repo = remember { AppLimitRepository(context) }
    val scope = rememberCoroutineScope()

    var permissionsGranted by remember { mutableStateOf(Permissions.allGranted(context)) }
    // Onboarding stays on screen — showing live checkmarks — until the user explicitly taps
    // Continue, rather than vanishing the instant the last permission is granted in the
    // background (which used to happen on the very next ON_RESUME, before the user ever saw
    // the final checked state).
    var onboardingActive by remember { mutableStateOf(!permissionsGranted) }
    var screen by remember { mutableStateOf(SettingsTab.DASHBOARD) }
    // Bumped on every resume so the `steps` list below (a plain val, not its own observable
    // state) is forced to recompute with fresh granted/not-granted flags even when the
    // overall permissionsGranted boolean hasn't flipped yet (e.g. only 1 of 3 granted so far).
    var resumeTick by remember { mutableStateOf(0) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { resumeTick++; permissionsGranted = Permissions.allGranted(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
                permissionsGranted = Permissions.allGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (onboardingActive) {
        var showAccessibilityRationale by remember { mutableStateOf(false) }
        val steps = remember(resumeTick) {
            listOf(
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
                ),
                PermissionStep(
                    title = "Instant blocking",
                    description = "Without this, the app still blocks apps, just by checking every second or two instead of instantly. It only reads which app is in front — never screen content or what you tap.",
                    granted = Permissions.hasAccessibility(context),
                    onRequest = { showAccessibilityRationale = true },
                    optional = true
                )
            )
        }
        if (showAccessibilityRationale) {
            AlertDialog(
                onDismissRequest = { showAccessibilityRationale = false },
                title = { Text("Turn on instant blocking?") },
                text = {
                    Text(
                        "This opens Android's Accessibility settings and asks you to enable Slay Task there. " +
                            "It only ever reads which app is currently in the foreground, to lock it the moment it opens " +
                            "instead of within a second or two. It cannot read screen content, typed text, or taps. " +
                            "You can turn it off again at any time in Accessibility settings."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showAccessibilityRationale = false
                        context.startActivity(Permissions.accessibilityIntent())
                    }) { Text("Open settings") }
                },
                dismissButton = {
                    TextButton(onClick = { showAccessibilityRationale = false }) { Text("Not now") }
                }
            )
        }
        OnboardingScreen(
            steps = steps,
            allGranted = steps.filter { !it.optional }.all { it.granted },
            onContinue = {
                onboardingActive = false
                MonitorService.start(context)
            }
        )
        return
    }

    when (screen) {
        SettingsTab.DASHBOARD -> {
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
                onAdd = { screen = SettingsTab.ADD_APP },
                onRemove = { limit: AppLimit -> scope.launch(Dispatchers.IO) { repo.remove(limit) } },
                onToggleSchedule = { limit, enabled ->
                    scope.launch(Dispatchers.IO) { repo.setBlockDuringSchedule(limit, enabled) }
                },
                onToggleFocus = { limit, enabled ->
                    scope.launch(Dispatchers.IO) { repo.setBlockDuringFocus(limit, enabled) }
                },
                onOpenSettings = { screen = SettingsTab.SETTINGS },
                contentPadding = contentPadding,
                topBar = topBar
            )
        }

        SettingsTab.ADD_APP -> {
            var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
            LaunchedEffect(Unit) {
                apps = withContext(Dispatchers.IO) {
                    InstalledAppsRepository.listLaunchableApps(context.packageManager, context.packageName)
                }
            }
            AddAppScreen(
                apps = apps,
                onBack = { screen = SettingsTab.DASHBOARD },
                onConfirm = { app: InstalledAppInfo, minutes: Int, openCountLimit: Int? ->
                    scope.launch(Dispatchers.IO) { repo.add(app.packageName, app.label, minutes, openCountLimit) }
                    screen = SettingsTab.DASHBOARD
                }
            )
        }

        SettingsTab.SETTINGS -> {
            var settingsResumeTick by remember { mutableStateOf(0) }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) settingsResumeTick++
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            var autoBlockCategories by remember(settingsResumeTick) {
                mutableStateOf(SchedulePrefs.getAutoBlockCategories(context))
            }
            var sleepAlarmEnabled by remember { mutableStateOf(SleepPrefs.isEnabled(context)) }
            var useRoutineSleepWindow by remember { mutableStateOf(SleepPrefs.getUseRoutineSleepWindow(context)) }
            var wakeMinuteOfDay by remember { mutableStateOf(SleepPrefs.getWakeMinuteOfDay(context)) }

            SettingsScreen(
                onBack = { screen = SettingsTab.DASHBOARD },
                autoBlockCategories = autoBlockCategories,
                onToggleCategory = { category, enabled ->
                    autoBlockCategories = if (enabled) autoBlockCategories + category else autoBlockCategories - category
                    SchedulePrefs.setAutoBlockCategories(context, autoBlockCategories)
                },
                accessibilityGranted = remember(settingsResumeTick) { Permissions.hasAccessibility(context) },
                onOpenAccessibilitySettings = { context.startActivity(Permissions.accessibilityIntent()) },
                sleepAlarmEnabled = sleepAlarmEnabled,
                onToggleSleepAlarm = { enabled ->
                    sleepAlarmEnabled = enabled
                    SleepPrefs.setEnabled(context, enabled)
                    WakeFlowActivity.rescheduleAsync(context)
                },
                useRoutineSleepWindow = useRoutineSleepWindow,
                onToggleUseRoutine = { use ->
                    useRoutineSleepWindow = use
                    SleepPrefs.setUseRoutineSleepWindow(context, use)
                    WakeFlowActivity.rescheduleAsync(context)
                },
                wakeMinuteOfDay = wakeMinuteOfDay,
                onWakeMinuteChange = { minute ->
                    wakeMinuteOfDay = minute
                    SleepPrefs.setWakeMinuteOfDay(context, minute)
                    WakeFlowActivity.rescheduleAsync(context)
                }
            )
        }
    }
}
