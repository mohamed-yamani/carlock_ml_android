/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.objectdetection

import DownloadTask
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.tensorflow.lite.examples.objectdetection.databinding.ActivityMainBinding

//  to download and save a TFLite model
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.FileInputStream


import androidx.core.content.ContextCompat
import org.tensorflow.lite.examples.objectdetection.other.Constants.ACTION_SHOW_C_FRAGMENT
import org.tensorflow.lite.examples.objectdetection.other.Constants.MATRICULES_LIST_URL
import org.tensorflow.lite.examples.objectdetection.other.Constants.REQUEST_CODE_PERMISSIONS
import org.tensorflow.lite.examples.objectdetection.services.MyService
import timber.log.Timber


/**
 * Main entry point into our app. This app follows the single-activity pattern, and all
 * functionality is implemented in the form of fragments.
 */



class MainActivity : AppCompatActivity() {

    private lateinit var activityMainBinding: ActivityMainBinding
    lateinit var binding: ActivityMainBinding

    // localition
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.plant(Timber.DebugTree())
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        get_matricules()
//        if (IS_FIRST_TIME) {
//            val intent = Intent(this, ToastService::class.java)
//            startService(intent)
//            IS_FIRST_TIME = false
//        }

        requestPermissions()

//        val intent = Intent(this, MyService::class.java)
//        startService(intent)





        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

//        get_matricules()
//        startService(Intent(this@MainActivity, MyService::class.java))
        // location

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        getCurrentLocation();

//        // Create an intent to start the MyForegroundService class
//        val intent = Intent(this, MyForegroundService::class.java)
//
//        // Start the service
//        startForegroundService(intent);
//        ContextCompat.startForegroundService(this, intent)

//        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
//        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, arrayOf(permission), 1)
//        }

//        downloadTFLiteModel(this, "https://cdn.pixabay.com/photo/2015/04/23/22/00/tree-736885__480.jpg", "mmmmmodel.tflite")
//        saveImageToGallery(this)
        checkPermission()
        get_tflite(this)


    }

    override fun onStop() {
        super.onStop()

        // Start the MyService service
        val intent = Intent(this, MyService::class.java)
        startService(intent)
    }

    override fun onResume() {
        super.onResume()

        // Stop the MyService service
        val intent = Intent(this, MyService::class.java)
        stopService(intent)
    }



    private val REQUEST_WRITE_STORAGE = 112

    fun checkPermission() {
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        val granted = ContextCompat.checkSelfPermission(this, permission)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                REQUEST_WRITE_STORAGE
            )
        } else {
            // Permission already granted, do your work
        }
    }

    private fun navigatToCameraFragmentIfNeeded(intent: Intent?) {
        if (intent?.action == ACTION_SHOW_C_FRAGMENT) {
        }
    }

    fun requestPermissions() {
        val REQUEST_CAMERA_PERMISSION = 1

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), REQUEST_CAMERA_PERMISSION)
            }
        }

//        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.SYSTEM_ALERT_WINDOW)
//            != PackageManager.PERMISSION_GRANTED) {
//            // Request permissions
////            ActivityCompat.requestPermissions(
////                this, // Pass service context
////                arrayOf(android.Manifest.permission.SYSTEM_ALERT_WINDOW),
////                REQUEST_CODE_PERMISSIONS
////            )
//        }
    }

    fun getCurrentLocation() {
        if (checkPermissions())
        {
            if (isLocationEnable())
            {
                // final latitude and longitude
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermission()
                    return
                }
                fusedLocationProviderClient.lastLocation.addOnCompleteListener(this) { task ->
                    val location:Location? = task.result
                    if (location==null) {
                        Toast.makeText(this, "Null Recieved", Toast.LENGTH_SHORT).show()
                    }
                    else {
                        Toast.makeText(this, "Get Success", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // setting open here
                Toast.makeText(this, "Turn on location", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        } else {
            // request permission here
            requestPermission()
        }
    }

    private fun isLocationEnable():Boolean {
        val locationManger:LocationManager=getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManger.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManger.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf( android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.SYSTEM_ALERT_WINDOW),
        PERMISSION_REQUEST_ACCESS_LOCATION
        )
    }

    companion object {
        private const val PERMISSION_REQUEST_ACCESS_LOCATION=100
    }

    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_WRITE_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, do your work
                } else {
                    // Permission denied, show an explanation or disable the functionality that requires this permission
                }
            }
        }

        if (requestCode == PERMISSION_REQUEST_ACCESS_LOCATION) {
            if (grantResults.isEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(applicationContext,"Granted", Toast.LENGTH_SHORT).show()
                getCurrentLocation()
            }
            else {
                Toast.makeText(applicationContext,"Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Workaround for Android Q memory leak issue in IRequestFinishCallback$Stub.
            // (https://issuetracker.google.com/issues/139738913)
            finishAfterTransition()
        } else {
            super.onBackPressed()
        }
    }
}

