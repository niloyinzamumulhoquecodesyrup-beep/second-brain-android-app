package com.secondbrain.lock.ui.nav

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.secondbrain.lock.R
import com.secondbrain.lock.data.FeedbackUtil
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.NavBarSurface
import com.secondbrain.lock.ui.theme.SbThemeState
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.ThemeMode
import kotlin.math.atan2
import kotlin.math.sqrt

private val BarCornerRadius = 24.dp
private val ButtonSize = 56.dp
private val ButtonRadius = ButtonSize / 2
// 55% less than the 20.dp this was before.
private val ButtonGap = 9.dp
// Concentric with the button (same center point) — the only way to guarantee the gap to the
// button's edge is the same width everywhere along this arc, not just directly underneath it.
private val NotchRadius = ButtonRadius + ButtonGap
// How far the button's center sits above the bar's top edge. The notch geometry above
// (NotchRadius/FilletRadius) is untouched — only this offset moves.
private val ButtonLift = ButtonRadius * 0.8f
// Blends the notch's arc into the flat bar edge with a matching tangent on both sides (no pointy
// corner) without pinching the gap the way a plain bezier dip did.
private val FilletRadius = 24.dp
// A little shorter than Material3's ~80dp default, to match the shallower notch below.
// NavigationBarItem still centers its icon/label vertically within whatever height it's given.
private val BarHeight = 72.dp

/**
 * 4-tab bottom nav (Work/Organize/Mind/Mindverse). The center button is capture, not a nav item —
 * tap opens [com.secondbrain.lock.ui.nav.FastCaptureSheet] to type, long-press is reserved for
 * voice capture once [com.secondbrain.lock.util.VoiceTranscriber] is wired into that sheet (P12) —
 * until then it just opens the same sheet as a tap, per that prompt's own explicit fallback rather
 * than stubbing a fake mic state.
 *
 * This button used to share the frame with a second, independent floating button in the
 * top-right corner (deleted here) that also wore this same shield artwork but did something
 * completely unrelated — started ad-hoc voice capture via [com.secondbrain.lock.util.VoiceTranscriber],
 * with no connection to [Destination.SHIELD] (the actual app-usage-lock screen, which has always
 * been reached separately via the top bar's settings icon and is untouched by this change). One
 * icon meaning two different things (an app-blocking feature AND capture) was the problem worth
 * fixing; this consolidates the artwork onto the one button that's actually capture.
 *
 * The bar is attached flush to the bottom and both side edges (no floating margin), with a notch
 * carved into its top-center where the raised button sits. The notch's main curve is an arc
 * concentric with the button, so the gap to the button's edge is uniform everywhere along it (not
 * just directly below) — with small fillet arcs blending it into the flat bar edge on both sides
 * so there's no pointy corner where they meet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomBar(navController: NavHostController, onCapture: (voice: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route
    val leftDestinations = Destination.bottomBarOrder.take(2)
    val rightDestinations = Destination.bottomBarOrder.drop(2)
    val barShape = NotchedTopBarShape(BarCornerRadius, NotchRadius, FilletRadius)
    val context = LocalContext.current

    Box(modifier.fillMaxWidth()) {
        NavigationBar(
            containerColor = NavBarSurface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .shadow(elevation = 8.dp, shape = barShape)
                .clip(barShape)
        ) {
            val colors = NavigationBarItemDefaults.colors(
                selectedIconColor = StreakAccent,
                selectedTextColor = StreakAccent,
                unselectedIconColor = Mist300,
                unselectedTextColor = Mist300,
                // Web's nav has no selection "pill," just a color change — matches that instead of
                // a solid Material3 indicator chip.
                indicatorColor = Color.Transparent
            )
            leftDestinations.forEach { destination -> BarItem(destination, currentRoute, navController, colors) }
            // Reserves the same width as one nav item so the raised button below has clearance —
            // together with each NavigationBarItem's own internal weight(1f), this splits the bar
            // into 6 even slots (2 tabs, gap, 3 tabs).
            Box(Modifier.weight(1f, fill = true))
            rightDestinations.forEach { destination -> BarItem(destination, currentRoute, navController, colors) }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // Same horizontal center the notch arc uses; vertically lifted by [ButtonLift]
                // rather than a full ButtonRadius, so the button sits closer to the bar.
                .offset(y = -ButtonLift)
                .size(ButtonSize)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(StreakAccent)
                .combinedClickable(
                    onClick = { onCapture(false) },
                    onLongClick = {
                        FeedbackUtil.longPressTick(context)
                        onCapture(true)
                    },
                    onLongClickLabel = "Capture by voice"
                )
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Capture by voice") { onCapture(true); true }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Flat #FB4F40 by design, same in both themes — only the artwork on top of it swaps.
            // Light mode gets its own variant (pale disc behind the rings); dark mode keeps the
            // near-black one. Reading SbThemeState.mode here makes this recompose live when the
            // user flips the theme toggle.
            val captureRes = if (SbThemeState.mode == ThemeMode.LIGHT) {
                R.drawable.ic_shield_custom_light
            } else {
                R.drawable.ic_shield_custom
            }
            Image(
                painter = painterResource(captureRes),
                contentDescription = "Capture",
                modifier = Modifier.size(ButtonSize)
            )
        }
    }
}

/**
 * An edge-to-edge bar shape — square bottom/side edges (flush against the screen) with rounded
 * top corners and a notch cut into the top-center, built from three tangent-continuous arcs so
 * there's no pointy corner anywhere:
 *
 * 1. A "fillet" arc (radius [filletRadius]) rising from the flat bar edge.
 * 2. The main arc (radius [notchRadius]), concentric with the "+" button above it — since both
 *    share the same center point, every point on this arc is exactly [notchRadius] away from that
 *    center, so the gap to the button's edge is identical all along it, not just at the bottom.
 * 3. A mirrored fillet arc back down to the flat bar edge on the other side.
 *
 * Each pair of arcs is constructed to share both a point AND a tangent direction where they meet
 * (verified algebraically, not just visually), so the whole curve reads as one continuous sweep.
 */
