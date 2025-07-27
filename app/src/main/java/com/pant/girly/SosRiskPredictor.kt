package com.pant.girly

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class SosRiskPredictor(context: Context) {
    private val interpreter: Interpreter = Interpreter(loadModelFile(context))

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("crime_alert_model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun predictRisk(
        latitude: Float,
        longitude: Float,
        timeOfDay: Float,
        incidents: Float,
        pastSos: Float
    ): Float {
        val input = arrayOf(floatArrayOf(latitude, longitude, timeOfDay, incidents, pastSos))
        val output = Array(1) { FloatArray(1) }
        interpreter.run(input, output)
        return output[0][0]
    }
}
