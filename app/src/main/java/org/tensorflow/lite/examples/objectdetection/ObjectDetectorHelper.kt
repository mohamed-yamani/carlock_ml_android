/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tensorflow.lite.examples.objectdetection

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.location.Location
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.HttpRequest
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.HttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import org.json.JSONObject
import org.tensorflow.lite.examples.objectdetection.fragments.registrationNumber
import org.tensorflow.lite.examples.objectdetection.other.Constants.MATCH_CREATE_URL
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.*
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Future
import kotlin.system.measureTimeMillis
import kotlin.time.measureTime


var index = 0;
//val listOfMatricules: List<String> = listOf("")
//val listOfMatricules = listOf("65528SAS8", "83092SAS72", "20181SHS1")
var imageToPost:String? = null;
var locationR: Location? = null;
var croppedImage: String?=null;

class ObjectDetectorHelper (
  var threshold: Float = 0.5f,
  var thresholdPr: Float = 0.15f,
  var numThreads: Int = 2,
  var maxResults: Int = 10,
  var maxResultsPr: Int = 10,
  var currentDelegate: Int = 0,
  var currentModel: Int = 0,
  val context: Context,
  val objectDetectorListener: DetectorListener?,
)  {

    // For this example this needs to be a var so it can be reset on changes. If the ObjectDetector
    // will not change, a lazy val would be preferable.
    private var objectDetector: ObjectDetector? = null

    init {
        setupObjectDetector()
    }

    fun clearObjectDetector() {
        objectDetector = null
    }

    // Initialize the object detector using current settings on the
    // thread that is using it. CPU and NNAPI delegates can be used with detectors
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the detector
    fun setupObjectDetector() {
        // Create the base options for the detector using specifies max results and score threshold
        val optionsBuilder =
            ObjectDetector.ObjectDetectorOptions.builder()
                .setScoreThreshold(threshold)
                .setMaxResults(maxResults)

        // Set general detection options, including number of used threads
        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads)

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                // Default
            }
            DELEGATE_GPU -> {
                if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                    baseOptionsBuilder.useGpu()
                } else {
                    objectDetectorListener?.onError("GPU is not supported on this device")
                }
            }
            DELEGATE_NNAPI -> {
                baseOptionsBuilder.useNnapi()
            }
        }

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        val modelName =
            when (currentModel) {
//                MODEL_MOBILENETV1 -> "object_labeler.tflite"
                MODEL_MOBILENETV1 -> "pf-ef0-cpu.tflite"
                MODEL_EFFICIENTDETV0 -> "pr-ef0-gpu-189.tflite"
                MODEL_EFFICIENTDETV1 -> "efficientdet-lite1.tflite"
                MODEL_EFFICIENTDETV2 -> "efficientdet-lite2.tflite"
                else -> "model-2531229698768240640_tflite_2022-10-28T22_57_41.244226Z_model.tflite"
            }

        try {
            objectDetector =
                ObjectDetector.createFromFileAndOptions(context, modelName, optionsBuilder.build())
        } catch (e: IllegalStateException) {
            objectDetectorListener?.onError(
                "Object detector failed to initialize. See error logs for details"
            )
            Log.e("Test", "TFLite failed to load model with error: " + e.message)
        }
    }


    fun detect(image: Bitmap, imageRotation: Int) {
        if (objectDetector == null) {
            setupObjectDetector()
        }

        // Inference time is the difference between the system time at the start and finish of the
        // process
        var inferenceTime = SystemClock.uptimeMillis()

        
        // Create preprocessor for the image.
        // See https://www.tensorflow.org/lite/inference_with_metadata/
        //            lite_support#imageprocessor_architecture
        val imageProcessor =
            ImageProcessor.Builder()
                .add(Rot90Op(-imageRotation / 90))
                .build()

        // Preprocess the image and convert it into a TensorImage for detection.
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

        val results = objectDetector?.detect(tensorImage)
        
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        if (results != null && results.isNotEmpty()) {
            // Get the inference time
//            inferenceTime = SystemClock.uptimeMillis() - inferenceTime

//            println("image and results are image height ${image.height} image with ${image.width} and $results")
//            Toast.makeText(context, "image and results are image height ${image.height} image with ${image.width} and $results", Toast.LENGTH_SHORT).show()

            // convert and save the image to the gallery
            if(isExternalStorageWritable())
            {
                try {
//                    if (++index % 3 === 0)
//                    {
                        println("index is : $index");
                        for (i in 0 until results.size) {
                            saveImage(image, results[i].boundingBox)
                        }
//                    }
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }   
            } else {
                println("External storage is not writable")
            }
        }

        
        objectDetectorListener?.onResults(
            results,
            inferenceTime,

            tensorImage.height,
            tensorImage.width,
            image,
            )
    }

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(
          results: MutableList<Detection>?,
          inferenceTime: Long,
          imageHeight: Int,
          imageWidth: Int,
          finalBitmap: Bitmap
        )
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DELEGATE_NNAPI = 2
        const val MODEL_MOBILENETV1 = 0
        const val MODEL_EFFICIENTDETV0 = 1
        const val MODEL_EFFICIENTDETV1 = 2
        const val MODEL_EFFICIENTDETV2 = 3
    }

    private fun saveImage(finalBitmap: Bitmap, boundingBox: RectF, context: Context = this.context) {
                // resize the final bitmap object detected result
       // scale the finalBitmap to match with imageHeight and imageWidth
       val scaledBitmap = Bitmap.createScaledBitmap(finalBitmap, finalBitmap.width, finalBitmap.height, true)
       val rotatedBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.width, scaledBitmap.height, Matrix().apply { postRotate(90f) }, true)
        
       // time now
       val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS")
                println("time now is start ${sdf.format(Date())}")
       try {
           matricule_crop(rotatedBitmap, context, threshold, thresholdPr, maxResults, maxResultsPr, finalBitmap);
       } catch (e: Exception) {
           e.printStackTrace()
       }
    }
    fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
         if (Environment.MEDIA_MOUNTED == state) {
             return  true
        }
        return false
    }

}

