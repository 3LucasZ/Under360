package com.example.kotlininsta360demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.arashivision.sdkcamera.camera.InstaCameraManager

@Composable
fun UsbConnectButton() {
    CustomButton(text="UsbConnect", onClick={
        InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_USB)
    })
}

@Composable
fun WifiConnectButton() {
    CustomButton(text="WifiConnect", onClick={
        InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_WIFI)
    })
}

@Composable
fun DisconnectButton() {
    CustomButton(text="Disconnect", onClick={
        InstaCameraManager.getInstance().closeCamera()
    })
}

@Composable
fun CheckConnectButton(log: MutableState<String>) {
    CustomButton(text="CheckConnect", onClick={
        val type = InstaCameraManager.getInstance().cameraConnectedType
        log.value = msgToLog("Connection Type: $type")
    })
}