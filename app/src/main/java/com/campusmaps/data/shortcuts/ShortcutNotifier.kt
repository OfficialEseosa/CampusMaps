package com.campusmaps.data.shortcuts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.campusmaps.R

// Posts "Your shortcut Library cut through is live." when a submission gets approved.
// Quietly does nothing if the user has not allowed notifications.
class ShortcutNotifier(private val context: Context) {

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.shortcut_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifyLive(shortcutName: String) {
        // Before Android 13 notifications need no runtime permission.
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("CampusMaps")
            .setContentText("Your shortcut $shortcutName is live.")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(shortcutName.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "shortcut_reviews"
    }
}
