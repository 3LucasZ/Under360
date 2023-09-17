package com.example.kotlininsta360demo.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import com.arashivision.sdkcamera.InstaCameraSDK
import com.example.kotlininsta360demo.CheckConnectButton
import com.example.kotlininsta360demo.CustomButton
import com.example.kotlininsta360demo.DisconnectButton
import com.example.kotlininsta360demo.GetLiveSupportedResolutionButton
import com.example.kotlininsta360demo.Logger
import com.example.kotlininsta360demo.PrepareLiveButton
import com.example.kotlininsta360demo.StartLiveButton
import com.example.kotlininsta360demo.StartNormalCaptureButton
import com.example.kotlininsta360demo.StartNormalRecordButton
import com.example.kotlininsta360demo.StopLiveButton
import com.example.kotlininsta360demo.StopNormalRecordButton
import com.example.kotlininsta360demo.UsbConnectButton
import com.example.kotlininsta360demo.WifiConnectButton
import com.example.kotlininsta360demo.msgToLog
import androidx.appcompat.app.AppCompatActivity
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkmedia.InstaMediaSDK
import com.example.kotlininsta360demo.R


class MainActivity : BaseObserveCameraActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        InstaCameraSDK.init(this.application)
        InstaMediaSDK.init(this.application)

        findViewById<Button>(R.id.btn_connect).setOnClickListener{
            InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_USB)
        }
        findViewById<Button>(R.id.btn_disconnect).setOnClickListener{
            InstaCameraManager.getInstance().closeCamera()
        }
        findViewById<Button>(R.id.btn_capture).setOnClickListener{
            InstaCameraManager.getInstance().startNormalCapture(false)
        }
        findViewById<Button>(R.id.btn_live_activity).setOnClickListener {
            val intent = Intent(this, LiveActivity::class.java)
            startActivity(intent)
        }
    }
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