fun ByteArray.toBase64(): String =
    String(Base64.getEncoder().encode(this))

private fun bitmapToBase64(bitmap: Bitmap, context: Context): String? {
    // convert bitmap to jpeg

    return saveImg(bitmap);

    // val file = File(Environment.getExternalStorageDirectory().toString() + File.separator + "image.jpg")
    // file.createNewFile()
    // // Convert bitmap to byte array
    // val baos = ByteArrayOutputStream()
    // bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos) // It can be also saved it as JPEG
    // val b = baos.toByteArray()
    // val b64 = b.toBase64()
//    val byteArrayOutputStream = ByteArrayOutputStream()
//    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
//    val b64 = byteArrayOutputStream.toByteArray().toBase64()
    // return b64;
}

private fun bitmapToBase64c(bitmap: Bitmap, context: Context): String? {
    // convert bitmap to jpeg

    return saveImg(bitmap);

    // val file = File(Environment.getExternalStorageDirectory().toString() + File.separator + "image.jpg")
    // file.createNewFile()
    // // Convert bitmap to byte array
    // val baos = ByteArrayOutputStream()
    // bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos) // It can be also saved it as JPEG
    // val b = baos.toByteArray()
    // val b64 = b.toBase64()
//    val byteArrayOutputStream = ByteArrayOutputStream()
//    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
//    val b64 = byteArrayOutputStream.toByteArray().toBase64()
    // return b64;
}



