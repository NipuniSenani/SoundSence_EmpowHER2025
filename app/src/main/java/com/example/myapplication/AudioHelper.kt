package com.example.myapplication

import android.content.Context
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import org.tensorflow.lite.task.audio.classifier.Classifications
import android.util.Log


class AudioHelper(context: Context) {

    private val classifier: AudioClassifier =
        AudioClassifier.createFromFile(context, "yamnet.tflite")

    // Use this for 1-second audio capture
    fun classify(): List<Classifications> {
        val audioRecord = classifier.createAudioRecord()
        val tensorAudio = classifier.createInputTensorAudio()


        audioRecord.startRecording()

        // Record 1 second of audio
        Thread.sleep(1000)

        // --- Silence detection ---
        val buffer = ShortArray(16000)
        val read = audioRecord.read(buffer, 0, buffer.size)
        var sumSquares = 0.0
        for (i in 0 until read) {
            val normalized = buffer[i] / 32768.0
            sumSquares += normalized * normalized
        }
        val rms = Math.sqrt(sumSquares / read)
        if (rms < 0.01) {
            Log.d("SoundSense", "Silence detected, skipping classification")
            audioRecord.stop()
            audioRecord.release()
            return emptyList()
        }


        // --- Classification ---
        // ✅ Load recorded audio into TensorAudio
        tensorAudio.load(audioRecord)

        val result = classifier.classify(tensorAudio) // captures 1 second
        audioRecord.stop()

        audioRecord.stop()
        audioRecord.release()

        return result
    }
}