package com.secondbrain.lock.ui.nav

import android.app.TimePickerDialog
import java.time.LocalTime

/** Opens the platform time picker anchored on [initial], reporting the picked time-of-day. Shared
 * with TasksPanel's "schedule a breakdown suggestion for a later day" flow. */
internal fun pickTime(context: android.content.Context, initial: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        initial.hour, initial.minute, true
    ).show()
}
