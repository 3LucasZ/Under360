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
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.Jetty
import io.ktor.server.netty.Netty


class MainActivity : BaseObserveCameraActivity() {
    private var mBtnConnect: Button? = null
    private var mBtnDisconnect: Button? = null
    private var mBtnCapture: Button? = null
    private var mBtnLiveActivity: Button? = null

    private var liveIntent: Intent? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {


        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        InstaCameraSDK.init(this.application)
        InstaMediaSDK.init(this.application)

        mBtnConnect = findViewById<Button>(R.id.btn_connect);
        mBtnConnect?.setOnClickListener{
            InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_USB)
        }
        mBtnDisconnect = findViewById<Button>(R.id.btn_disconnect);
        mBtnDisconnect?.setOnClickListener{
            InstaCameraManager.getInstance().closeCamera()
        }
        mBtnCapture = findViewById<Button>(R.id.btn_capture);
        mBtnCapture?.setOnClickListener{
            InstaCameraManager.getInstance().startNormalCapture(false)
        }
        liveIntent = Intent(this, LiveActivity::class.java)
        mBtnLiveActivity = findViewById<Button>(R.id.btn_live_activity);
        mBtnLiveActivity?.setOnClickListener {
            startActivity(liveIntent)
        }

        val thread: Thread = object : Thread() {
            @SuppressLint("SetTextI18n")
            override fun run() {
                try {
                    while (!this.isInterrupted) {
                        sleep(1000)
                        runOnUiThread {
                            // update TextView here!
                            findViewById<TextView>(R.id.tv_cam_status).text =
                                "T:"+System.currentTimeMillis() +", Connected:"+InstaCameraManager.getInstance().cameraConnectedType+ ", Battery:"+InstaCameraManager.getInstance().cameraCurrentBatteryLevel+", Charging:"+InstaCameraManager.getInstance().isCameraCharging
                        }
                    }
                } catch (e: InterruptedException) {
                }
            }
        }
        thread.start()

        embeddedServer(Jetty, 8080) {
            install(ContentNegotiation) {
                gson {}
            }
            routing {
                get("/") {
                    call.respond(mapOf("message" to "Welcome to Huber360 API!"))
                }
                get("/command/connect"){
                    call.respond(mapOf("result" to mBtnConnect?.performClick()));
                }
                get("/command/capture"){
                    if (InstaCameraManager.getInstance().cameraConnectedType!=-1){
                        InstaCameraManager.getInstance().startNormalCapture(false)
                        call.respond(mapOf("msg" to "success"));
                    } else {
                        call.respond(mapOf("msg" to "err, camera not connected"));
                    }

                }
                get("/command/liveActivity"){
                    if (InstaCameraManager.getInstance().cameraConnectedType!=-1){
                        startActivity(liveIntent)
                        call.respond(mapOf("msg" to "success"));
                    } else {
                        call.respond(mapOf("msg" to "err, camera not connected"));
                    }
                }
                get("/status/time"){
                    call.respond(mapOf("time" to System.currentTimeMillis()))
                }
                get("/status/connectedType") {
                    call.respond(mapOf("connectedType" to InstaCameraManager.getInstance().cameraConnectedType))
                }
                get("/status/batteryLevel") {
                    call.respond(mapOf("batteryLevel" to "100"))
                }
            }
        }.start(wait = false)
    }
}