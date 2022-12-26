package org.tensorflow.lite.examples.objectdetection

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat


class MyForegroundService: Service() {
    private val CHANNEL_ID = "my_channel_01"
    private val NOTIFICATION_ID = 123

    override fun onCreate() {
        super.onCreate()
        // Create a notification channel for devices running Android 8.0 and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "My Channel", NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "My channel description"
            channel.enableLights(true)
            channel.lightColor = Color.RED
            channel.enableVibration(true)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                channel
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Create the notification
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_overlay)
            .setContentTitle("My Service")
            .setContentText("Running in the foreground")
            .setAutoCancel(true)

        // Set the intent that will fire when the user taps the notification
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        builder.setContentIntent(pendingIntent)

        // Start the service in the foreground
        startForeground(NOTIFICATION_ID, builder.build())

        // Perform your long-running tasks here

        // Return the service's starting flag
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        // Return the communication channel to the service
        return null
    }
}
