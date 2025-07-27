package com.pant.girly

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions
import com.google.firebase.ml.modeldownloader.DownloadType
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.concurrent.thread

class VoiceDetectionService : Service() {

    private val SAMPLE_RATE = 16000
    private val THRESHOLD = 0.85f
    private var interpreter: Interpreter? = null
    private var isListening = false
    private val CHANNEL_ID = "VoiceDetectionServiceChannel"
    private val NOTIFICATION_ID = 1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundService()
        downloadModelAndStartListening()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Voice Detection Service Channel"
            val descriptionText = "Channel for voice detection service notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        createNotificationChannel()

        val stopIntent = Intent(this, VoiceDetectionService::class.java).apply {
            action = "ACTION_STOP_SERVICE"
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Listening for SOS keyword")
            .setContentText("Say the trigger word to send an SOS.")
            .setSmallIcon(R.drawable.ic_mic) // Make sure this drawable exists
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent) // Make sure this icon exists
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    private fun downloadModelAndStartListening() {
        FirebaseModelDownloader.getInstance()
            .getModel("Voice-Trigger", DownloadType.LOCAL_MODEL, CustomModelDownloadConditions.Builder().build())
            .addOnSuccessListener { model ->
                model.file?.let {
                    interpreter = Interpreter(it)
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startListening()
                    } else {
                        Log.e("VoiceDetection", "Permission not granted")
                    }
                } ?: run {
                    loadModelFromAssets()
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && interpreter != null) {
                        startListening()
                    }
                }
            }
            .addOnFailureListener {
                it.printStackTrace()
                loadModelFromAssets()
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && interpreter != null) {
                    startListening()
                }
            }
    }

    private fun loadModelFromAssets() {
        try {
            val modelBuffer = loadModelFile()
            interpreter = Interpreter(modelBuffer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startListening() {
        if (isListening || interpreter == null) return
        isListening = true

        thread {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            val audioData = ShortArray(bufferSize)
            recorder.startRecording()

            while (isListening) {
                recorder.read(audioData, 0, bufferSize)
                val mfcc = AudioUtils.extractMFCC(audioData, SAMPLE_RATE)
                val confidence = predict(mfcc)

                if (confidence > THRESHOLD) {
                    val intent = Intent(this@VoiceDetectionService, SosTriggerActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    break
                }

                Thread.sleep(1000)
            }

            recorder.stop()
            recorder.release()
        }
    }

    private fun predict(input: FloatArray): Float {
        val inputBuffer = ByteBuffer.allocateDirect(4 * input.size).order(ByteOrder.nativeOrder())
        input.forEach { inputBuffer.putFloat(it) }
        val output = Array(1) { FloatArray(1) }
        interpreter?.run(inputBuffer, output)
        return output[0][0]
    }

    override fun onDestroy() {
        isListening = false
        interpreter?.close()
        super.onDestroy()
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = assets.openFd("voice_sos_model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
