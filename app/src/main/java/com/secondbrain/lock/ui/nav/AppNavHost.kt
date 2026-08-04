package com.secondbrain.lock.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.secondbrain.lock.ui.screens.mind.MindScreen
import com.secondbrain.lock.ui.screens.mindverse.MindverseRoomScreen
import com.secondbrain.lock.ui.screens.mindverse.MindverseScreen
import com.secondbrain.lock.ui.screens.organize.NoteDetailScreen
import com.secondbrain.lock.ui.screens.organize.OrganizeScreen
import com.secondbrain.lock.ui.screens.work.AllTasksScreen
import com.secondbrain.lock.ui.screens.work.StreakDetailScreen
import com.secondbrain.lock.ui.screens.work.WorkScreen

private const val NOTE_DETAIL_ROUTE = "note/{noteId}"
private const val STREAK_DETAIL_ROUTE = "streak"
private const val ALL_TASKS_ROUTE = "tasks"

/** Reached from the top bar's avatar menu — distinct from [Destination.SHIELD], which stays
 * scoped to app-blocking config. */
const val ACCOUNT_SETTINGS_ROUTE = "account_settings"

/** Full-screen, bottom-nav-hiding call takeover pushed once Mindcord reports a joined room.
 * No argument — the screen reads the joined room straight off MindverseRepository's
 * currentRoom StateFlow, same as the room picker does. Exported (not private) so MainActivity
 * can compare against it to hide the bottom nav bar while this route is on top. */
const val MINDVERSE_ROOM_ROUTE = "mindverse_room"

/**
 * The 5 top-level tabs, plus a "note/{noteId}" detail route pushed on top from Organize (and
 * recursively from note detail's own links/related). Shield reuses the existing onboarding/
 * dashboard flow verbatim so app-blocking setup isn't regressed by this rewrite.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    shieldContent: @Composable (PaddingValues) -> Unit,
    accountSettingsContent: @Composable (PaddingValues) -> Unit,
    topBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val openNote: (String) -> Unit = { id -> navController.navigate("note/$id") }
    // Mirrors clicking a tag chip in pages/notes/[id].js, which routes to /organize?tag=X:
    // stash the tag on Organize's own back-stack entry (the standard Nav-Compose "return a
    // result to a previous screen" pattern) then pop everything back down to it, so a tag
    // tapped from a note reached via several link hops still lands cleanly on Organize.
    val openTag: (String) -> Unit = { tag ->
        navController.getBackStackEntry(Destination.ORGANIZE.route).savedStateHandle["tag"] = tag
        navController.popBackStack(Destination.ORGANIZE.route, inclusive = false)
    }

    NavHost(navController = navController, startDestination = Destination.WORK.route, modifier = modifier) {
        composable(Destination.WORK.route) {
            WorkScreen(
                contentPadding = contentPadding,
                onOpenStreak = { navController.navigate(STREAK_DETAIL_ROUTE) },
                onOpenAllTasks = { navController.navigate(ALL_TASKS_ROUTE) },
                topBar = topBar
            )
        }
        composable(Destination.ORGANIZE.route) { backStackEntry ->
            val tag by backStackEntry.savedStateHandle.getStateFlow<String?>("tag", null).collectAsState()
            OrganizeScreen(
                onOpenNote = openNote,
                tagFilter = tag,
                onClearTag = { backStackEntry.savedStateHandle["tag"] = null },
                contentPadding = contentPadding,
                topBar = topBar
            )
        }
        composable(Destination.MIND.route) {
            MindScreen(onOpenNote = openNote, contentPadding = contentPadding, topBar = topBar)
        }
        composable(Destination.MINDVERSE.route) {
            MindverseScreen(
                contentPadding = contentPadding,
                topBar = topBar,
                onOpenRoom = { navController.navigate(MINDVERSE_ROOM_ROUTE) }
            )
        }
        composable(MINDVERSE_ROOM_ROUTE) {
            MindverseRoomScreen(onBack = { navController.popBackStack() }, contentPadding = contentPadding)
        }
        composable(Destination.SHIELD.route) { shieldContent(contentPadding) }
        composable(ACCOUNT_SETTINGS_ROUTE) { accountSettingsContent(contentPadding) }
        composable(STREAK_DETAIL_ROUTE) {
            StreakDetailScreen(onBack = { navController.popBackStack() }, contentPadding = contentPadding)
        }
        composable(ALL_TASKS_ROUTE) {
            AllTasksScreen(onBack = { navController.popBackStack() }, contentPadding = contentPadding)
        }
        composable(
            route = NOTE_DETAIL_ROUTE,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId").orEmpty()
            NoteDetailScreen(
                noteId = noteId,
                onBack = { navController.popBackStack() },
                onOpenNote = openNote,
                onOpenTag = openTag,
                contentPadding = contentPadding
            )
        }
    }
}
