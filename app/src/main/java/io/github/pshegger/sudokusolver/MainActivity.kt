package io.github.pshegger.sudokusolver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 42
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var analyzer: SudokuAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)
        findViewById<View>(android.R.id.content).systemUiVisibility =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        if (allPermissionGranted()) {
            preview.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, CAMERA_PERMISSION_REQUEST)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        analyzer = SudokuAnalyzer(overlay)

        preview.preferredImplementationMode = PreviewView.ImplementationMode.TEXTURE_VIEW
        reset.setOnClickListener { analyzer.reset() }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (allPermissionGranted()) {
                preview.post { startCamera() }
            } else {
                finish()
            }
        }
    }

    private fun startCamera() {
        ProcessCameraProvider.getInstance(this).let { cameraProviderFuture ->
            cameraProviderFuture.addListener(Runnable {
                val cameraProvider = cameraProviderFuture.get()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(512, 512))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, analyzer)
                    }

                val previewUseCase = Preview.Builder()
                    .setTargetResolution(Size(1200, 1200))
                    .build()
                    .also { it.setSurfaceProvider(preview.createSurfaceProvider()) }

                val imageCapture = ImageCapture.Builder()
                    .setTargetResolution(Size(512, 512))
                    .build()

                capture.setOnClickListener { capture(imageCapture) }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    previewUseCase,
                    imageAnalysis,
                    imageCapture
                )
            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun getOutputFile(): File {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.apply {
            if (!exists()) {
                mkdir()
            }
        }
        return File(dir, "${System.currentTimeMillis()}.jpg")
    }

    private fun capture(imageCapture: ImageCapture) {
        val file = getOutputFile()
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(options, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                analyzer.addSolution(file) { outFile ->
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.provider", outFile)
                    val intent = Intent().apply {
                        action = Intent.ACTION_VIEW
                        setDataAndType(uri, "image/jpeg")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                }
            }

            override fun onError(exception: ImageCaptureException) {

            }
        })
    }

    private fun allPermissionGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}
