package com.example.myapplication

import android.content.Context
import android.util.Log
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import org.tensorflow.lite.task.audio.classifier.Classifications
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/**
 * A mock version of AudioHelper for testing on the emulator.
 * Instead of using the microphone, it generates random noise to simulate audio input.
 */
class MockAudioHelper(context: Context) : AudioClassificationHelper {

    private val classifier: AudioClassifier = AudioClassifier.createFromFile(context, "yamnet.tflite")

    override val lastRecordingFilePath: String = File(context.cacheDir, "last_mock_recording.wav").absolutePath

    override fun classify(): List<Classifications> {
        Log.d("SoundSense", "--- Using Mock Audio for Classification ---")
        try {
            val tensorAudio = classifier.createInputTensorAudio()
            val mockAudioBuffer = ShortArray(tensorAudio.tensorBuffer.flatSize)

            // Generate random noise to simulate a non-silent audio signal
            for (i in mockAudioBuffer.indices) {
                mockAudioBuffer[i] = Random.nextInt(-32768, 32767).toShort()
            }

            Log.d("SoundSense", "Generated mock audio with ${mockAudioBuffer.size} samples.")

            // Save the mock audio so playback can be tested
            saveRawAudioAsWav(mockAudioBuffer, mockAudioBuffer.size)

            // Load the mock audio for classification
            tensorAudio.load(mockAudioBuffer)

            val result = classifier.classify(tensorAudio)
            Log.d("SoundSense", "Mock classification result: $result")
            return result

        } catch (e: Exception) {
            Log.e("SoundSense", "Error during MOCK classification: ${e.message}")
            return emptyList()
        }
    }

    private fun saveRawAudioAsWav(audioData: ShortArray, samplesRead: Int) {
        val sampleRate = 16000
        val audioDataSize = samplesRead * 2

        try {
            val outputStream = FileOutputStream(lastRecordingFilePath)
            writeWavHeader(outputStream, audioDataSize.toLong(), sampleRate.toLong())

            val byteBuffer = ByteBuffer.allocate(audioDataSize)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until samplesRead) {
                byteBuffer.putShort(audioData[i])
            }

            outputStream.write(byteBuffer.array())
            outputStream.close()
        } catch (e: IOException) {
            Log.e("SoundSense", "Error saving WAV file: ${e.message}")
        }
    }

    @Throws(IOException::class)
    private fun writeWavHeader(out: FileOutputStream, totalAudioLen: Long, longSampleRate: Long) {
        val channels = 1
        val totalDataLen = totalAudioLen + 36
        val byteRate = longSampleRate * channels * 2

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 2).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        out.write(header, 0, 44)
    }
}
