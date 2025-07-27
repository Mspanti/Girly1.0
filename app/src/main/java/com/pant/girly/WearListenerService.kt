package com.pant.girly

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions
import com.google.firebase.ml.modeldownloader.DownloadType
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.channels.FileChannel

class WearListenerService : WearableListenerService() {

    private var interpreter: Interpreter? = null
    private val MODEL_NAME = "heart_rate_panic_model_sos.tflite"

    companion object {
        var lastHeartRate: Float = 0f
        var lastMotionLevel: Float = 0f

        fun getSensorData(): Pair<Float, Float> {
            return Pair(lastHeartRate, lastMotionLevel)
        }

        fun predict(context: android.content.Context, heartRate: Float, motionLevel: Float) {
            // You can move prediction logic here if needed.
        }
    }

    override fun onCreate() {
        super.onCreate()
        loadModel()
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = assets.openFd(MODEL_NAME)
            val fileInputStream = assetFileDescriptor.createInputStream()
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            interpreter = Interpreter(fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength))
        } catch (e: Exception) {
            Log.e("WearListenerService", "Local model load failed: ${e.message}")
        }

        val conditions = CustomModelDownloadConditions.Builder().build()
        FirebaseModelDownloader.getInstance()
            .getModel("Heart-rate-Trigger", DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND, conditions)
            .addOnSuccessListener { model ->
                val modelFile: File? = model.file
                modelFile?.let {
                    interpreter = Interpreter(it)
                    Log.d("WearListenerService", "Model loaded from Firebase")
                }
            }
            .addOnFailureListener {
                Log.e("WearListenerService", "Firebase model download failed: ${it.message}")
            }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

                when (path) {
                    "/sos_trigger" -> {
                        val intent = Intent(this, SosTriggerActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }

                    "/sensor_data" -> {
                        val heartRate = dataMap.getFloat("heart_rate", 0f)
                        val motionValue = dataMap.getFloat("motion_value", 0f)
                        lastHeartRate = heartRate
                        lastMotionLevel = motionValue

                        predictAndTriggerSos(heartRate, motionValue)
                    }
                }
            }
        }
    }

    private fun predictAndTriggerSos(heartRate: Float, motionLevel: Float) {
        if (interpreter == null) return
        val input = arrayOf(floatArrayOf(heartRate, motionLevel))
        val output = Array(1) { FloatArray(1) }
        interpreter?.run(input, output)

        if (output[0][0] > 0.5f) {
            val sosIntent = Intent(this, SosTriggerActivity::class.java)
            sosIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            sosIntent.putExtra("source", "prediction")
            startActivity(sosIntent)
        }
    }
}