private fun saveImg(imgBitmap: Bitmap): String? {
    val root = Environment.getExternalStorageDirectory().toString()
    val myDir = File("$root/captured_images_new")
    myDir.mkdirs()
    val fname = "Image-${System.currentTimeMillis()}.jpg"
    val file = File(myDir, fname)
    if (file.exists()) file.delete()
    try {

        try {
//            val out = FileOutputStream(file)
//            imgBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            // out to base64
            val baos = ByteArrayOutputStream()

            // scale the finalBitmap to match with imageHeight and imageWidth
            val scaledBitmap = Bitmap.createScaledBitmap(imgBitmap, imgBitmap.width, imgBitmap.height, true)
//            val rotatedBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.width, scaledBitmap.height, Matrix().apply { postRotate(90f) }, true)


            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos) // It can be also saved it as JPEG
            val b = baos.toByteArray()
            val b64 = b.toBase64()
//            out.flush()
//            out.close()
            return b64;
        } catch (e: Exception) {
            e.printStackTrace()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null;
}
var coordsResult: Detection? = null;

public fun matricule_crop(finalBitmap: Bitmap, context: Context, threshold: Float,thresholdPr: Float, maxResults: Int, maxResultsPr: Int, originalImage: Bitmap) {
    
    var image = finalBitmap
    val optionsBuilder =
            ObjectDetector.ObjectDetectorOptions.builder()
                .setScoreThreshold(threshold)
                .setMaxResults(maxResults)
    val modelName = "pf-ef0-cpu.tflite"
            println("Time kotlin Native tflite is start ${System.currentTimeMillis()}");

             val objectDetectorCroped =
                ObjectDetector.createFromFileAndOptions(context, modelName, optionsBuilder.build())
//    Log.d("start here", "___filePath___ start here")
//    val file = File(context.getExternalFilesDir(null), "model22.tflite")
//    val filePath = file.absolutePath
//    val objectDetectorCroped = ObjectDetector.createFromFileAndOptions(context, filePath, optionsBuilder.build())
//
//    println("___filePath___\n $filePath \nfile ${file}\nobjectDetectorCroped $objectDetectorCroped \n___filePath___")

            // check if the mobile is pivot or not to rotate the image
//            if (Configuration.ORIENTATION_PORTRAIT != context.resources.configuration.orientation){
//                val rotatedBitmap = Bitmap.createBitmap(image, 0, 0, image.width, image.height, Matrix().apply { postRotate(90f) }, true)
//                image = rotatedBitmap
//            }

    if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = windowManager.defaultDisplay.rotation
        if (rotation == Surface.ROTATION_90) {
            val rotatedBitmap = Bitmap.createBitmap(image, 0, 0, image.width, image.height, Matrix().apply { postRotate(270f) }, true)
            image = rotatedBitmap
        } else if (rotation == Surface.ROTATION_270) {
            val rotatedBitmap = Bitmap.createBitmap(image, 0, 0, image.width, image.height, Matrix().apply { postRotate(90f) }, true)
            image = rotatedBitmap
        }
        // val screenLayout = context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_LAYOUTDIR_MASK
        // if (screenLayout == Configuration.SCREENLAYOUT_LAYOUTDIR_RTL) {
        //     val rotatedBitmap = Bitmap.createBitmap(image, 0, 0, image.width, image.height, Matrix().apply { postRotate(90f) }, true)
        //     image = rotatedBitmap
        // } else {
        //     val rotatedBitmap = Bitmap.createBitmap(image, 0, 0, image.width, image.height, Matrix().apply { postRotate(270f) }, true)
        //     image = rotatedBitmap
        // }
    } else  {
        val screenLayout = context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_LAYOUTDIR_MASK
        if (screenLayout == Configuration.SCREENLAYOUT_LAYOUTDIR_RTL) {
            val rotatedBitmap = Bitmap.createBitmap(image, 0, 0, image.width, image.height, Matrix().apply { postRotate(180f) }, true)
            image = rotatedBitmap
        }
    }


    val tensorImage = TensorImage.fromBitmap(image)
            val results = objectDetectorCroped?.detect(tensorImage)
            if (results != null && results.isNotEmpty()) {
                println("RESULTS SIZE IS ${results.size}")

                println("Time kotlin Native tflite is end ${System.currentTimeMillis()}");

                for (i in 0 until results.size
                ) {
                    val cropedBitmap = Bitmap.createBitmap(image, results[i].boundingBox.left.toInt(), results[i].boundingBox.top.toInt(), results[i].boundingBox.width().toInt(), results[i].boundingBox.height().toInt())
                    imageToPost = bitmapToBase64c(originalImage, context)
                    croppedImage = bitmapToBase64c(cropedBitmap, context)
                    coordsResult = results[i]
                    letters_and_numbers_crop(cropedBitmap, context, thresholdPr, maxResultsPr);
                    val root = Environment.getExternalStorageDirectory().toString()
                    val myDir = File("$root/croped_images_2")
                    myDir.mkdirs()
                    // time human readable format
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS")
                    val fname = "Image-${i}__${Date()}.jpg"
                    val file = File(myDir, fname)
                    if (file.exists()) file.delete()
                    println("file path is $file")
                    val out = FileOutputStream(file)
//                    cropedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    cropedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    out.flush()
                    out.close()
                }
            }
    }

