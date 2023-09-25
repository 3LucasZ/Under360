package com.example.kotlininsta360demo.archive

import android.util.Log
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun CustomButton(text: String, onClick: () -> Unit){
    Button (
        onClick={
            Log.w(text,text)
            onClick()

        },content={ Text(text) }
    )
}