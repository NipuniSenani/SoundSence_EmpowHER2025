package com.example.myapplication

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication.ui.theme.MyApplicationTheme

import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.lifecycleScope

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


class MainActivity : ComponentActivity() {
    // 🔹 Placeholder for when we start listening / integrating AudioClassifier
    private var listening = false  // outside startListening()
    private val CONFIDENCE_THRESHOLD = 0.5f
    private var detectedLabel by mutableStateOf("…") // for Compose UI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 🔹 Modern permission launcher
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    startListening()
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
            startListening()
        } else {
            // 🔹 Request permission
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        // 🔹 Compose UI
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SoundSenseScreen(
                        detectedLabel = detectedLabel,
                        onStart = { startListening() },
                        onStop = { stopListening() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

    }




    private fun startListening() {
        val audioHelper = AudioHelper(this)
        val labelMap = LabelMapHelper(this, "yamnet_class_map.csv")


        listening = true
        Toast.makeText(this, "Listening started...", Toast.LENGTH_SHORT).show()

        // Launch background coroutine
        lifecycleScope.launch(Dispatchers.IO) {
            while (listening) {
                val results = audioHelper.classify() // 1s capture
                if (results.isNotEmpty()) {
                    val topCategory = results[0].categories.maxByOrNull { it.score }
                    val topIndex = topCategory?.index ?: -1
                    val topScore = topCategory?.score ?: 0f
                    val topLabel = labelMap.getLabel(topIndex)  // Map index → display_name

                    if (topScore > CONFIDENCE_THRESHOLD) {
                        // Log
                        Log.d("SoundSense", "Alert! Detected $topLabel ($topScore)")

                        // Update Compose UI on main thread
                        withContext(Dispatchers.Main) {
                            detectedLabel = "$topLabel (${String.format("%.2f", topScore)})"
                        }

                        // Optional: trigger vibration
                        @Suppress("DEPRECATION")
                        val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator

                        vibrator.vibrate(
                            android.os.VibrationEffect.createOneShot(
                                200,
                                android.os.VibrationEffect.DEFAULT_AMPLITUDE
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
    }
    // Use with a button in the UI
    private fun stopListening() {
        listening = false
        Toast.makeText(this, "Listening stopped", Toast.LENGTH_SHORT).show()
    }


}

@Composable
fun Greeting(detectedLabel: String, modifier: Modifier = Modifier) {
    Text(
        text = detectedLabel,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}

@Composable
fun SoundSenseScreen(
    detectedLabel: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Detected Sound:",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = detectedLabel,
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onStart) {
                Text("Start Listening")
            }

            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Stop")
            }
        }
    }
}

