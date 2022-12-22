package org.tensorflow.lite.examples.objectdetection

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import org.tensorflow.lite.examples.objectdetection.databinding.FragmentCameraBinding

class MyService: Service() {

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        onTaskRemoved(intent)
        Toast.makeText(
            applicationContext, "This is a Service running in Background",
            Toast.LENGTH_SHORT
        ).show()
        return START_STICKY
    }
    override fun onBind(intent: Intent): IBinder? {
        // TODO: Return the communication channel to the service.
        throw UnsupportedOperationException("Not yet implemented")
    }
    override fun onTaskRemoved(rootIntent: Intent) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }
}