package com.secondbrain.lock.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import com.secondbrain.lock.MainActivity

class SectographWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val bitmap = SectographRenderer.render(context)
        provideContent {
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
        }
    }
}
