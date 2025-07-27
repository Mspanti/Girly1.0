package com.pant.girly

import kotlin.math.log10

object AudioUtils {
    fun extractMFCC(audioData: ShortArray, sampleRate: Int): FloatArray {
        // NOTE: This is a simplified placeholder – use a native MFCC lib like TarsosDSP for real use.
        // For now, return a dummy 40-feature vector
        val mfcc = FloatArray(40)
        for (i in mfcc.indices) {
            mfcc[i] = log10(audioData.getOrNull(i * 10)?.toFloat()?.plus(1) ?: 1f)
        }
        return mfcc
    }
}
