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

import android.content.Context
import android.graphics.*
import android.os.Environment
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import org.tensorflow.lite.examples.objectdetection.fragments.registrationNumber
import java.util.LinkedList
import kotlin.math.max
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.File
import java.io.FileOutputStream
import kotlin.math.absoluteValue

class OverlayView(context: Context?, attrs: AttributeSet?
) : View(context, attrs) {

    private var results: List<Detection> = LinkedList<Detection>()

    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()


    private var scaleFactor: Float = 1f

    private var bounds = Rect()

    init {
        initPaints()
    }

    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        for (result in results) {
            val boundingBox = result.boundingBox

            val top = boundingBox.top * scaleFactor
            val bottom = boundingBox.bottom * scaleFactor
            val left = boundingBox.left * scaleFactor
            val right = boundingBox.right * scaleFactor
           

            println("top: $top, bottom: $bottom, left: $left, right: $right")


            // Draw bounding box around detected objects
            val drawableRect = RectF(left, top, right, bottom)
            canvas.drawRect(drawableRect, boxPaint)

            boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)

            // Create text to display alongside detected objects
            var splitemtr = registrationNumber.split("S")

            var nmtr = ""

            try {
                if (splitemtr.size == 3) {
                    nmtr = " [" + splitemtr[0] + " | " + splitemtr[1] + " | " + splitemtr[2].replace(" ✔", "") + "]"
                }
                } catch (e: ArithmeticException)
            {

            }

            val drawableText =
                result.categories[0].label + " " +
                        String.format("%.2f", result.categories[0].score) + nmtr

            // Draw rect behind display text
            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()
            canvas.drawRect(
                left,
                top,
                left + textWidth + Companion.BOUNDING_RECT_TEXT_PADDING,
                top + textHeight + Companion.BOUNDING_RECT_TEXT_PADDING,
                textBackgroundPaint

            )

            // change color
            if (registrationNumber.contains("✔"))
            {
                textPaint.color = ContextCompat.getColor(context!!, R.color.green)
            } else {
                textPaint.color = ContextCompat.getColor(context!!, R.color.cardview_light_background)
            }


            // Draw text for detected object
            canvas.drawText(drawableText, left, top + bounds.height(), textPaint)
        }
    }

    fun setResults(
      detectionResults: MutableList<Detection>,
      imageHeight: Int,
      imageWidth: Int,
      finalBitmap: Bitmap,
    ) {
        results = detectionResults

        //
        // PreviewView is in FILL_START mode. So we need to scale up the bounding box to match with
        // the size that the captured images will be displayed.
        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)

        for (result in results) {
            val boundingBox = result.boundingBox
            

            
            // scale the finalBitmap to match with imageHeight and imageWidth
            val scaledBitmap = Bitmap.createScaledBitmap(finalBitmap, imageHeight, imageWidth, true)
            
            val rotatedBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.width, scaledBitmap.height, Matrix().apply { postRotate(90f) }, true)

            
            val root = Environment.getExternalStorageDirectory().toString()
            val myDir = File("$root/captured_images")
            myDir.mkdirs()
            val fname = "Image-${System.currentTimeMillis()}.jpg"
            val file = File(myDir, fname)
            if (file.exists()) file.delete()
            try {
                val croppedBitmap = Bitmap.createBitmap(
                    rotatedBitmap,
                    boundingBox.left.toInt(),
                    boundingBox.top.toInt(),
                    boundingBox.width().toInt(),
                    boundingBox.height().toInt()
                    )
                try {
                    val out = FileOutputStream(file)
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                    out.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}
