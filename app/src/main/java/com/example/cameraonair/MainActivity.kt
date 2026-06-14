package com.example.cameraonair

import android.Manifest
import android.animation.ObjectAnimator
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnRecord: Button
    private lateinit var btnFlipCamera: Button
    private lateinit var btnComposeDemo: Button
    private lateinit var chronometer: Chronometer
    private lateinit var tvRecIndicator: TextView

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isCameraStarted = false

    private val blinkAnimator by lazy {
        ObjectAnimator.ofFloat(tvRecIndicator, "alpha", 1f, 0f).apply {
            duration = 500
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
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
        btnComposeDemo = findViewById(R.id.btnComposeDemo)
        chronometer = findViewById(R.id.chronometer)
        tvRecIndicator = findViewById(R.id.tvRecIndicator)

        btnRecord.isEnabled = false

        btnComposeDemo.setOnClickListener {
            startActivity(Intent(this, ComposeActivity::class.java))
        }

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
            isCameraStarted = false
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
                .setQualitySelector(
                    QualitySelector.fromOrderedList(
                        listOf(Quality.FHD, Quality.HD, Quality.SD, Quality.LOWEST),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.LOWEST)
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture!!)
                isCameraStarted = true
                btnRecord.isEnabled = true
            } catch (e: Exception) {
                btnRecord.isEnabled = false
                Toast.makeText(this, "Не удалось запустить камеру: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val capture = videoCapture ?: return

        val timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))

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
                        tvRecIndicator.visibility = View.VISIBLE
                        blinkAnimator.start()
                        btnFlipCamera.isEnabled = false
                    }
                    is VideoRecordEvent.Finalize -> {
                        activeRecording = null
                        btnRecord.setText(R.string.start_recording)
                        chronometer.stop()
                        chronometer.visibility = View.GONE
                        blinkAnimator.cancel()
                        tvRecIndicator.visibility = View.GONE
                        btnFlipCamera.isEnabled = true
                        if (!event.hasError()) {
                            Toast.makeText(this, "Видео сохранено", Toast.LENGTH_SHORT).show()
                            openVideoPlayer(event.outputResults.outputUri)
                        } else {
                            val msg = when (event.error) {
                                VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA ->
                                    "Нет данных для записи — камера не успела инициализироваться"
                                VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE ->
                                    "Недостаточно места на устройстве"
                                VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED ->
                                    "Достигнут максимальный размер файла"
                                VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE ->
                                    "Камера была отключена во время записи"
                                VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED ->
                                    "Ошибка кодирования видео"
                                else -> "Ошибка записи (код: ${event.error})"
                            }
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
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
            putExtra(VideoPlayerActivity.EXTRA_VIDEO_URI, uri)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted() && !isCameraStarted) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        blinkAnimator.cancel()
    }
}
