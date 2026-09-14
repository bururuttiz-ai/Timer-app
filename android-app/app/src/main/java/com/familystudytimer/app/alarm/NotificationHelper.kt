package com.familystudytimer.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.familystudytimer.app.R

private const val CHANNEL_ID = "study_reminder"

object NotificationHelper {

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            setSound(soundUri, android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 目覚まし時計のような全画面アラーム画面（[AlarmRingActivity]）を開くための通知を出す。
     * 画面ロック中・スリープ中はフルスクリーンで自動的に立ち上がり、画面が点いている間は
     * 通常の通知バナーとして表示され、タップすると同じ画面が開く。
     */
    fun showReminder(context: Context, childId: Long, childName: String, remainingMinutes: Int) {
        val ringIntent = Intent(context, AlarmRingActivity::class.java).apply {
            putExtra(EXTRA_CHILD_ID, childId)
            putExtra(EXTRA_CHILD_NAME, childName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val ringPendingIntent = PendingIntent.getActivity(
            context, childId.toInt(), ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(
                context.getString(R.string.notification_text_format, childName, remainingMinutes),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(ringPendingIntent)
            .setFullScreenIntent(ringPendingIntent, true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(childId.toInt(), notification)
    }
}
