package com.pant.girly

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LiveVideoActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnStartLocalRecord: Button
    private lateinit var btnStopLocalRecord: Button
    private lateinit var btnCaptureSnapshot: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnStartStreaming: Button
    private lateinit var btnStopStreaming: Button
    private lateinit var switchAudio: Switch
    private lateinit var switchVideo: Switch

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    private var currentRecording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService

    private val TAG = "LiveVideoActivity"

    private val storage = FirebaseStorage.getInstance()
    private val storageRef = storage.reference

    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var granted = true
        permissions.entries.forEach {
            if (!it.value) granted = false
        }
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_video)

        previewView = findViewById(R.id.previewView)
        btnStartLocalRecord = findViewById(R.id.btnStartLocalRecord)
        btnStopLocalRecord = findViewById(R.id.btnStopLocalRecord)
        btnCaptureSnapshot = findViewById(R.id.btnCaptureSnapshot)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnStartStreaming = findViewById(R.id.btnStartStreaming)
        btnStopStreaming = findViewById(R.id.btnStopStreaming)
        switchAudio = findViewById(R.id.switchAudio)
        switchVideo = findViewById(R.id.switchVideo)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(permissions)
        }

        btnSwitchCamera.setOnClickListener {
            toggleCamera()
        }

        btnCaptureSnapshot.setOnClickListener {
            takePhoto()
        }

        btnStartLocalRecord.setOnClickListener {
            startRecording()
        }

        btnStopLocalRecord.setOnClickListener {
            stopRecording()
        }

        btnStartStreaming.setOnClickListener {
            startRtmpStream()
        }

        btnStopStreaming.setOnClickListener {
            stopRtmpStream()
        }
    }

    private fun allPermissionsGranted() = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
            CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        startCamera()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = createFile("IMG_", ".jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    Toast.makeText(this@LiveVideoActivity, "Photo captured: $savedUri", Toast.LENGTH_SHORT).show()
                    uploadToFirebase(photoFile, "images/${photoFile.name}")
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@LiveVideoActivity, "Photo capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Photo capture failed", exception)
                }
            }
        )
    }

    private fun createFile(prefix: String, extension: String): File {
        val dir = externalCacheDir ?: filesDir
        return File.createTempFile(prefix + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()), extension, dir)
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val videoCapture = this.videoCapture ?: return

        btnStartLocalRecord.isEnabled = false
        btnStopLocalRecord.visibility = Button.VISIBLE

        val videoFile = createFile("VID_", ".mp4")

        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        currentRecording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .apply {
                if (switchAudio.isChecked) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            Toast.makeText(this, "Recording saved: ${videoFile.absolutePath}", Toast.LENGTH_SHORT).show()
                            uploadToFirebase(videoFile, "videos/${videoFile.name}")
                        } else {
                            Log.e(TAG, "Recording error: ${recordEvent.error}")
                        }
                        btnStartLocalRecord.isEnabled = true
                        btnStopLocalRecord.visibility = Button.GONE
                    }
                }
            }
    }

    private fun stopRecording() {
        currentRecording?.stop()
        currentRecording = null
    }

    private fun uploadToFirebase(file: File, path: String) {
        val fileUri = Uri.fromFile(file)
        val ref = storageRef.child(path)

        ref.putFile(fileUri)
            .addOnSuccessListener {
                Toast.makeText(this, "Uploaded: $path", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Dummy RTMP streaming methods with try-catch to keep your app safe
    private fun startRtmpStream() {
        try {
            // Your RTMP streaming initialization here (e.g., using a streaming SDK)
            // For now, just simulate success:
            Toast.makeText(this, "RTMP Streaming started", Toast.LENGTH_SHORT).show()
            btnStartStreaming.visibility = Button.GONE
            btnStopStreaming.visibility = Button.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(this, "RTMP Stream failed: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "RTMP Stream start error", e)
        }
    }

    private fun stopRtmpStream() {
        try {
            // Stop your RTMP streaming here
            Toast.makeText(this, "RTMP Streaming stopped", Toast.LENGTH_SHORT).show()
            btnStartStreaming.visibility = Button.VISIBLE
            btnStopStreaming.visibility = Button.GONE
        } catch (e: Exception) {
            Toast.makeText(this, "RTMP Stream stop error: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "RTMP Stream stop error", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