var first_get_calling = 0;

public fun letters_and_numbers_crop(finalBitmap: Bitmap, context: Context, thresholdPr: Float, maxResultsPr: Int) {

    if (first_get_calling++ == 0) {
        println("get_matricules called")
        get_matricules()
    }

    val scaledBitmap = Bitmap.createScaledBitmap(finalBitmap, 470, 110, true)    
    val optionsBuilder =
            ObjectDetector.ObjectDetectorOptions.builder()
                .setScoreThreshold(thresholdPr)
                .setMaxResults(maxResultsPr)
    val modelName =
         "pr-ef0-gpu-189.tflite"

            println("Time kotlin Native tflite is start ${System.currentTimeMillis()}");
      
             val objectDetectorCroped =
                ObjectDetector.createFromFileAndOptions(context, modelName, optionsBuilder.build())
            val tensorImage = TensorImage.fromBitmap(scaledBitmap)
            val results = objectDetectorCroped?.detect(tensorImage)
            if (results != null && results.isNotEmpty()) {
                println("RESULTS SIZE IS ${results.size}")

                println("Time kotlin Native tflite is end ${System.currentTimeMillis()}");



//                println("all results is : ${results}")
                val resultso = order_results(results);
                val resultss = result_to_string(resultso)


//                FragmentCameraBinding binding;
//                binding.bottomSheetLayout.registrationNumberVal.text = String.format("444455|T|5")

                // time human readable format
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS")


                println("time now is ${sdf.format(Date())}")
                if (resultss.length > 7) {
                    registrationNumber = resultss;
                    if (listOfMatricules.contains(resultss)) {
                        registrationNumber = "$registrationNumber ✔"
                        post_result(finalBitmap, context);
                    }
                }
                println("all results is : $resultss")
            }
    }

public fun order_results(results: List<Detection>): List<Detection> {
    val sortedResults = results.sortedBy {  it.boundingBox.left }

    //    println("list detection ... $results boundingbox ${results[0].categories[0].label}")
    return  filter_results(sortedResults);
}

public fun filter_results(results: List<Detection>): List<Detection> {
    val filtred_result: MutableList<Detection> = mutableListOf<Detection>();


    var best_element: Detection? = null;
    for (result in results)
    {
//        println("init result ---- : $result")
        if (best_element == null)
        {
            best_element = result;
        }
        if (result.boundingBox.left - best_element.boundingBox.left < 10)
        {
            if (best_element.categories[0].score < result.categories[0].score)
            {
               best_element = result;
            }
        } else {
            filtred_result.add(best_element)
            best_element = result
        }
    }

    if (best_element != null)
    {
        filtred_result.add(best_element)
    }
    return filtred_result;
}

fun getLetters(str: String): String {
    return str.filter { it.isLetter() }
}


