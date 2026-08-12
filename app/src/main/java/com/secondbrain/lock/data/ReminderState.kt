package com.secondbrain.lock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Device-local presentation state for a reminder's snooze escalation — deliberately never synced.
 * Snoozing on your phone must not escalate the same reminder on your tablet, so this lives only in
 * this device's Room database, keyed by the same reminderId [com.secondbrain.lock.service.ReminderNotifier]
 * and [com.secondbrain.lock.receiver.ReminderActionReceiver] already use. */
@Entity(tableName = "reminder_state")
data class ReminderState(
    @PrimaryKey val reminderId: String,
    val snoozeCount: Int = 0,
    val lastFiredAt: Long = 0
)
