package com.example.recyclerextreme

import android.Manifest
import android.graphics.Matrix
import java.util.concurrent.TimeUnit
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.util.Rational
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.app.ActivityCompat

import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions

private val REQUEST_CODE_PERMISSIONS = 42

private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)



class CameraActivity : AppCompatActivity(), LifecycleOwner {

    private lateinit var viewFinder: TextureView
    private var rotation = 0
    private lateinit var label: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        viewFinder = findViewById(R.id.view_finder)

        if (allPermissionGranted()) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }
        label = findViewById(R.id.label)

    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionGranted()) {
                viewFinder.post { startCamera() }
            } else {
                Toast.makeText(
                    this, "Permissions not granted by user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()

            }
        }
    }

    private fun allPermissionGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    private fun updateTransform() {

        val matrix = Matrix()

        //Find the center

        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        //get correct rotation

        rotation = when (viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }

        matrix.postRotate(-rotation.toFloat(), centerX, centerY)

        viewFinder.setTransform(matrix)

    }

    private fun startCamera() {

        val previewConfig = PreviewConfig.Builder().apply {
            setTargetAspectRatio(Rational(1, 1))
        }.build()

        val preview = Preview(previewConfig)

        preview.setOnPreviewOutputUpdateListener {
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            viewFinder.surfaceTexture = it.surfaceTexture
            parent.addView(viewFinder, 0)
            updateTransform()
        }

        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            val analyzerThread = HandlerThread(
                "LabelAnalysis"
            ).apply { start() }
            setCallbackHandler(Handler(analyzerThread.looper))

            setImageReaderMode(
                ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE
            )
        }.build()

        val analyzerUseCase = ImageAnalysis(analyzerConfig).apply {
            analyzer = LabelAnalyzer(label)
        }

        CameraX.bindToLifecycle(this, preview, analyzerUseCase)
    }

    private class LabelAnalyzer(val textView: TextView) : ImageAnalysis.Analyzer {

        private var lastAnalyzedTimestamp = 0L


        override fun analyze(image: ImageProxy, rotationDegrees: Int) {

            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastAnalyzedTimestamp >=
                TimeUnit.SECONDS.toMillis(1)
            ) {
                lastAnalyzedTimestamp = currentTimestamp
            }

            val y = image.planes[0]
            val u = image.planes[1]
            val v = image.planes[2]

            val Yb = y.buffer.remaining()
            val Ub = u.buffer.remaining()
            val Vb = v.buffer.remaining()

            val data = ByteArray(Yb + Ub + Vb)

            y.buffer.get(data, 0, Yb)
            u.buffer.get(data, Yb, Ub)
            v.buffer.get(data, Yb + Ub, Vb)


            val metadata = FirebaseVisionImageMetadata.Builder()
                .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_YV12)
                .setHeight(image.height)
                .setWidth(image.width)
                .setRotation(getRotation(rotationDegrees))
                .build()

            val labelImage = FirebaseVisionImage.fromByteArray(data, metadata)

            val labeler = FirebaseVision.getInstance().getOnDeviceImageLabeler()
            labeler.processImage(labelImage)
                .addOnSuccessListener { labels ->
                    textView.run {
                        if (labels.size >= 1) {
                            text = labels[0].text + " " + labels[0].confidence
                        }
                    }
                }
        }
        private fun getRotation(rotationCompensation: Int): Int {
            return when(rotationCompensation) {
                0 -> FirebaseVisionImageMetadata.ROTATION_0
                90 -> FirebaseVisionImageMetadata.ROTATION_90
                180 -> FirebaseVisionImageMetadata.ROTATION_180
                270 -> FirebaseVisionImageMetadata.ROTATION_270
                else -> {
                    FirebaseVisionImageMetadata.ROTATION_0
                }
            }
        }
    }
}





