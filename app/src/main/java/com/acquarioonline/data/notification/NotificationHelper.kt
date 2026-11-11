package com.acquarioonline.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.acquarioonline.R

class NotificationHelper(private val context: Context) {
    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {

        val name =  context.getString(R.string.notificationTitle)
        val descriptionText = context.getString(R.string.descriptionText)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

    }

    fun showStateNotification(aquariumName: String?, stateName: String, currentState: String){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        val message = "$aquariumName "+"$stateName "+context.getString(R.string.actualState)+" $currentState"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_drop)
            .setContentTitle(context.getString(R.string.acquariumAllarm))
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    companion object {
        const val CHANNEL_ID = "aquarium_alerts_channel"
    }
}
