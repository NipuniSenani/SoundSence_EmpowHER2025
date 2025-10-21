package com.example.myapplication

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import org.tensorflow.lite.task.audio.classifier.Classifications
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

class MockAudioHelper(private val context: Context) : AudioClassificationHelper {

    private val classifier: AudioClassifier = AudioClassifier.createFromFile(context, "yamnet.tflite")

    private var mockSource = MockSource.NOISE

    // Enum to manage which mock source to use
    private enum class MockSource {
        NOISE,
        AUDIO_1,
        AUDIO_2
    }

    override val lastRecordingFilePath: String = File(context.cacheDir, "last_mock_recording.wav").absolutePath

    override fun classify(): List<Classifications> {
        Log.d("SoundSense", "--- Classifying with Mock Source: $mockSource ---")

        val audioBuffer = when (mockSource) {
            MockSource.NOISE -> createNoiseBuffer()
            MockSource.AUDIO_1 -> decodeAudioFile(R.raw.audio1)
            MockSource.AUDIO_2 -> decodeAudioFile(R.raw.audio2)
        }

        // Cycle to the next source for the next run
        mockSource = when (mockSource) {
            MockSource.NOISE -> MockSource.AUDIO_1
            MockSource.AUDIO_1 -> MockSource.AUDIO_2
            MockSource.AUDIO_2 -> MockSource.NOISE
        }

        if (audioBuffer == null) {
            Log.e("SoundSense", "Failed to create or decode audio buffer.")
            return emptyList()
        }

        // Now, proceed with the existing logic using the generated or decoded buffer
        try {
            val tensorAudio = classifier.createInputTensorAudio()
            tensorAudio.load(audioBuffer)

            saveRawAudioAsWav(audioBuffer, audioBuffer.size)

            val result = classifier.classify(tensorAudio)
            Log.d("SoundSense", "Mock classification result: $result")
            return result
        } catch (e: Exception) {
            Log.e("SoundSense", "Error during MOCK classification: ${e.message}")
            return emptyList()
        }
    }

    private fun createNoiseBuffer(): ShortArray {
        val tensorAudio = classifier.createInputTensorAudio()
        val bufferSize = tensorAudio.tensorBuffer.flatSize
        val mockAudioBuffer = ShortArray(bufferSize)
        for (i in mockAudioBuffer.indices) {
            mockAudioBuffer[i] = Random.nextInt(-32768, 32767).toShort()
        }
        Log.d("SoundSense", "Generated mock noise with ${mockAudioBuffer.size} samples.")
        return mockAudioBuffer
    }

    private fun decodeAudioFile(resourceId: Int): ShortArray? {
        val extractor = MediaExtractor()
        val afd = context.resources.openRawResourceFd(resourceId)
        extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()

        // Find the audio track and its format
        val format = extractor.getTrackFormat(0)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

        // Critical check for YAMNet requirements
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        if (sampleRate != 16000 || channelCount != 1) {
            Log.e("SoundSense", "Audio file at resource $resourceId is not 16kHz mono. Is ${sampleRate}Hz, $channelCount channel(s).")
            return null
        }

        extractor.selectTrack(0)

        // Set up the decoder
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val decodedData = mutableListOf<Short>()

        // Decoding loop
        while (true) {
            val inputBufferIndex = decoder.dequeueInputBuffer(10000)
            if (inputBufferIndex >= 0) {
                val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                if (sampleSize < 0) {
                    decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                } else {
                    decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }

            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufferIndex >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val shorts = ShortArray(shortBuffer.remaining())
                shortBuffer.get(shorts)
                decodedData.addAll(shorts.toList())

                decoder.releaseOutputBuffer(outputBufferIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()

        Log.d("SoundSense", "Decoded ${decodedData.size} samples from audio file.")
        return decodedData.toShortArray()
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
