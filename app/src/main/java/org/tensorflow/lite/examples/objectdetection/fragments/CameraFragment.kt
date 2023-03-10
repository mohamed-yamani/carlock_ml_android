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
package org.tensorflow.lite.examples.objectdetection.fragments

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper
import org.tensorflow.lite.examples.objectdetection.R
import org.tensorflow.lite.examples.objectdetection.databinding.FragmentCameraBinding
import org.tensorflow.lite.examples.objectdetection.get_matricules
import org.tensorflow.lite.examples.objectdetection.ourToken
import org.tensorflow.lite.examples.objectdetection.services.MyService

import org.tensorflow.lite.task.vision.detector.Detection


var registrationNumber:String = "";

class CameraFragment : Fragment() , ObjectDetectorHelper.DetectorListener {


    private val TAG = "ObjectDetection"

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!



    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var bitmapBuffer: Bitmap
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

//    private fun sendCommandToService(action: String) = Intent(requireContext(), CameraService::class.java).also {

//        it.action = action
//        requireContext().startService(it)
//    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            val binder = service as ToastService.LocalBinder
//            val service = binder.service()
//            service.doSomething()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(context, MyService::class.java)
        context?.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)


    }

    override fun onStop() {
        super.onStop()
        context?.unbindService(serviceConnection)
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(CameraFragmentDirections.actionCameraToPermissions())
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()
    }

    override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        sendCommandToService(ACTION_START_OR_RESUME_SERVICE)

        objectDetectorHelper = ObjectDetectorHelper(
            context = requireContext(),
            objectDetectorListener = this)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Attach listeners to UI control widgets
        initBottomSheetControls()


        // get token from shared preferences
        try{
            val sharedPreference =  activity?.getSharedPreferences("myPref", Context.MODE_PRIVATE)
            ourToken = sharedPreference?.getString("token", null)
//            Toast.makeText(context, "you are logged in", Toast.LENGTH_SHORT).show()
            if (ourToken != null && ourToken!!.isNotEmpty())
            {
                fragmentCameraBinding.bottomSheetLayout.tokenLayout.visibility = View.GONE
                fragmentCameraBinding.bottomSheetLayout.button2.visibility = View.VISIBLE
            } else {
                fragmentCameraBinding.bottomSheetLayout.tokenLayout.visibility = View.VISIBLE
                fragmentCameraBinding.bottomSheetLayout.button2.visibility = View.GONE
            }
        }catch(e: Error){
            fragmentCameraBinding.bottomSheetLayout.tokenLayout.visibility = View.VISIBLE
            fragmentCameraBinding.bottomSheetLayout.button2.visibility = View.GONE
        }
    }

    private fun initBottomSheetControls() {
        // When clicked, lower detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (objectDetectorHelper.threshold >= 0.1) {
                objectDetectorHelper.threshold -= 0.05f
                updateControlsUi()
            }
        }

        // When clicked, raise detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (objectDetectorHelper.threshold <= 0.8) {
                objectDetectorHelper.threshold += 0.05f
                updateControlsUi()
            }
        }

        // When clicked, lower detection score threshold Pr floor
        fragmentCameraBinding.bottomSheetLayout.thresholdMinuss.setOnClickListener {
            if (objectDetectorHelper.thresholdPr >= 0.1) {
                objectDetectorHelper.thresholdPr -= 0.05f
                updateControlsUi()
            }
        }

        // When input text token chandged
