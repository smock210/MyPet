package com.smock210.mypet.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smock210.mypet.notifications.NotificationHelper
import com.smock210.mypet.R

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title")
            ?: context.getString(R.string.reminder_default)
        val message = intent.getStringExtra("message") ?: ""
        val notificationId = when (intent.getStringExtra("type")) {
            "vaccination" -> intent.getLongExtra("vaccination_id", 0L).toInt()
            "visit" -> intent.getLongExtra("visit_id", 0L).toInt()
            else -> System.currentTimeMillis().toInt()
        }

        NotificationHelper(context).showNotificationNow(title, message, notificationId)
    }
}