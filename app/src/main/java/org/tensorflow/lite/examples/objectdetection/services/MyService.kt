package org.tensorflow.lite.examples.objectdetection.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Camera
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import org.tensorflow.lite.examples.objectdetection.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.isNullOrEmpty


class MyService : Service(), SurfaceHolder.Callback, Camera.PreviewCallback {
    private val REQUEST_CODE_PERMISSIONS = 101

    private var surfaceView: SurfaceView? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var camera: Camera? = null
    private var imageCounter = 0

    companion object {
        const val CHANNEL_ID = "my_service_channel"
        const val NOTIFICATION_ID = 1
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun service(): MyService = this@MyService
    }


    override fun onCreate() {
        super.onCreate()

        // Create the notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "My Service", NotificationManager.IMPORTANCE_DEFAULT)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MyService", "Service started")
        // Set up surface view and holder

        // Create the notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("My Service")
            .setContentText("The service is running in the foreground")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.BADGE_ICON_SMALL)
            .build()

        // Start the service in the foreground
        startForeground(NOTIFICATION_ID, notification)

        // Check for permissions

            // Permissions granted, set up surface view and holder
            surfaceView = SurfaceView(this)
            surfaceHolder = surfaceView?.holder
            surfaceHolder?.addCallback(this)

            // Add surface view to window
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val layoutParams = WindowManager.LayoutParams()
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            windowManager.addView(surfaceView, layoutParams)

        // Start camera preview
        Log.d("MyService", "start")
        startCameraPreview()
        Log.d("MyService", "end")

        return super.onStartCommand(intent, flags, startId)
    }

    private fun startCameraPreview() {
        Log.d("MyService", "startCameraPreview")
        // Open camera
        camera = Camera.open()

        // Set preview display
        camera?.setPreviewDisplay(surfaceHolder)

        // Set preview callback
        camera?.setPreviewCallback(this)
        // Start preview
        val success = camera?.startPreview()

        // Log result
        Log.d("MyService", "Camera preview started: $success")
    }

    override fun surfaceChanged(p0: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("MyService", "surfaceChanged")

        // Update camera preview size

        val parameters = camera?.parameters
        
        val previewSizes = parameters?.supportedPreviewSizes
        if (previewSizes.isNullOrEmpty()) {
            Log.e("MyService", "No supported preview sizes")
            return
        }

        // Get largest preview size
        var previewSize = previewSizes[0]
        for (size in previewSizes) {
            if (size.width > previewSize.width) {
                previewSize = size
            }
        }

        // Set preview size
        parameters?.setPreviewSize(previewSize.width, previewSize.height)
        camera?.parameters = parameters
//        val parameters = camera?.parameters
        parameters?.setPreviewSize(width, height)
        camera?.parameters = parameters
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        Log.d("MyService", "surfaceDestroyed")
        // Stop camera preview
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    override fun surfaceCreated(p0: SurfaceHolder) {
        Log.d("MyService", "surfaceCreated")
        // Start camera preview
        startCameraPreview()
    }

    private fun playSong(){
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val mediaPlayer = MediaPlayer.create(this, ringtone)
        mediaPlayer.start()
    }

    override fun onPreviewFrame(p0: ByteArray?, p1: Camera?) {
        if (p0 == null || p0.isEmpty()) {
            Log.e("MyService", "Received empty preview frame")
            return
        }
        try {
            val image = BitmapFactory.decodeByteArray(p0, 0, p0.size)
            if (image == null) {
                Log.e("MyService", "Failed to decode preview frame to bitmap")
                return
            }
            // Save the image or do something else with it
        } catch (e: Exception) {
            Log.e("MyService", "Error decoding preview frame", e)
        }
    }

    private fun saveImage(image: Bitmap) {
        // Create a file to save the image
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFile = File.createTempFile(
            imageFileName, /* prefix */
            ".jpg", /* suffix */
            storageDir      /* directory */
        )

        // Save the image
        val outputStream = FileOutputStream(imageFile)
        image.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.close()

        // Add the image to the media store
        MediaScannerConnection.scanFile(
            this,
            arrayOf(imageFile.absolutePath),
            null
        ) { _, _ ->
            imageCounter++
            Log.d("MyService", "Saved image $imageCounter")
        }
    }
}


