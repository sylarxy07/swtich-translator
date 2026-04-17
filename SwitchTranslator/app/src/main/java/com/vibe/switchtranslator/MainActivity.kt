package com.vibe.switchtranslator

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nltranslate.TranslateLanguage
import com.google.mlkit.nltranslate.Translation
import com.google.mlkit.nltranslate.Translator
import com.google.mlkit.nltranslate.TranslatorOptions
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "SwitchTranslator"

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var textOriginal: TextView
    private lateinit var textTranslated: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnStart: Button

    private var tessBaseAPI: TessBaseAPI? = null
    private var translator: Translator? = null
    private lateinit var dataPath: String
    private var translationReady = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, R.string.camera_permission_rationale, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        textOriginal = findViewById(R.id.text_original)
        textTranslated = findViewById(R.id.text_translated)
        progress = findViewById(R.id.progress)
        btnStart = findViewById(R.id.btnStart)

        viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER
        cameraExecutor = Executors.newSingleThreadExecutor()

        dataPath = filesDir.absolutePath + "/tesseract/"
        prepareTesseract()
        prepareTranslator()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        btnStart.setOnClickListener {
            captureOcrAndTranslate()
        }

        Toast.makeText(this, R.string.model_download_hint, Toast.LENGTH_LONG).show()
    }

    private fun prepareTranslator() {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.TURKISH)
            .build()
        val client = Translation.getClient(options)
        translator = client

        progress.visibility = View.VISIBLE
        btnStart.isEnabled = false
        val conditions = DownloadConditions.Builder().build()
        client.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translationReady = true
                progress.visibility = View.GONE
                btnStart.isEnabled = true
                Toast.makeText(this, R.string.status_ready, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e: Exception ->
                Log.e(TAG, "Model download failed", e)
                progress.visibility = View.GONE
                btnStart.isEnabled = true
                Toast.makeText(this, R.string.translation_failed, Toast.LENGTH_LONG).show()
            }
    }

    private fun prepareTesseract() {
        val tessDataDir = File(dataPath + "tessdata/")
        if (!tessDataDir.exists()) tessDataDir.mkdirs()

        val trainedDataFile = File(tessDataDir, "eng.traineddata")
        if (!trainedDataFile.exists()) {
            try {
                assets.open("tessdata/eng.traineddata").use { input ->
                    FileOutputStream(trainedDataFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Asset copy failed", e)
            }
        }

        val api = TessBaseAPI()
        try {
            if (!api.init(dataPath, "eng")) {
                api.end()
                Toast.makeText(this, R.string.tesseract_init_failed, Toast.LENGTH_LONG).show()
                return
            }
            tessBaseAPI = api
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract init failed", e)
            try {
                api.end()
            } catch (_: Exception) { }
            Toast.makeText(this, R.string.tesseract_init_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            } catch (e: Exception) {
                Log.e(TAG, "Camera failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureOcrAndTranslate() {
        val api = tessBaseAPI ?: run {
            Toast.makeText(this, R.string.tesseract_init_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val tr = translator ?: run {
            Toast.makeText(this, R.string.translation_failed, Toast.LENGTH_SHORT).show()
            return
        }
        if (!translationReady) {
            Toast.makeText(this, R.string.model_download_hint, Toast.LENGTH_LONG).show()
            return
        }

        val fullBitmap = viewFinder.bitmap ?: run {
            Toast.makeText(this, R.string.ocr_no_text, Toast.LENGTH_SHORT).show()
            return
        }
        val overlayBox = findViewById<View>(R.id.overlay_box)

        val cropRect: Rect = try {
            mapOverlayRectToBitmapRect(viewFinder, overlayBox, fullBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Crop rect failed", e)
            return
        }

        val croppedBitmap: Bitmap = try {
            Bitmap.createBitmap(
                fullBitmap,
                cropRect.left,
                cropRect.top,
                cropRect.width(),
                cropRect.height()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Crop bitmap failed", e)
            return
        }

        progress.visibility = View.VISIBLE
        btnStart.isEnabled = false
        textOriginal.text = getString(R.string.status_processing)
        textTranslated.text = ""

        cameraExecutor.execute {
            val rawText: String
            try {
                api.setImage(croppedBitmap)
                rawText = api.utF8Text?.trim().orEmpty()
            } finally {
                croppedBitmap.recycle()
            }

            runOnUiThread {
                if (rawText.isBlank()) {
                    progress.visibility = View.GONE
                    btnStart.isEnabled = true
                    textOriginal.text = ""
                    textTranslated.text = ""
                    Toast.makeText(this, R.string.ocr_no_text, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                textOriginal.text = rawText

                tr.translate(rawText)
                    .addOnSuccessListener { translated: String ->
                        textTranslated.text = translated
                        progress.visibility = View.GONE
                        btnStart.isEnabled = true
                    }
                    .addOnFailureListener { e: Exception ->
                        Log.e(TAG, "Translate failed", e)
                        textTranslated.text = getString(R.string.translation_failed)
                        progress.visibility = View.GONE
                        btnStart.isEnabled = true
                    }
            }
        }
    }

    private fun mapOverlayRectToBitmapRect(
        previewView: PreviewView,
        overlay: View,
        bitmap: Bitmap
    ): Rect {
        val pvLoc = IntArray(2)
        val ovLoc = IntArray(2)
        previewView.getLocationInWindow(pvLoc)
        overlay.getLocationInWindow(ovLoc)

        val relLeft = (ovLoc[0] - pvLoc[0]).toFloat()
        val relTop = (ovLoc[1] - pvLoc[1]).toFloat()
        val relRight = relLeft + overlay.width
        val relBottom = relTop + overlay.height

        val vw = previewView.width.toFloat()
        val vh = previewView.height.toFloat()
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()

        val scale = min(vw / bw, vh / bh)
        val drawnW = bw * scale
        val drawnH = bh * scale
        val offsetX = (vw - drawnW) / 2f
        val offsetY = (vh - drawnH) / 2f

        fun bx(x: Float) = ((x - offsetX) / scale).roundToInt()
        fun by(y: Float) = ((y - offsetY) / scale).roundToInt()

        val left = bx(relLeft).coerceIn(0, bitmap.width - 1)
        val top = by(relTop).coerceIn(0, bitmap.height - 1)
        val right = bx(relRight).coerceIn(left + 1, bitmap.width)
        val bottom = by(relBottom).coerceIn(top + 1, bitmap.height)
        return Rect(left, top, right, bottom)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tessBaseAPI?.end()
        tessBaseAPI = null
        translator?.close()
        translator = null
    }
}
