package com.secondbrain.lock.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.data.OnboardingPrefs
import com.secondbrain.lock.data.OnboardingTemplate
import com.secondbrain.lock.data.SleepPrefs
import com.secondbrain.lock.data.Templates
import com.secondbrain.lock.data.repo.NotesRepository
import com.secondbrain.lock.data.repo.OnboardingRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.dto.CreateNoteRequest
import com.secondbrain.lock.ui.screens.authFieldColors
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.fullAuraBackground
import com.secondbrain.lock.util.Permissions
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class WelcomeStep { WORKING_ON, PAIN_POINTS, TEMPLATE, SCHEDULE, ONE_THING }

private data class PainPointOption(val key: String, val label: String)

private val PAIN_POINTS = listOf(
    PainPointOption(OnboardingPrefs.PAIN_FORGETS, "I forget things"),
    PainPointOption(OnboardingPrefs.PAIN_CANT_START, "I can't get started"),
    PainPointOption(OnboardingPrefs.PAIN_LOSES_TIME, "I lose track of time"),
    PainPointOption(OnboardingPrefs.PAIN_NEVER_FINISHES, "I collect but never finish")
)

/**
 * Five screens, skippable at every step, under 90 seconds (P20). Wires the data layer that
 * already existed and was called by nothing (OnboardingRepository, the persona-list endpoint) —
 * see CLAUDE.md's note on this. Gated on GET /api/onboarding/status by the caller (MainActivity),
 * not by this composable.
 */
@Composable
fun WelcomeFlow(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(WelcomeStep.WORKING_ON) }
    var workingOn by remember { mutableStateOf("") }
    var painPoints by remember { mutableStateOf(setOf<String>()) }
    var selectedTemplate by remember { mutableStateOf<OnboardingTemplate?>(null) }
    var wakeMinute by remember { mutableStateOf(SleepPrefs.getWakeMinuteOfDay(context)) }
    var sleepMinute by remember { mutableStateOf(SleepPrefs.getSleepMinuteOfDay(context)) }
    var oneThing by remember { mutableStateOf("") }
    var finishing by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* declining is fine — reminders degrade gracefully, never re-prompted from here */ }

    fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Skipping any screen skips the rest and lands on Today with "Just tasks" — never re-prompted,
    // never punished (P20's own rule).
    fun skipAll() {
        if (finishing) return
        finishing = true
        scope.launch {
            OnboardingPrefs.setTemplate(context, OnboardingTemplate.JUST_TASKS.name)
            runCatching { OnboardingRepository.complete(displayName = "there", age = null, persona = "skipped") }
            onFinished()
        }
    }

    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            if (workingOn.isNotBlank()) {
                NotesRepository.create(CreateNoteRequest(title = workingOn.take(80), content = workingOn, para = "project"))
            }
            OnboardingPrefs.setPainPoints(context, painPoints)
            val template = selectedTemplate ?: OnboardingTemplate.JUST_TASKS
            OnboardingPrefs.setTemplate(context, template.name)
            SleepPrefs.setWakeMinuteOfDay(context, wakeMinute)
            SleepPrefs.setSleepMinuteOfDay(context, sleepMinute)
            Templates.seed(template)
            if (oneThing.isNotBlank()) {
                TasksRepository.create(title = oneThing, dueDate = LocalDate.now().toString())
            }
            runCatching {
                OnboardingRepository.complete(
                    displayName = workingOn.take(40).ifBlank { "there" },
                    age = null,
                    persona = painPoints.firstOrNull() ?: "unspecified"
                )
            }
            onFinished()
        }
    }

    Box(Modifier.fullAuraBackground().fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                WelcomeStep.WORKING_ON -> WelcomeStepScaffold(
                    title = "What are you working on?",
                    onSkip = ::skipAll
                ) {
                    OutlinedTextField(
                        value = workingOn,
                        onValueChange = { workingOn = it },
                        placeholder = { Text("A project, a goal, whatever's on your mind") },
                        colors = authFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { step = WelcomeStep.PAIN_POINTS },
                        colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Continue") }
                }

                WelcomeStep.PAIN_POINTS -> WelcomeStepScaffold(
                    title = "What usually gets in the way?",
                    onSkip = ::skipAll
                ) {
                    PAIN_POINTS.forEach { option ->
                        val selected = option.key in painPoints
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) StreakAccent.copy(alpha = 0.18f) else Ink900)
                                .clickable {
                                    painPoints = if (selected) painPoints - option.key else painPoints + option.key
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(option.label, color = if (selected) StreakAccent else Mist100, style = SecondBrainTypography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            requestPermissionsIfNeeded()
                            step = WelcomeStep.TEMPLATE
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Continue") }
                    if (Permissions.hasExactAlarm(context).not()) {
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { context.startActivity(Permissions.exactAlarmIntent(context)) }) {
                            Text("So reminders arrive at the right minute", color = Mist300, style = SecondBrainTypography.bodySmall)
                        }
                    }
                }

                WelcomeStep.TEMPLATE -> WelcomeStepScaffold(
                    title = "Pick a template",
                    onSkip = ::skipAll
                ) {
                    OnboardingTemplate.entries.forEach { template ->
                        val selected = selectedTemplate == template
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) StreakAccent.copy(alpha = 0.18f) else Ink900)
                                .clickable { selectedTemplate = template }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(template.label, color = if (selected) StreakAccent else Mist100, style = SecondBrainTypography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { step = WelcomeStep.SCHEDULE },
                        enabled = selectedTemplate != null,
                        colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Continue") }
                }

                WelcomeStep.SCHEDULE -> WelcomeStepScaffold(
                    title = "When's your day?",
                    onSkip = ::skipAll
                ) {
                    MinutePicker(label = "Wake", minute = wakeMinute, onChange = { wakeMinute = it })
                    Spacer(Modifier.height(16.dp))
                    MinutePicker(label = "Sleep", minute = sleepMinute, onChange = { sleepMinute = it })
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { step = WelcomeStep.ONE_THING },
                        colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Continue") }
                }

                WelcomeStep.ONE_THING -> WelcomeStepScaffold(
                    title = "One thing for today",
                    onSkip = ::skipAll
                ) {
                    OutlinedTextField(
                        value = oneThing,
                        onValueChange = { oneThing = it },
                        placeholder = { Text("Something small and doable") },
                        colors = authFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = ::finish,
                        enabled = !finishing,
                        colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text(if (finishing) "Setting things up…" else "Done") }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStepScaffold(title: String, onSkip: () -> Unit, content: @Composable () -> Unit) {
    Text(title, color = Mist100, style = SecondBrainTypography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    content()
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onSkip) {
        Text("Skip", color = Mist300, style = SecondBrainTypography.bodySmall)
    }
}

@Composable
private fun MinutePicker(label: String, minute: Int, onChange: (Int) -> Unit) {
    SbSectionTitle(label, color = StreakAccent)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { onChange(((minute - 15) + 1440) % 1440) }) {
            Text("−", style = SecondBrainTypography.headlineMedium, color = StreakAccent)
        }
        Text(
            "%02d:%02d".format(minute / 60, minute % 60),
            style = SecondBrainTypography.displayLarge,
            color = Mist100,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        TextButton(onClick = { onChange((minute + 15) % 1440) }) {
            Text("+", style = SecondBrainTypography.headlineMedium, color = StreakAccent)
        }
    }
}
