package com.secondbrain.lock.ui.screens.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import kotlinx.coroutines.delay

private data class WelcomeBackCopy(val title: String, val body: String, val bankruptcyPrimary: Boolean)

private fun copyFor(days: Int): WelcomeBackCopy = when {
    days >= 30 -> WelcomeBackCopy(
        title = "Hello again.",
        body = "Let's start clean. Your notes are safe in Library.",
        bankruptcyPrimary = true
    )
    days >= 7 -> WelcomeBackCopy(
        title = "Good to see you.",
        body = "Everything's still here. Want to start fresh instead?",
        bankruptcyPrimary = true
    )
    else -> WelcomeBackCopy(
        title = "Welcome back.",
        body = "I moved everything to today. Nothing's broken.",
        bankruptcyPrimary = false
    )
}

/**
 * Shown once, the first time [daysSinceLastOpen] indicates a real lapse (>= 3 days) since the app
 * was last opened — see [WorkScreen]'s [com.secondbrain.lock.data.WelcomeBackPrefs] check.
 *
 * ABSOLUTELY FORBIDDEN here, per spec: any number describing what was missed ("14 overdue"), any
 * streak mention, any red, any exclamation mark, any emoji. This screen exists to make coming
 * back feel safe, not to itemize the lapse.
 *
 * At 30+ days bankruptcy runs automatically (no button needed — the copy above just informs the
 * user it happened) rather than asking them to confirm clearing a month-old pile.
 *
 * [onBankruptcy] hands the undo snapshot up to the CALLER rather than this composable owning its
 * own undo bar — [onDismiss] tears this whole composable out of composition immediately after a
 * bankruptcy run (same as [onShowOneThing]), so any state that needs to outlive that dismissal,
 * like a 10-second undo window, has to live in a parent that isn't also being torn down.
 *
 * [onBankruptcy] is a plain trigger, not a suspend call this composable awaits itself — the
 * actual [TasksRepository.bankruptcy] call has to run on the CALLER's [rememberCoroutineScope],
 * not one scoped to this composable. A scope from `rememberCoroutineScope()` in here would get
 * cancelled the instant [onDismiss] tears this composable out of composition, right after
 * `runBankruptcy()` fires it — killing the in-flight call before it can report its snapshot back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeBackSheet(
    daysSinceLastOpen: Int,
    onShowOneThing: (taskId: String?) -> Unit,
    onBankruptcy: () -> Unit,
    onDismiss: () -> Unit
) {
    val copy = remember(daysSinceLastOpen) { copyFor(daysSinceLastOpen) }

    LaunchedEffect(daysSinceLastOpen) {
        if (daysSinceLastOpen >= 30) onBankruptcy()
    }

    fun runBankruptcy() {
        onBankruptcy()
        onDismiss()
    }

    fun showOneThing() {
        val smallest = TasksRepository.tasks.value
            .filter { !it.done }
            .minByOrNull { it.durationMin ?: Int.MAX_VALUE }
        onShowOneThing(smallest?.id)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink900) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(copy.title, style = SecondBrainTypography.headlineMedium, color = Mist100, textAlign = TextAlign.Center)
                Text(copy.body, style = SecondBrainTypography.bodyLarge, color = Mist300, textAlign = TextAlign.Center)
            }

            if (daysSinceLastOpen >= 30) {
                // Already cleared automatically above — one door out, not a choice to make.
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White)
                ) { Text("Show me one thing to do") }
            } else if (copy.bankruptcyPrimary) {
                // 7-29 days: bankruptcy is the primary action, "show me one thing" demotes to
                // a text link — the spec's own emphasis swap for this tier.
                Button(
                    onClick = ::runBankruptcy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White)
                ) { Text("Clear the whole list") }
                TextButton(onClick = ::showOneThing) {
                    Text("Show me one thing to do", color = Mist300, style = SecondBrainTypography.bodyMedium)
                }
            } else {
                Button(
                    onClick = ::showOneThing,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White)
                ) { Text("Show me one thing to do") }
                TextButton(onClick = ::runBankruptcy) {
                    Text("Clear the whole list", color = Mist300, style = SecondBrainTypography.bodyMedium)
                }
            }
        }
    }
}

/** A lightweight bottom banner rather than Material3's Scaffold-level SnackbarHost — nothing else
 * in this app's nav chrome hoists a SnackbarHostState, and threading one through just for this
 * single, self-contained use is more plumbing than the feature is worth. Auto-dismisses after 10
 * seconds, matching the spec's "10-second undo snackbar is the entire safety net." Owned by the
 * CALLER (see [WelcomeBackSheet]'s doc comment) so it outlives the sheet's own dismissal.
 *
 * [bottomPadding] MUST be [PaddingValues.calculateBottomPadding] from whatever contentPadding the
 * caller was given — WorkScreen's own outer Box fills the full screen height rather than stopping
 * above the bottom nav bar (only its scrollable Column applies that inset), so without this the
 * bar's BottomCenter alignment pins it to the literal bottom of the screen: physically behind the
 * opaque nav bar, composed and state-correct but permanently invisible. Confirmed by direct
 * reproduction — logging showed the backing state updating correctly on every tap while the bar
 * stayed invisible, including when forced to render unconditionally. */
@Composable
fun BankruptcyUndoBar(bottomPadding: Dp = 0.dp, onUndo: () -> Unit, onExpire: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(10_000)
        onExpire()
    }
    // fillMaxSize (not fillMaxWidth) is what makes BottomCenter actually mean "bottom of the
    // screen" rather than just centering the Row within its own wrap-content bounds — same
    // pattern WorkScreen's own celebration overlay already uses. zIndex forces this above
    // WorkScreen's own scrollable content unconditionally — composition order alone wasn't
    // reliably enough (this bar's bounds landed correctly in the accessibility tree but painted
    // behind the still-open TASKS card in practice).
    Box(
        Modifier.fillMaxSize().padding(16.dp).padding(bottom = bottomPadding).zIndex(10f),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink950, RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("List cleared.", color = Mist100, style = SecondBrainTypography.bodyMedium)
            TextButton(onClick = onUndo) {
                Text("Undo", color = StreakAccent, style = SecondBrainTypography.bodyMedium)
            }
        }
    }
}
