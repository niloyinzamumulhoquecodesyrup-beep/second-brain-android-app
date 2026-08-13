package com.secondbrain.lock.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * P21 collapses the bottom nav to 3 tabs: Today / Library / Me. TODAY and LIBRARY keep their P0
 * route strings ("work"/"organize") to avoid touching every existing composable(...) call and
 * deep-link — only the enum name and label changed, per P21's own "keep route strings stable"
 * option. MIND and MINDVERSE are no longer bottom-bar destinations (folding Mind's content into
 * Library-as-Insights is a separate, not-yet-done migration — see MeScreen's KDoc) but keep their
 * own routes so existing navigation to them still resolves; MINDVERSE is reachable from Me when
 * community is enabled instead of getting its own bottom-bar slot. SHIELD is unchanged — reached
 * from Me instead of the top bar's avatar menu.
 */
enum class Destination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TODAY("work", "Today", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    LIBRARY("organize", "Library", Icons.Filled.GridView, Icons.Outlined.GridView),
    ME("me", "Me", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle),
    MIND("mind", "Mind", Icons.Filled.Psychology, Icons.Outlined.Psychology),
    MINDVERSE("mindverse", "Mindverse", Icons.Filled.Public, Icons.Outlined.Public),
    SHIELD("shield", "Shield", Icons.Filled.Shield, Icons.Outlined.Shield);

    companion object {
        // Fixed at exactly 3 — P21's whole point is a bottom bar that's always this shape,
        // unlike the old 4-tab layout which grew/shrank with CommunityState.enabled.
        val bottomBarOrder: List<Destination> = listOf(TODAY, LIBRARY, ME)

        fun fromRoute(route: String?): Destination = entries.find { it.route == route } ?: TODAY
    }
}
