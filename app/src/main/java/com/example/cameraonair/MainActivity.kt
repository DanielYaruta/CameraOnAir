package com.example.cameraonair

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.Chronometer
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnRecord: Button
    private lateinit var btnFlipCamera: Button
    private lateinit var chronometer: Chronometer
    private lateinit var tvRecIndicator: TextView

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private val blinkHandler = Handler(Looper.getMainLooper())
    private val blinkRunnable = object : Runnable {
        override fun run() {
            tvRecIndicator.visibility =
                if (tvRecIndicator.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
            blinkHandler.postDelayed(this, 500)
        }
    }

    private val requiredPermissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (!results.all { it.value }) {
            Toast.makeText(this, "Разрешения необходимы для работы камеры", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        btnRecord = findViewById(R.id.btnRecord)
        btnFlipCamera = findViewById(R.id.btnFlipCamera)
        chronometer = findViewById(R.id.chronometer)
        tvRecIndicator = findViewById(R.id.tvRecIndicator)

        checkAndRequestPermissions()

        btnRecord.setOnClickListener {
            if (activeRecording != null) stopRecording() else startRecording()
        }

        btnFlipCamera.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }
    }

    private fun allPermissionsGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAndRequestPermissions() {
        val denied = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isEmpty()) return
        if (denied.any { shouldShowRequestPermissionRationale(it) }) {
            AlertDialog.Builder(this)
                .setTitle("Необходимы разрешения")
                .setMessage("Для записи видео приложению нужен доступ к камере и микрофону.")
                .setPositiveButton("Разрешить") { _, _ -> permissionLauncher.launch(denied.toTypedArray()) }
                .setNegativeButton("Отмена") { _, _ ->
                    Toast.makeText(this, "Разрешения необходимы для работы камеры", Toast.LENGTH_LONG).show()
                }
                .show()
        } else {
            permissionLauncher.launch(denied.toTypedArray())
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture!!)
            } catch (e: Exception) {
                Toast.makeText(this, "Не удалось запустить камеру: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val capture = videoCapture ?: return

        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "VID_$timestamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraOnAir")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        activeRecording = capture.output
            .prepareRecording(this, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        btnRecord.setText(R.string.stop_recording)
                        chronometer.base = SystemClock.elapsedRealtime()
                        chronometer.visibility = View.VISIBLE
                        chronometer.start()
                        blinkHandler.post(blinkRunnable)
                        btnFlipCamera.isEnabled = false
                    }
                    is VideoRecordEvent.Finalize -> {
                        activeRecording = null
                        btnRecord.setText(R.string.start_recording)
                        chronometer.stop()
                        chronometer.visibility = View.GONE
                        blinkHandler.removeCallbacks(blinkRunnable)
                        tvRecIndicator.visibility = View.GONE
                        btnFlipCamera.isEnabled = true
                        if (!event.hasError()) {
                            Toast.makeText(this, "Видео сохранено", Toast.LENGTH_SHORT).show()
                            openVideoPlayer(event.outputResults.outputUri)
                        } else {
                            Toast.makeText(this, "Ошибка записи: ${event.error}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {}
                }
            }
    }

    private fun stopRecording() {
        activeRecording?.stop()
    }

    private fun openVideoPlayer(uri: Uri) {
        val intent = Intent(this, VideoPlayerActivity::class.java).apply {
            putExtra(VideoPlayerActivity.EXTRA_VIDEO_URI, uri.toString())
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted() && activeRecording == null) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        blinkHandler.removeCallbacks(blinkRunnable)
    }
}
