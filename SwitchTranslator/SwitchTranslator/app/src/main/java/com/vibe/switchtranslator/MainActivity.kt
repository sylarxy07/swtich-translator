package com.example.switchtranslator

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var txtResult: TextView
    private lateinit var btnCapture: Button
    private lateinit var btnCopy: Button

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var api: TessBaseAPI

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        txtResult = findViewById(R.id.txtResult)
        btnCapture = findViewById(R.id.btnCapture)
        btnCopy = findViewById(R.id.btnCopy)

        cameraExecutor = Executors.newSingleThreadExecutor()

        initTesseract()
        checkPermission()

        btnCapture.setOnClickListener {
            captureOcrAndTranslate()
        }

        btnCopy.setOnClickListener {
            copyText(txtResult.text.toString())
        }
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureOcrAndTranslate() {
        val imageCapture = imageCapture ?: return

        val file = File(
            externalCacheDir,
            "capture_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions =
            ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults: ImageCapture.OutputFileResults
                ) {
                    runOnUiThread {
                        processImage(file)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            exception.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    private fun processImage(file: File) {
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)

            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                bitmap.height / 3,
                bitmap.width,
                bitmap.height / 3
            )

            cameraExecutor.execute {
                val rawText: String

                try {
                    api.setImage(croppedBitmap)
                    rawText = api.getUTF8Text()?.trim().orEmpty()
                } finally {
                    croppedBitmap.recycle()
                    bitmap.recycle()
                }

                runOnUiThread {
                    txtResult.text =
                        if (rawText.isEmpty()) "Text not found"
                        else rawText
                }
            }

        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun initTesseract() {
        val dataPath = File(filesDir, "tesseract/")
        val tessData = File(dataPath, "tessdata/")

        if (!tessData.exists()) {
            tessData.mkdirs()
        }

        api = TessBaseAPI()
        api.init(dataPath.absolutePath, "eng")
    }

    private fun copyText(text: String) {
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clip = ClipData.newPlainText("text", text)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        api.end()
    }
}