package com.example.myapplication

import org.tensorflow.lite.task.audio.classifier.Classifications

/**
 * An interface that defines a common contract for both real and mock audio helpers.
 * This allows MainActivity to seamlessly switch between them for testing.
 */
interface AudioClassificationHelper {

    /**
     * The path to the last saved audio recording (real or mock).
     */
    val lastRecordingFilePath: String

    /**
     * Performs audio classification and returns the results.
     */
    fun classify(): List<Classifications>
}
