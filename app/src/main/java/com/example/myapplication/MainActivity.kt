package com.example.myapplication

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity() {
    private var listening by mutableStateOf(false)
    private val confidenceThreshold = 0.1f
    private var detectedLabel by mutableStateOf("None")
    private var showAlert by mutableStateOf(false)
    private var alertLabel by mutableStateOf("")

    private val alertKeywords = listOf(
        "Siren", "Police car (siren)", "Fire alarm", "Smoke detector",
        "Baby cry, infant cry", "Doorbell", "Alarm", "Explosion", "Gunshot, gunfire", "Glass"
    )

    private val recentSounds = mutableStateListOf<String>()
    private lateinit var audioHelper: AudioClassificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // --- The ONLY line you need to change for testing ---
        audioHelper = MockAudioHelper(this) // or AudioHelper(this)
//        audioHelper = AudioHelper(this)

        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startListening(
                    onLabelDetected = { detectedLabel = it },
                    onStopListening = { listening = false }
                )
            } else {
                Toast.makeText(this, "Microphone permission is required for real device.", Toast.LENGTH_LONG).show()
            }
        }

        val startListeningAction = {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startListening(
                    onLabelDetected = { detectedLabel = it },
                    onStopListening = { listening = false }
                )
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        setContent {
            MyApplicationTheme {
                if (showAlert) {
                    AlertScreen(alertLabel = alertLabel, onClose = { showAlert = false })
                } else {
                    SoundSenseScreen(
                        detectedLabel = detectedLabel,
                        listening = listening,
                        recentSounds = recentSounds,
                        onStart = { startListeningAction() },
                        onStop = { stopListening() },
                        onPlayLastSound = { playLastSound() }
                    )
                }
            }
        }
    }

    private fun startListening(onLabelDetected: (String) -> Unit, onStopListening: () -> Unit) {
        listening = true
        Toast.makeText(this, "Listening started...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                while (listening) {
                    val results = audioHelper.classify()
                    if (results.isNotEmpty()) {
                        val topCategory = results[0].categories.maxByOrNull { it.score }
                        val topIndex = topCategory?.index ?: -1
                        val topScore = topCategory?.score ?: 0f
                        val topLabel = LabelMapHelper(this@MainActivity, "yamnet_class_map.csv").getLabel(topIndex)

                        val isSilence = topLabel.equals("Silence", ignoreCase = true)
                        if (!isSilence && topScore > confidenceThreshold) {
                            val time = SimpleDateFormat("HH:mm:ss, MMM d", Locale.US).format(Date())
                            val displayText = "$topLabel (${String.format(Locale.US, "%.2f", topScore)}) — $time"

                            withContext(Dispatchers.Main) {
                                if (recentSounds.size >= 5) recentSounds.removeAt(0)
                                recentSounds.add(displayText)
                                onLabelDetected("$topLabel (${String.format(Locale.US, "%.2f", topScore)})")
                            }

                            if (alertKeywords.any { topLabel.contains(it, ignoreCase = true) }) {
                                triggerAlert(topLabel)
                            }

                            @Suppress("DEPRECATION")
                            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            Log.d("SoundSense", "Detected $topLabel but below threshold or is silence.")
                        }
                    }
                    delay(1000)
                }
            } catch (_: CancellationException) {
                withContext(Dispatchers.Main) { onStopListening() }
            }
        }
    }

    private fun playLastSound() {
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setDataSource(audioHelper.lastRecordingFilePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
            Toast.makeText(this, "Playing last sound", Toast.LENGTH_SHORT).show()
            mediaPlayer.setOnCompletionListener { it.release() }
        } catch (e: IOException) {
            Log.e("SoundSense", "Could not play audio: ${e.message}")
            Toast.makeText(this, "Could not play audio.", Toast.LENGTH_SHORT).show()
            mediaPlayer.release()
        }
    }

    private fun triggerAlert(label: String) {
        alertLabel = label
        showAlert = true

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 300, 800), 0))
    }

    private fun stopListening() {
        listening = false
        Toast.makeText(this, "Listening stopped", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SoundSenseScreen(
    detectedLabel: String,
    listening: Boolean,
    recentSounds: List<String>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPlayLastSound: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Detected Sound:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(detectedLabel, style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.height(16.dp))

        Text("Recent Sounds:", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

        LazyColumn(modifier = Modifier.fillMaxWidth().height(150.dp).padding(8.dp)) {
            items(recentSounds.size) { index ->
                Text("• ${recentSounds[index]}")
            }
        }

        Spacer(Modifier.height(32.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onStart, enabled = !listening) { Text("Start Listening") }
                Button(onClick = onStop, enabled = listening, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Stop") }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onPlayLastSound, enabled = !listening && recentSounds.isNotEmpty()) {
                Text("Play Last Sound")
            }
        }
    }
}

@Composable
fun AlertScreen(alertLabel: String, onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Red),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠️ ALERT ⚠️", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text("Detected: $alertLabel", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                Text("Close Alert", color = Color.Red)
            }
        }
    }
}
