package com.example.kotlininsta360demo.archive

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

//@Composable
//fun MainScreen(msg: String) {
//    val log = remember { mutableStateOf(msgToLog(msg)) }
//    Column {
//        UsbConnectButton()
//        WifiConnectButton()
//        DisconnectButton()
//        CheckConnectButton(log = log)
//
//        StartNormalCaptureButton()
//        StartNormalRecordButton()
//        StopNormalRecordButton()
//
//        PrepareLiveButton()
//        StartLiveButton(log = log)
//        StopLiveButton()
//        GetLiveSupportedResolutionButton(log = log)
//
//        Logger(log = log)
//    }
//}
