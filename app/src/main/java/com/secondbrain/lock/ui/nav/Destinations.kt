package com.secondbrain.lock.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.ui.graphics.vector.ImageVector

/** The 5 top-level bottom-nav destinations, replacing the web's top nav (Work/Organize/Mind/MINDVERSE). */
enum class Destination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    WORK("work", "Work", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    ORGANIZE("organize", "Organize", Icons.Filled.GridView, Icons.Outlined.GridView),
    MIND("mind", "Mind", Icons.Filled.Psychology, Icons.Outlined.Psychology),
    MINDVERSE("mindverse", "Mindverse", Icons.Filled.Public, Icons.Outlined.Public),
    SHIELD("shield", "Shield", Icons.Filled.Shield, Icons.Outlined.Shield);

    companion object {
        // SHIELD is intentionally left off the bottom bar for now (still reachable via the top
        // bar's settings gear) — 4 evenly-split tabs reads better around the center "+" notch.
        //
        // A `get()`, not a cached `val` (P11) — MINDVERSE is gated behind CommunityState.enabled
        // (defaults off: cross-account live chat/video with no moderation/reporting/blocking
        // yet), and this needs to react live when that toggle flips, the same reason every theme
        // color in ui/theme/Color.kt is a getter and not a cached val. Dropping to 3 tabs makes
        // BottomBar's notch layout asymmetric (built for an even split) — accepted for this
        // prompt, the full nav rebalance is P21.
        val bottomBarOrder: List<Destination>
            get() = if (CommunityState.enabled) listOf(WORK, ORGANIZE, MIND, MINDVERSE) else listOf(WORK, ORGANIZE, MIND)

        fun fromRoute(route: String?): Destination = entries.find { it.route == route } ?: WORK
    }
}
