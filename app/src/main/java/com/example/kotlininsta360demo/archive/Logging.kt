package com.example.kotlininsta360demo.archive

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState

@Composable
fun Logger(log: MutableState<String>) {
    Text(text=log.value)
}
fun msgToLog(msg: String): String {
    return ("time: "+System.currentTimeMillis()+"\n"+msg)
}