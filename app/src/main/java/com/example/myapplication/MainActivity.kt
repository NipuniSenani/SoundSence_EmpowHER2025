package com.example.myapplication

import android.Manifest
import android.content.Context
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity() {
    // 🔹 Placeholder for when we start listening / integrating AudioClassifier
    private var listening by mutableStateOf(false)
    private val confidenceThreshold = 0.5f
    private var detectedLabel by mutableStateOf("None") // for Compose UI
    private var showAlert by mutableStateOf(false)
    private var alertLabel by mutableStateOf("")

    private val alertKeywords = listOf(
        "Siren",
        "Police car (siren)",
        "Fire alarm",
        "Smoke detector",
        "Baby cry, infant cry",
        "Doorbell",
        "Alarm",
        "Explosion",
        "Gunshot, gunfire",
        "Glass"
    )

    private val recentSounds = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startListeningAction = {
            startListening(
                onLabelDetected = { detectedLabel = it },
                onStopListening = { listening = false }
            )
        }

        // 🔹 Modern permission launcher
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    startListeningAction()
                } else {
                    Toast.makeText(
                        this,
                        "Microphone permission is required to detect sounds.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        // 🔹 Check if permission is already granted
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startListeningAction()
        } else {
            // 🔹 Request permission
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        // 🔹 Compose UI
        setContent {
            MyApplicationTheme {
                if (showAlert) {
                    AlertScreen(
                        alertLabel = alertLabel,
                        onClose = { showAlert = false }
                    )
                } else {
                    SoundSenseScreen(
                        detectedLabel = detectedLabel,
                        listening = listening,
                        recentSounds = recentSounds,
                        onStart = { startListeningAction() },
                        onStop = { stopListening() }
                    )
                }
            }
        }

    }




    private fun startListening(
        onLabelDetected: (String) -> Unit,
        onStopListening: () -> Unit
    ) {
        val audioHelper = AudioHelper(this)
        val labelMap = LabelMapHelper(this, "yamnet_class_map.csv")
        listening = true
        Toast.makeText(this, "Listening started...", Toast.LENGTH_SHORT).show()

        // Launch background coroutine
        lifecycleScope.launch(Dispatchers.IO) {
            try{
                while (listening) {
                    val results = audioHelper.classify() // 1s capture
                    if (results.isNotEmpty()) {
                        val topCategory = results[0].categories.maxByOrNull { it.score }
                        val topIndex = topCategory?.index ?: -1
                        val topScore = topCategory?.score ?: 0f
                        val topLabel = labelMap.getLabel(topIndex)  // Map index → display_name

                        if (topScore > confidenceThreshold) {
                            // Log
                            val time = SimpleDateFormat("HH:mm:ss, MMM d", Locale.US).format(Date())
                            val displayText =
                                "$topLabel (${String.format(Locale.US, "%.2f", topScore)}) — $time"

                            // Keep latest 5
                            if (recentSounds.size >= 5)
                                withContext(Dispatchers.Main) {
                                    recentSounds.removeAt(0)
                                }
                            withContext(Dispatchers.Main) { recentSounds.add(displayText) }



                            Log.d("SoundSense", "Alert! Detected $topLabel ($topScore)")

                            // Update Compose UI on main thread
                            withContext(Dispatchers.Main) {
                                onLabelDetected ( "$topLabel (${String.format(Locale.US, "%.2f", topScore)})")
                            }

                            // If it's an alert sound
                            if (alertKeywords.any { topLabel.contains(it, ignoreCase = true) }) {
                                triggerAlert(topLabel)
                            }

                            // Optional: trigger vibration
                            @Suppress("DEPRECATION")
                            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

                            vibrator.vibrate(
                                VibrationEffect.createOneShot(
                                    200,
                                    VibrationEffect.DEFAULT_AMPLITUDE
                                )
                            )
                        } else {
                            Log.d("SoundSense", "Detected $topLabel but below threshold ($topScore)")
                        }
                    } else {
                        Log.d("SoundSense", "Silence detected, skipping classification")
                    }

                    // Pause 2 seconds
                    delay(2000)
                }
            }
            catch (_: CancellationException) {
                    onStopListening()
                }

        }
    }




    private fun triggerAlert(label: String) {
        alertLabel = label
        showAlert = true

        // Vibrate strongly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 800, 300, 800), 0)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 300, 800), 0))
        }
    }
    
    // Use with a button in the UI
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
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Detected Sound:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(detectedLabel, style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Recent Sounds:",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(8.dp)
        ) {
            items(recentSounds.size) { index ->
                Text("• ${recentSounds[index]}")
            }
        }

        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onStart, enabled = !listening) {
                Text("Start Listening")
            }
            Button(
                onClick = onStop,
                enabled = listening,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Stop")
            }
        }
    }
}

@Composable
fun AlertScreen(alertLabel: String, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Red),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "⚠️ ALERT ⚠️",
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Detected: $alertLabel",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Close Alert", color = Color.Red)
            }
        }
    }
}
