package com.smock210.mypet.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smock210.mypet.R
import com.smock210.mypet.sqlClass.Vaccination
import com.smock210.mypet.sqlClass.VetVisit

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "pet_notifications"
        const val REQUEST_CODE_VACCINATION = 100
        const val REQUEST_CODE_VISIT = 200
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.reminder_channel_desc)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun scheduleVaccinationReminder(v: Vaccination, petName: String) {
        if (!v.reminderEnabled) return

        val alarmTime = v.dateMillis - (24 * 3600 * 1000) // За 24 часа

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("type", "vaccination")
            putExtra("title", context.getString(R.string.reminder_vaccination_title, v.name))
            putExtra("message", context.getString(R.string.reminder_vaccination_text, v.name, petName))
            putExtra("vaccination_id", v.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_VACCINATION + v.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setAlarm(alarmTime, pendingIntent)
    }

    fun scheduleVisitReminder(v: VetVisit, petName: String) {
        val alarmTime = v.dateMillis - (24 * 3600 * 1000) // За 24 часа

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("type", "visit")
            putExtra("title", context.getString(R.string.reminder_visit_title))
            putExtra(
                "message",
                context.getString(R.string.reminder_visit_text, v.clinicName, v.doctorName, petName)
            )
            putExtra("visit_id", v.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_VISIT + v.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setAlarm(alarmTime, pendingIntent)
    }

    /**
     * Безопасное планирование: на Android 12+ точные будильники требуют
     * SCHEDULE_EXACT_ALARM — при отсутствии разрешения откатываемся на
     * неточный будильник, чтобы не получить SecurityException.
     */
    private fun setAlarm(alarmTime: Long, pendingIntent: PendingIntent) {
        // Напоминание в прошлое не имеет смысла
        if (alarmTime <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
        }
    }

    fun showNotificationNow(title: String, message: String, notificationId: Int) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    fun cancelVaccinationReminder(v: Vaccination) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("type", "vaccination")
            putExtra("vaccination_id", v.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_VACCINATION + v.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    fun cancelVisitReminder(v: VetVisit) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("type", "visit")
            putExtra("visit_id", v.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_VISIT + v.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }
}