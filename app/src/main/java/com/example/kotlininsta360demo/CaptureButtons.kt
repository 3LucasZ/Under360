package com.example.kotlininsta360demo

import androidx.compose.runtime.Composable
import com.arashivision.sdkcamera.camera.InstaCameraManager

@Composable
fun StartNormalCaptureButton() {
    CustomButton(text="StartNormalCapture", onClick={
        InstaCameraManager.getInstance().startNormalCapture(false)
    })
}
@Composable
fun StartNormalRecordButton() {
    CustomButton(text="StartNormalRecord", onClick={
        InstaCameraManager.getInstance().startNormalRecord()
    })
}
@Composable
fun StopNormalRecordButton() {
    CustomButton(text="StopNormalRecord", onClick={
        InstaCameraManager.getInstance().stopNormalRecord()
    })
}