//        fragmentCameraBinding.bottomSheetLayout.editTextToken.text = "hello";

        // When clicked, button set token
        fragmentCameraBinding.bottomSheetLayout.buttonToken.setOnClickListener {
            var newToken = fragmentCameraBinding.bottomSheetLayout.editTextToken.text.toString();
            val sharedPreference =  activity?.getSharedPreferences("myPref", Context.MODE_PRIVATE)
            val editor = sharedPreference!!.edit()
            editor.putString("token", newToken)
            ourToken = newToken
            editor.apply()
            Toast.makeText(context, "Token set to: $ourToken", Toast.LENGTH_LONG).show()
            // hide token layout and show bottom sheet layout
            fragmentCameraBinding.bottomSheetLayout.tokenLayout.visibility = View.GONE
            fragmentCameraBinding.bottomSheetLayout.button2.visibility = View.VISIBLE
            get_matricules()
        }
        // When clicked, delete button
        fragmentCameraBinding.bottomSheetLayout.button2.setOnClickListener {

            val sharedPreference =  activity?.getSharedPreferences("myPref", Context.MODE_PRIVATE)
            val editor = sharedPreference!!.edit()
            editor.putString("token", null)
            ourToken = null
            editor.apply()
            Toast.makeText(context, "Token set to: $ourToken", Toast.LENGTH_LONG).show()
            fragmentCameraBinding.bottomSheetLayout.tokenLayout.visibility = View.VISIBLE
            fragmentCameraBinding.bottomSheetLayout.button2.visibility = View.GONE
        }

        // When clicked, raise detection score threshold pr floor
        fragmentCameraBinding.bottomSheetLayout.thresholdPluss.setOnClickListener {
            if (objectDetectorHelper.thresholdPr <= 0.8)
            {
                objectDetectorHelper.thresholdPr += 0.05f
                updateControlsUi()
            }
        }

        // When clicked, reduce the number of objects that can be detected at a time
        fragmentCameraBinding.bottomSheetLayout.maxResultsMinus.setOnClickListener {
            if (objectDetectorHelper.maxResults > 1) {
                objectDetectorHelper.maxResults--
                updateControlsUi()
            }
        }

        // When clicked, increase the number of objects that can be detected at a time
        fragmentCameraBinding.bottomSheetLayout.maxResultsPlus.setOnClickListener {
            if (objectDetectorHelper.maxResults < 10) {
                objectDetectorHelper.maxResults++
                updateControlsUi()
            }
        }

        // When clicked, reduce the number of objects that can be detected at a time Pr
        fragmentCameraBinding.bottomSheetLayout.maxResultsMinuss.setOnClickListener {
            if (objectDetectorHelper.maxResultsPr > 1) {
                objectDetectorHelper.maxResultsPr--
                updateControlsUi()
            }
        }

        // When clicked, increase the number of objects that can be detected at a time Pr
        fragmentCameraBinding.bottomSheetLayout.maxResultsPluss.setOnClickListener {
            if (objectDetectorHelper.maxResultsPr < 12) {
                objectDetectorHelper.maxResultsPr++
                updateControlsUi()
            }
        }

        // When clicked, decrease the number of threads used for detection
        fragmentCameraBinding.bottomSheetLayout.threadsMinus.setOnClickListener {
            if (objectDetectorHelper.numThreads > 1) {
                objectDetectorHelper.numThreads--
                updateControlsUi()
            }
        }

        // When clicked, increase the number of threads used for detection
        fragmentCameraBinding.bottomSheetLayout.threadsPlus.setOnClickListener {
            if (objectDetectorHelper.numThreads < 4) {
                objectDetectorHelper.numThreads++
                updateControlsUi()
            }
        }

        // When clicked, change the underlying hardware used for inference. Current options are CPU
        // GPU, and NNAPI
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(0, false)
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    objectDetectorHelper.currentDelegate = p2
                    updateControlsUi()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }

        // When clicked, change the underlying model used for object detection
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.setSelection(0, false)
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    objectDetectorHelper.currentModel = p2
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }


    }

    // Update the values displayed in the bottom sheet. Reset detector.
    private fun updateControlsUi() {
        fragmentCameraBinding.bottomSheetLayout.maxResultsValue.text =
            objectDetectorHelper.maxResults.toString()
        fragmentCameraBinding.bottomSheetLayout.maxResultsValuePr.text = objectDetectorHelper.maxResultsPr.toString()
        fragmentCameraBinding.bottomSheetLayout.thresholdValue.text =
            String.format("%.2f", objectDetectorHelper.threshold)
        fragmentCameraBinding.bottomSheetLayout.labelSecondThreshold.text = String.format("%.2f", objectDetectorHelper.thresholdPr)
        fragmentCameraBinding.bottomSheetLayout.threadsValue.text =
            objectDetectorHelper.numThreads.toString()

        // Needs to be cleared instead of reinitialized because the GPU
        // delegate needs to be initialized on the thread using it when applicable
        objectDetectorHelper.clearObjectDetector()
        fragmentCameraBinding.overlay.clear()
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector - makes assumption that we're only using the back camera
        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview =
            Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        if (!::bitmapBuffer.isInitialized) {
                            // The image rotation and RGB image buffer are initialized only once
                            // the analyzer has started running
                            bitmapBuffer = Bitmap.createBitmap(
                              image.width,
                              image.height,
                              Bitmap.Config.ARGB_8888
                            )
                        }
                        // show the image on screen
                        println("image is : ${image.width} x ${image.height} ${image.imageInfo.rotationDegrees}")
                        detectObjects(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectObjects(image: ImageProxy) {
        // Copy out RGB bits to the shared bitmap buffer
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }
        val imageRotation = image.imageInfo.rotationDegrees

        // Pass Bitmap and rotation to the object detector helper for processing and detection
        objectDetectorHelper.detect(bitmapBuffer, imageRotation)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after objects have been detected. Extracts original image height/width
    // to scale and place bounding boxes properly through OverlayView
    override fun onResults(
      results: MutableList<Detection>?,
      inferenceTime: Long,

      imageHeight: Int,
      imageWidth: Int,
      finalBitmap: Bitmap
    ) {
        activity?.runOnUiThread {
            fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                            String.format("%d ms", inferenceTime)

            fragmentCameraBinding.bottomSheetLayout.registrationNumberVal.text = String.format(registrationNumber)

            // Pass necessary information to OverlayView for drawing on the canvas
            fragmentCameraBinding.overlay.setResults(
                results ?: LinkedList<Detection>(),
                imageHeight,
                imageWidth,
                finalBitmap
            )

            // Force a redraw
            fragmentCameraBinding.overlay.invalidate()
        }
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }
}


