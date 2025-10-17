package com.example.myapplication

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class LabelMapHelper(context: Context, csvFileName: String) {
    val labels: List<String>

    init {
        val inputStream = context.assets.open(csvFileName)
        val reader = BufferedReader(InputStreamReader(inputStream))
        labels = reader.lineSequence()
            .drop(1) // skip header
            .map { it.split(",")[2] } // display_name is the 3rd column
            .toList()
        reader.close()
    }

    fun getLabel(index: Int): String {
        return if (index in labels.indices) labels[index] else "Unknown"
    }
}
