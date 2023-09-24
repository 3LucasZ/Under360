package com.example.kotlininsta360demo.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.arashivision.sdkcamera.InstaCameraSDK
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

        val thread: Thread = object : Thread() {
            @SuppressLint("SetTextI18n")
            override fun run() {
                try {
                    while (!this.isInterrupted) {
                        sleep(1000)
                        runOnUiThread {
                            // update TextView here!
                            findViewById<Button>(R.id.tv_cam_status).text = "Connected:"+InstaCameraManager.getInstance().cameraConnectedType+", Battery:"+InstaCameraManager.getInstance().cameraCurrentBatteryLevel+", Charging: "+InstaCameraManager.getInstance().isCameraCharging
                        }
                    }
                } catch (e: InterruptedException) {
                }
            }
        }

        thread.start()
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