public fun result_to_string(results: List<Detection>): String {
    var result_string = ""
    for (i in 0 until results.size
    ) {
        result_string += results[i].categories[0].label
    }
    var result_string_1 = result_string.replace("SS", "S")
        .replace("AA", "A")
        .replace("BB", "B")
        .replace("HH", "H")
        .replace("S1S", "SAS")
        .replace("S9S", "SWS")

        result_string_1.replace("S", "")
        var charachter = getLetters(result_string_1)
        result_string_1.replace(charachter, "S${charachter}S")
        var list_result_string = result_string_1.split("S").toMutableList()
        list_result_string[0] = list_result_string[0].replace("A", "1").replace("W", "9")
        list_result_string[2] = list_result_string[2].replace("A", "1").replace("W", "9")
        println("our list_result_string : $list_result_string")
        val separator = "S"
        val result = list_result_string.joinToString(separator)
        println("our string_joined : $result")
        return result
}

fun longLog(str: String?) {
    if (str!!.length > 4000) {
        Log.d("", str!!.substring(0, 4000))
        longLog(str.substring(4000))
    } else Log.d("", str!!)
}



var ourToken:String? = "";



public fun post_result(finalBitmap : Bitmap, context: Context) {
    println("Coroutine_Scope start here ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS"))}")
    // async

    CoroutineScope(IO).launch {
        async {
            println("cuted 1 image")
            longLog(croppedImage);
            println("cuted 2 image")
            get_location(context)
            // Create JSON using JSONObject
                        try {
                // sleep for one second
                Thread.sleep(150)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            
            val jsonObject = JSONObject()
            jsonObject.put("matricule_str", "${registrationNumber.replace(" ✔", "")}")
            jsonObject.put("location", "{\"lat\": ${locationR?.latitude ?: 0.0}, \"long\": ${locationR  ?.longitude ?: 0.0}}")
            jsonObject.put("coords", "{\"left\": ${coordsResult?.boundingBox?.left}, \"right\": ${coordsResult?.boundingBox?.right}, \"top\": ${coordsResult?.boundingBox?.top}, \"bottom\": ${coordsResult?.boundingBox?.bottom}}")
            jsonObject.put("picture", imageToPost)
//            jsonObject.put("photo2", croppedImage)


            // Convert JSONObject to String
            val jsonObjectString = jsonObject.toString()

            GlobalScope.launch(Dispatchers.IO) {
//                var token = "bb9c96f6634c4c82e9f551611b074994e1e8528466fdc3c735774580eb1871a7"
//                val url = URL("https://eoy9xavp8k1exi6.m.pipedream.net")
                val url = URL(MATCH_CREATE_URL)
                val httpURLConnection = url.openConnection() as HttpURLConnection
                httpURLConnection.doOutput = true
                httpURLConnection.requestMethod = "POST"
                httpURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                httpURLConnection.setRequestProperty("Accept", "application/json")
                // token is the token of the user
                httpURLConnection.setRequestProperty("Authorization", "Bearer $ourToken")
                httpURLConnection.doInput = true
                httpURLConnection.doOutput = true

                // Send the JSON we created
                val outputStreamWriter = OutputStreamWriter(httpURLConnection.outputStream)
                outputStreamWriter.write(jsonObjectString)
                outputStreamWriter.flush()

                // Check if the connection is successful
                val responseCode = httpURLConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = httpURLConnection.inputStream.bufferedReader()
                        .use { it.readText() }  // defaults to UTF-8
                    withContext(Dispatchers.Main) {
                        // Convert raw JSON to pretty JSON using GSON library
                        Log.d("Pretty Printed JSON :", response)
                    }
                } else {
                    Log.e("INTERCONNECTION_ERROR", responseCode.toString())
                }
            }
        }
    }
    println("Coroutine_Scope end here ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS"))}")
}


public fun get_location(context: Context) {
    var fusedLocationProviderClient: FusedLocationProviderClient? = null;

    fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context);
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        return;
    } else {
        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                locationR = location!!
            }
        }
    }
}