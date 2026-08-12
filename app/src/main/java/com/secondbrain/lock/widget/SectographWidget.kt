package com.secondbrain.lock.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import com.secondbrain.lock.MainActivity
import com.secondbrain.lock.R

private val OpenCaptureKey = ActionParameters.Key<Boolean>(MainActivity.EXTRA_OPEN_CAPTURE)

class SectographWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val bitmap = SectographRenderer.render(context)
        provideContent {
            Box(modifier = GlanceModifier.fillMaxSize()) {
                // Tapping anywhere else on the widget opens the app normally, same as before P7.
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity<MainActivity>())
                ) {
                    Image(
                        provider = ImageProvider(bitmap),
                        contentDescription = "Today's schedule",
                        modifier = GlanceModifier.fillMaxSize()
                    )
                }
                // P7: a small capture entry point layered on top, in its own alignment box so it
                // doesn't disturb the full-size click target underneath it — same shield artwork
                // as the in-app capture button (P6a) and the launcher shortcut, for one consistent
                // "this icon means capture" reading everywhere it appears.
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(
                        modifier = GlanceModifier
                            .padding(6.dp)
                            .size(28.dp)
                            .background(ColorProvider(Color(0xFFFB4F40)))
                            .clickable(
                                actionStartActivity<MainActivity>(
                                    parameters = actionParametersOf(OpenCaptureKey to true)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_shield_custom),
                            contentDescription = "Capture",
                            modifier = GlanceModifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
