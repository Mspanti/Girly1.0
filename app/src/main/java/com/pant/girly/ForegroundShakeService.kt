package com.pant.girly

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.LinkedList

class ForegroundShakeService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var shakeTimestamp: Long = 0
    private var shakeCount = 0
    private lateinit var wakeLock: PowerManager.WakeLock

    private var tfliteInterpreter: Interpreter? = null
    private lateinit var inputBuffer: ByteBuffer
    private lateinit var outputBuffer: ByteBuffer

    private val INPUT_SIZE = 199
    private val OUTPUT_SIZE = 1
    private val MODEL_FILENAME = "motion_model.tflite"

    private val SHAKE_THRESHOLD = 30.0f // Increased threshold
    private val SHAKE_WINDOW_NS = 1000000000L // Reduced window
    private val SHAKE_COUNT_THRESHOLD = 5 // Increased count

    private val CHANNEL_ID = "ShakeServiceChannel"
    private val predictionWindow = LinkedList<Float>()
    private val PREDICTION_WINDOW_SIZE = 5 // Majority voting window size

    override fun onCreate() {
        super.onCreate()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Girly::ShakeWakeLock"
        ).apply { acquire(10 * 60 * 1000L) }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        loadTfliteModel()
        createNotificationChannel()
        startForegroundServiceWithNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Shake Detection Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun loadTfliteModel() {
        try {
            val modelFile = loadModelFile(this, MODEL_FILENAME)
            tfliteInterpreter = Interpreter(modelFile)

            inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * 4).apply {
                order(ByteOrder.nativeOrder())
            }
            outputBuffer = ByteBuffer.allocateDirect(OUTPUT_SIZE * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            tfliteInterpreter?.resizeInput(0, intArrayOf(1, INPUT_SIZE))
            // ✅ REMOVED resizeOutput (not needed or available)
        } catch (e: IOException) {
            Log.e("ShakeService", "Error loading TFLite model", e)
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(context: Context, modelFile: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun startForegroundServiceWithNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ForegroundShakeService::class.java).apply {
            action = "STOP_SHAKE_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shake Detection Service")
            .setContentText("Running...")
            .setSmallIcon(R.drawable.ic_sos)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_sos, "Stop Service", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Added priority
            .setOngoing(true) // Added ongoing
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SHAKE_SERVICE") {
            stopSelf()
        }
        return START_STICKY
    }

    private var lastX: Float = 0.0f
    private var lastY: Float = 0.0f
    private var lastZ: Float = 0.0f

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            val deltaX = kotlin.math.abs(lastX - x)
            val deltaY = kotlin.math.abs(lastY - y)
            val deltaZ = kotlin.math.abs(lastZ - z)

            if (deltaX + deltaY + deltaZ > SHAKE_THRESHOLD) {
                val currentTime = System.nanoTime()
                if (shakeTimestamp == 0L || (currentTime - shakeTimestamp > SHAKE_WINDOW_NS)) {
                    shakeCount = 1
                    shakeTimestamp = currentTime
                } else if (currentTime - shakeTimestamp <= SHAKE_WINDOW_NS) {
                    shakeCount++
                    if (shakeCount >= SHAKE_COUNT_THRESHOLD) {
                        val features = extractFeatures(event)
                        features?.let {
                            runTfliteInference(it)
                        }
                        shakeCount = 0
                        shakeTimestamp = currentTime + SHAKE_WINDOW_NS
                    }
                }
            }

            lastX = x
            lastY = y
            lastZ = z
        }
    }

    private fun extractFeatures(event: SensorEvent): FloatArray {
        val xValues = FloatArray(50)
        val yValues = FloatArray(50)
        val zValues = FloatArray(50)

        for (i in 0 until 50) {
            xValues[i] = event.values[0] + (Math.random() * 2 - 1).toFloat()
            yValues[i] = event.values[1] + (Math.random() * 2 - 1).toFloat()
            zValues[i] = event.values[2] + (Math.random() * 2 - 1).toFloat()
        }

        fun mean(arr: FloatArray): Float {
            var sum = 0.0f
            for (v in arr) sum += v
            return sum / arr.size
        }

        fun stdDev(arr: FloatArray, mean: Float): Float {
            var sum = 0.0f
            for (v in arr) sum += (v - mean) * (v - mean)
            return kotlin.math.sqrt(sum / (arr.size - 1))
        }

        fun max(arr: FloatArray): Float {
            var maxVal = Float.MIN_VALUE
            for (v in arr) if (v > maxVal) maxVal = v
            return maxVal
        }

        fun min(arr: FloatArray): Float {
            var minVal = Float.MAX_VALUE
            for (v in arr) if (v < minVal) minVal = v
            return minVal
        }

        val features = FloatArray(199)

        val xMean = mean(xValues)
        val xStdDev = stdDev(xValues, xMean)
        val xMax = max(xValues)
        val xMin = min(xValues)

        val yMean = mean(yValues)
        val yStdDev = stdDev(yValues, yMean)
        val yMax = max(yValues)
        val yMin = min(yValues)

        val zMean = mean(zValues)
        val zStdDev = stdDev(zValues, zMean)
        val zMax = max(zValues)
        val zMin = min(zValues)

        features[0] = xMean
        features[1] = xStdDev
        features[2] = xMax
        features[3] = xMin
        features[4] = yMean
        features[5] = yStdDev
        features[6] = yMax
        features[7] = yMin
        features[8] = zMean
        features[9] = zStdDev
        features[10] = zMax
        features[11] = zMin

        for (i in 12 until 199) {
            features[i] = (Math.random() * 2 - 1).toFloat()
        }

        return features
    }

    private fun runTfliteInference(features: FloatArray) {
        if (tfliteInterpreter == null) {
            Log.e("ShakeService", "TFLite interpreter is not initialized!")
            return
        }

        inputBuffer.rewind()
        outputBuffer.rewind()

        features.forEach { inputBuffer.putFloat(it) }

        tfliteInterpreter?.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()
        val output = outputBuffer.getFloat()

        Log.d("ShakeService", "TFLite Output: $output")

        predictionWindow.add(output)
        if (predictionWindow.size > PREDICTION_WINDOW_SIZE) {
            predictionWindow.removeFirst()
        }

        var sosCount = 0
        for (pred in predictionWindow) {
            if (pred > 0.6f) { // Increased threshold for prediction
                sosCount++
            }
        }

        if (sosCount > PREDICTION_WINDOW_SIZE / 2) {
            triggerSOS()
        } else {
            Log.d("ShakeService", "Motion not classified as SOS (majority vote)")
        }
    }

    private fun triggerSOS() {
        Log.w("ShakeService", "🚨 SOS EVENT DETECTED!")
        val sosIntent = Intent(this, SosTriggerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(sosIntent)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        tfliteInterpreter?.close()
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}