private class NotchedTopBarShape(
    private val topCornerRadius: Dp,
    private val notchRadius: Dp,
    private val filletRadius: Dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = with(density) { topCornerRadius.toPx() }
        val r1 = with(density) { notchRadius.toPx() }
        val f = with(density) { filletRadius.toPx() }
        val centerX = size.width / 2f

        // Distance from the notch's center to where each fillet meets the flat edge — the
        // standard "circle-tangent-to-a-line-and-to-another-circle" fillet construction.
        val dx = sqrt(r1 * r1 + 2f * r1 * f)
        // Angle (from the notch's center) of the point where the main arc hands off to each
        // fillet — same value on both sides by symmetry.
        val handoffAngle = Math.toDegrees(atan2(f.toDouble(), dx.toDouble())).toFloat()

        val path = Path().apply {
            moveTo(corner, 0f)
            lineTo(centerX - dx, 0f)
            arcTo(
                rect = Rect(centerX - dx - f, 0f, centerX - dx + f, 2 * f),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f - handoffAngle,
                forceMoveTo = false
            )
            arcTo(
                rect = Rect(centerX - r1, -r1, centerX + r1, r1),
                startAngleDegrees = 180f - handoffAngle,
                sweepAngleDegrees = 2f * handoffAngle - 180f,
                forceMoveTo = false
            )
            arcTo(
                rect = Rect(centerX + dx - f, 0f, centerX + dx + f, 2 * f),
                startAngleDegrees = 180f + handoffAngle,
                sweepAngleDegrees = 90f - handoffAngle,
                forceMoveTo = false
            )
            lineTo(size.width - corner, 0f)
            quadraticBezierTo(size.width, 0f, size.width, corner)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            lineTo(0f, corner)
            quadraticBezierTo(0f, 0f, corner, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun RowScope.BarItem(
    destination: Destination,
    currentRoute: String?,
    navController: NavHostController,
    colors: NavigationBarItemColors
) {
    val selected = currentRoute == destination.route
    NavigationBarItem(
        selected = selected,
        onClick = {
            if (!selected) {
                // From a screen pushed on top of a tab (e.g. Statistics or All Tasks, both
                // reached via a plain navigate() with no popUpTo of their own), popUpTo(start
                // destination id){saveState=true} silently no-ops here — the back stack is left
                // unchanged even though the id matches the start destination's entry, so the tap
                // did nothing. Popping straight back to an already-visited tab by route sidesteps
                // that entirely; navigate() with the usual save/restore options is only needed the
                // first time a tab is opened, when there's no existing entry to pop back to.
                val alreadyOnBackStack = navController.popBackStack(destination.route, inclusive = false)
                if (!alreadyOnBackStack) {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
        icon = {
            Icon(
                if (selected) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = destination.label
            )
        },
        label = {
            Text(
                destination.label,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = colors
    )
}