var listOfMatricules: List<String> = listOf("")
public fun get_matricules() {

    println("get_matricule start")
//    var token = "bb9c96f6634c4c82e9f551611b074994e1e8528466fdc3c735774580eb1871a7"

//    val url = URL("https://eoucl1hfxkk6c0o.m.pipedream.net")
//    val connection = url.openConnection()
    // BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
    //     var line: String?
    //     while (inp.readLine().also { line = it } != null) {
    //         println("get_matricules ${line}")
    //         line = line!!.replace("\"", "")
    //         line = line!!.replace("[", "")
    //         line = line!!.replace("]", "")
    //         line = line!!.replace(" ", "")
    //         var ourList = line!!.split(",");
    //         listOfMatricules = ourList
    //         return;
    //     }
    // }

    val client = OkHttpClient()
    val request = Request.Builder()
        .url(MATRICULES_LIST_URL)
        .get()
        .addHeader("Authorization", "Bearer $ourToken")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            println("get_matricules onFailure")
        }

        override fun onResponse(call: Call, response: Response) {
            println("get_matricules onResponse")
            val body = response.body?.string()
            println("get_matricules $body")
            body?.let {
                var line = it
                line = line.replace("\"", "")
                line = line.replace("[", "")
                line = line.replace("]", "")
                line = line.replace(" ", "")
                var ourList = line.split(",");
                listOfMatricules = ourList
                println("listOfMatricules callback: " + listOfMatricules)
                return;
            }
        }
    })

    println("get_matricule end")
}

fun downloadTFLiteModel(context: Context, url: String, fileName: String) {
    val request = Request.Builder()
        .url(url)
        .build()

    val client = OkHttpClient()
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            // Handle failure
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                val inputStream = response.body!!.byteStream()
                val fileOutputStream = context.assets.openFd(fileName).createOutputStream()
                val buffer = ByteArray(1024)
                var len = inputStream.read(buffer)
                while (len != -1) {
                    fileOutputStream.write(buffer, 0, len)
                    len = inputStream.read(buffer)
                }
                fileOutputStream.close()
                inputStream.close()
                println("mmmmodel.tflite saved")
            } else {
                // Handle unsuccessful response
                println("mmmmodel.tflite unsuccessful response")
            }
        }
    })
}

fun get_tflite(context: Context) {
    val downloadTask = DownloadTask(context, object : DownloadTask.OnDownloadListener {
        override fun onProgressUpdate(progress: Int) {
            // Update progress bar with download progress
        }

        override fun onPostExecute(result: String) {
            if (result == "Success") {
                // Download successful, do something
            } else {
                // Download failed, do something
            }
        }
    })
    downloadTask.execute("https://carlock.icebergtech.net/api/v1/core/mlmodel/platefinder/download/", "bb9c96f6634c4c82e9f551611b074994e1e8528466fdc3c735774580eb1871a7")
}

fun copyFileToAssets(context: Context) {
    val inputStream = FileInputStream("model.tflite")
    val outputStream = context.assets.openFd("model.tflite").apply {
        createOutputStream().use { output ->
            val buffer = ByteArray(1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            output.close()
        }
    }
    inputStream.close()
}




