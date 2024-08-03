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

//        //Infinite loop to update top bar UI with latest information
//        val thread: Thread = object : Thread() {
//            @SuppressLint("SetTextI18n")
//            override fun run() {
//                try {
//                    while (!this.isInterrupted) {
//                        sleep(1000)
//                        runOnUiThread {
//                            // update TextView here!
//                            findViewById<TextView>(R.id.tv_cam_status).text =
//                                "T:" + System.currentTimeMillis() + ", Connected:" + InstaCameraManager.getInstance().cameraConnectedType + ", Battery:" + InstaCameraManager.getInstance().cameraCurrentBatteryLevel + ", Charging:" + InstaCameraManager.getInstance().isCameraCharging
//                        }
//                    }
//                } catch (e: InterruptedException) {
//                    Log.w("INTERRUPTED UPDATE THREAD",e)
//                }
//            }
//        }
//        thread.start()