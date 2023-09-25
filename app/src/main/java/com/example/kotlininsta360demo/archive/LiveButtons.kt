package com.example.kotlininsta360demo.archive

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ILiveStatusListener
import com.arashivision.sdkcamera.camera.live.LiveParamsBuilder
import com.arashivision.sdkcamera.camera.resolution.PreviewStreamResolution

@Composable
fun PrepareLiveButton(){
    CustomButton(text = "PrepareLive", onClick = {
        InstaCameraManager.getInstance().startPreviewStream(PreviewStreamResolution.STREAM_1920_960_30FPS, InstaCameraManager.PREVIEW_TYPE_LIVE);
        
    });
}

@Composable
fun StartLiveButton(log: MutableState<String>){
    val key = "g1eg-y0x6-r3e9-mfr3-6twg"
    CustomButton(text="StartLive", onClick={
        val rtmp = "rtmp://a.rtmp.youtube.com/live2/$key"
        val builder = LiveParamsBuilder()
            // (Must) Set the rtmp address to push stream
            .setRtmp(rtmp)
            // (Must) Set width to push, such as 1440
            .setWidth(1920)
            // (Must) Set height to push, such as 720
            .setHeight(1080)
            // (Must) Set fps to push, such as 30
            .setFps(30)
            // (Must) Set bitrate to push, such as 2*1024*1024
            .setBitrate(8*1024*1024)
            // (Optional) Whether the live is panorama or not, the default value is true
            .setPanorama(true)
        InstaCameraManager.getInstance().startLive(builder, object : ILiveStatusListener {
            override fun onLivePushStarted() {
                Log.w("STARTED","STARTED")
                log.value = msgToLog("STARTED")
            }
            override fun onLivePushFinished() {
                Log.w("FINISHED","FINISHED")
                log.value = msgToLog("FINISHED")
            }

            override fun onLivePushError(error: Int, desc: String?) {
                Log.w("ERROR","ERROR")
                log.value = msgToLog("ERROR")
            }
            override fun onLiveFpsUpdate(fps: Int) {
                Log.w("UPDATE","UPDATE")
                log.value = msgToLog("UPDATE")
            }
        })
    })
}
@Composable
fun StopLiveButton() {
    CustomButton(text="StopLive", onClick={
        InstaCameraManager.getInstance().stopLive()
    })
}
@Composable
fun GetLiveSupportedResolutionButton(log: MutableState<String>) {
    CustomButton(text="GetLiveSupportedResolution", onClick={
        val supportedList = InstaCameraManager.getInstance()
            .getSupportedPreviewStreamResolution(InstaCameraManager.PREVIEW_TYPE_LIVE)
        log.value = msgToLog("Supported List: $supportedList")

    })
}