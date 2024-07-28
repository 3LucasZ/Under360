package com.example.kotlininsta360demo.activity


import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.arashivision.sdkcamera.InstaCameraSDK
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ILiveStatusListener
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener
import com.arashivision.sdkcamera.camera.live.LiveParamsBuilder
import com.arashivision.sdkcamera.camera.preview.PreviewParamsBuilder
import com.arashivision.sdkcamera.camera.resolution.PreviewStreamResolution
import com.arashivision.sdkmedia.InstaMediaSDK
import com.arashivision.sdkmedia.export.ExportImageParamsBuilder
import com.arashivision.sdkmedia.export.ExportUtils
import com.arashivision.sdkmedia.export.ExportVideoParamsBuilder
import com.arashivision.sdkmedia.export.IExportCallback
import com.arashivision.sdkmedia.player.capture.CaptureParamsBuilder
import com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView
import com.arashivision.sdkmedia.player.config.InstaStabType
import com.arashivision.sdkmedia.player.listener.PlayerViewListener
import com.arashivision.sdkmedia.work.WorkUtils
import com.arashivision.sdkmedia.work.WorkWrapper
import com.example.kotlininsta360demo.R
import com.example.kotlininsta360demo.formatSize
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.Jetty


class MainActivity : BaseObserveCameraActivity(), IPreviewStatusListener, ILiveStatusListener,
    IExportCallback, PlayerViewListener {
    //UI components to assign to later
    private var connectBtn: Button? = null
    private var disconnectBtn: Button? = null
    private var captureBtn: Button? = null
    private var startPreviewBtn: Button? = null
    private var stopPreviewBtn: Button? = null
    private var livestreamStatusText: TextView? = null
    private var previewView: InstaCapturePlayerView? = null

    //streaming state
    private var previewResolution: PreviewStreamResolution? = null
    private var livestreamFPS: Int = 0

    //streaming configuration
    private val rtmp = "rtmp://a.rtmp.youtube.com/live2/g1eg-y0x6-r3e9-mfr3-6twg"
    private val width = 1920
    private val height = 1080
    private val fps = 30
    private val bitRate = 18 * 1024 * 1024
    private val panorama = true
    private val audioEnabled = true
    private val livestreamSettings = LiveParamsBuilder()
        .setRtmp(rtmp)
        .setWidth(width)
        .setHeight(height)
        .setFps(fps)
        .setBitrate(bitRate)
        .setPanorama(panorama)

    //export state
    private var exportId = -1
    private var exportProgress = 0.0
    private var exportWorkWrapper = WorkWrapper("")

    //export configuration
    private val exportDirPath =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            .toString() + "/SDK_DEMO_EXPORT/"

    //Initialize (run on app load)
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        //base
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //init camera SDK
        InstaCameraSDK.init(this.application)
        InstaMediaSDK.init(this.application)
        //set UI as variables and assign their behavior
        connectBtn = findViewById(R.id.btn_connect)
        connectBtn?.setOnClickListener {
            connectCamera()
        }
        disconnectBtn = findViewById(R.id.btn_disconnect)
        disconnectBtn?.setOnClickListener {
            disconnectCamera()
        }
        captureBtn = findViewById(R.id.btn_capture)
        captureBtn?.setOnClickListener {
            captureImage()
        }
        startPreviewBtn = findViewById(R.id.btn_start_preview)
        startPreviewBtn?.setOnClickListener {
            startPreview()
        }
        stopPreviewBtn = findViewById(R.id.btn_stop_preview)
        stopPreviewBtn?.setOnClickListener {
            stopPreview()
        }
        previewView = findViewById(R.id.player_capture)
        previewView!!.setLifecycle(lifecycle)
        livestreamStatusText = findViewById(R.id.tv_live_status)

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

        //---API ROUTES---
        embeddedServer(Jetty, 8080) {
            install(ContentNegotiation) {
                gson {}
            }
            routing {
                get("/") {
                    call.respond(mapOf("msg" to "Welcome to Underwater API!"))
                }
                //---CONNECT--
                get("/command/connect") {
                    connectCamera()
                    call.respond(mapOf("msg" to "ok"))
                }
                get("/command/disconnect") {
                    disconnectCamera()
                    call.respond(mapOf("msg" to "ok"))
                }
                //--CAPTURE--
                get("/command/capture") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != -1) {
                        captureImage()
                        call.respond(mapOf("msg" to "ok"))
                    } else {
                        call.respond(mapOf("err" to "camera not connected"))
                    }
                }
                get("/command/startRecord") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != -1) {
                        startRecord()
                        call.respond(mapOf("msg" to "ok"))
                    } else {
                        call.respond(mapOf("err" to "camera not connected"))
                    }
                }
                get("/command/stopRecord") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != -1) {
                        stopRecord()
                        call.respond(mapOf("msg" to "ok"))
                    } else {
                        call.respond(mapOf("err" to "camera not connected"))
                    }
                }
                get("/command/startLive") {
                    startPreview()
                    call.respond(mapOf("msg" to "ok"))
                }
                get("/command/stopLive") {
                    stopPreview()
                    call.respond(mapOf("msg" to "ok"))
                }
                //--EXPORT--
                get("/ls"){
                    val response = HashMap<String, Any>()
                    response["rawUrlList"] = InstaCameraManager.getInstance().rawUrlList;
                    response["allUrlList"] = InstaCameraManager.getInstance().allUrlList;
                    response["allUrlListIncludeRecording"] = InstaCameraManager.getInstance().allUrlListIncludeRecording;
                    response["cameraHttpPrefix"] = InstaCameraManager.getInstance().cameraHttpPrefix;
                    call.respond(response)
                }
                get("/inspect") {
                    val request = call.request.queryParameters
                    val url = request["url"]
                    Log.w("url", url.toString())
                    val workWrapper = WorkWrapper(url)
                    val response = HashMap<String, Any>()
                    response["urlsRaw"] = workWrapper.getUrls(true)
                    response["urlsNotRaw"] = workWrapper.getUrls(false)
                    response["width"] = workWrapper.width
                    response["height"] = workWrapper.height
                    response["durationInMs"] = workWrapper.durationInMs
                    response["bitrate"] = workWrapper.bitrate
                    response["fps"] = workWrapper.fps
                    response["isPhoto"] = workWrapper.isPhoto
                    response["isVideo"] = workWrapper.isVideo
                    response["isCameraFile"] = workWrapper.isCameraFile
                    response["isLocalFile"] = workWrapper.isLocalFile
                    response["creationTime"] = workWrapper.creationTime
                    response["isPanoramaFile"] = workWrapper.isPanoramaFile
                    call.respond(response)
                }
                get("/export/image") {
                    val request = call.request.queryParameters
                    val response = HashMap<String, Any>()
                    val url = request["url"]
                    exportWorkWrapper = WorkWrapper(url)

                    if (!exportWorkWrapper.isPhoto) {
                        response["msg"] = "requested url is not a photo"
                    } else {
                        val exportFileName = url?.substring(url.lastIndexOf("/")+1,url.lastIndexOf(".")) + ".jpg"
                        val exportImageSettings = ExportImageParamsBuilder()
                            .setExportMode(ExportUtils.ExportMode.PANORAMA)
                            .setImageFusion(exportWorkWrapper.isPanoramaFile)
                            .setTargetPath(exportDirPath + exportFileName)
                        exportId = ExportUtils.exportImage(
                            exportWorkWrapper,
                            exportImageSettings,
                            this@MainActivity
                        )
                        response["msg"] = "ok"
                    }
                    call.respond(response)
                }
                get("/export/video") {
                    val request = call.request.queryParameters
                    val url = request["url"]
                    Log.w("url", url.toString())
                    val workWrapper = WorkWrapper(url)
                    if (!workWrapper.isVideo) {
                        call.respond(mapOf("err" to "url is not a video"))
                    } else {
//                        val exportVideoSettings =
//                            ExportVideoParamsBuilder().setTargetPath(exportDirPath + "/" + System.currentTimeMillis())
//                                .setExportMode(ExportUtils.ExportMode.SPHERE).setWidth(16)
//                                .setHeight(16).setBitrate(1 * 1024 * 1024).setFps(1)
//                                .setDynamicStitch(false);
                        val exportVideoSettings =
                            ExportVideoParamsBuilder().setTargetPath(exportDirPath + System.currentTimeMillis() + ".mp4")
                                .setBitrate(8 * 1024 * 1024).setFps(10).setWidth(512).setHeight(512)
                        exportId =
                            ExportUtils.exportVideo(
                                workWrapper,
                                exportVideoSettings,
                                this@MainActivity
                            )
                    }
                }
                //--STATUS--
                get("/status/camera") {
                    val response = HashMap<String, Any>()
                    //connection
                    InstaCameraManager.getInstance().cameraConnectedType
                    //battery
                    InstaCameraManager.getInstance().cameraCurrentBatteryLevel
                    InstaCameraManager.getInstance().isCameraCharging
                    //mem
                    InstaCameraManager.getInstance().isSdCardEnabled
                    formatSize(InstaCameraManager.getInstance().cameraStorageFreeSpace)
                    formatSize(InstaCameraManager.getInstance().cameraStorageTotalSpace)
//                    InstaCameraManager.getInstance().allUrlList
//                    WorkUtils.getAllCameraWorks(
//                        InstaCameraManager.getInstance().cameraHttpPrefix,
//                        InstaCameraManager.getInstance().cameraInfoMap,
//                        InstaCameraManager.getInstance().allUrlList,
//                        InstaCameraManager.getInstance().rawUrlList
//                    ).toString()
                    //livestream
                    livestreamFPS.toString()
                    //settings
                    response["exportDirPath"] = exportDirPath
                    //export
                    response["exportId"] = exportId.toString()
                    response["exportProgress"] = exportProgress.toString()
                    //ret
                    call.respond(response)
                }
                get("/status/khadas") {
                    val request = call.request.queryParameters
                    Log.w("request", request.toString())
                    val response = HashMap<String, Any>()
                    //mem
                    response["freeSpace"] = totalMemory()
                    response["totalSpace"] = totalMemory()
                    if (request.contains("urlList")) response["urlList"] =
                        InstaCameraManager.getInstance().allUrlList.toString()
                    //ret
                    call.respond(response)
                }
            }
        }.start(wait = false)
    }


    //---API FUNCTIONS---
    private fun connectCamera() {
        InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_USB)
    }

    private fun disconnectCamera() {
        InstaCameraManager.getInstance().closeCamera()
    }

    private fun captureImage() {
        InstaCameraManager.getInstance().startNormalCapture(false)
    }

    private fun startRecord() {
        InstaCameraManager.getInstance().startNormalRecord()
    }

    private fun stopRecord() {
        InstaCameraManager.getInstance().stopNormalRecord()
    }

    private fun startPreview() {
        val list = InstaCameraManager.getInstance()
            .getSupportedPreviewStreamResolution(InstaCameraManager.PREVIEW_TYPE_LIVE)
        if (list.isNotEmpty()) {
            InstaCameraManager.getInstance().setPreviewStatusChangedListener(this)
            previewResolution = list[0]
            val previewSettings = PreviewParamsBuilder()
                .setStreamResolution(previewResolution)
                .setPreviewType(InstaCameraManager.PREVIEW_TYPE_LIVE)
                .setAudioEnabled(audioEnabled)
            InstaCameraManager.getInstance().startPreviewStream(previewSettings)
        }
    }

    private fun stopPreview() {
        //stop live
        InstaCameraManager.getInstance().stopLive()
        //stop preview
        InstaCameraManager.getInstance().closePreviewStream()
        InstaCameraManager.getInstance().setPreviewStatusChangedListener(null)
        previewView!!.destroy()
    }

    private fun startLivestream() {
        InstaCameraManager.getInstance().startLive(livestreamSettings, this)
    }

    //---HELPERS---
    private fun createParams(): CaptureParamsBuilder? {
        return CaptureParamsBuilder()
            .setCameraType(InstaCameraManager.getInstance().cameraType)
            .setMediaOffset(InstaCameraManager.getInstance().mediaOffset)
            .setMediaOffsetV2(InstaCameraManager.getInstance().mediaOffsetV2)
            .setMediaOffsetV3(InstaCameraManager.getInstance().mediaOffsetV3)
            .setCameraSelfie(InstaCameraManager.getInstance().isCameraSelfie)
            .setGyroTimeStamp(InstaCameraManager.getInstance().gyroTimeStamp)
            .setBatteryType(InstaCameraManager.getInstance().batteryType)
            .setStabType(InstaStabType.STAB_TYPE_AUTO)
            .setStabEnabled(true)
            .setLive(true)
            .setGestureEnabled(false) //added
            .setResolutionParams(
                previewResolution!!.width,
                previewResolution!!.height,
                previewResolution!!.fps
            )
    }

    private fun totalMemory(): Long { //TODO: NOT FINISHED, CHECKOUT https://stackoverflow.com/questions/7115016/how-to-find-the-amount-of-free-storage-disk-space-left-on-android
        val statFs = StatFs(Environment.getRootDirectory().absolutePath)
        return (statFs.blockCountLong * statFs.blockSizeLong)
    }


    //---CALLBACKS---
    //Main activity is no longer being viewed callback
    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            // Auto close preview since page loses focus
            stopPreview()
        }
    }

    //Preview callback
    //Steam started and playable
    override fun onOpened() {
        InstaCameraManager.getInstance().setStreamEncode()
        previewView!!.setPlayerViewListener(object : PlayerViewListener {
            override fun onLoadingFinish() {
                InstaCameraManager.getInstance().setPipeline(previewView!!.pipeline)
                startLivestream()
            }

            override fun onReleaseCameraPipeline() {
                InstaCameraManager.getInstance().setPipeline(null)
            }
        })
        previewView!!.prepare(createParams())
        previewView!!.play()
        previewView!!.keepScreenOn = true
    }

    //Camera status changed callback
    override fun onCameraStatusChanged(enabled: Boolean) {
        super.onCameraStatusChanged(enabled)
        if (!enabled) {
            stopPreview()
        }
    }

    //Live callbacks
    @SuppressLint("SetTextI18n")
    override fun onLivePushError(error: Int, desc: String?) {
        livestreamStatusText?.text = "Live Push Error: ($error) ($desc)"
    }

    @SuppressLint("SetTextI18n")
    override fun onLiveFpsUpdate(fps: Int) {
        livestreamFPS = fps
        livestreamStatusText?.text = "FPS: $fps"
    }

    @SuppressLint("SetTextI18n")
    override fun onLivePushStarted() {
        livestreamStatusText?.text = "Live Push Started"
    }

    @SuppressLint("SetTextI18n")
    override fun onLivePushFinished() {
        livestreamStatusText?.text = "Live Push Finished"
    }

    //Export callbacks
    override fun onSuccess() {
        Log.w("EXPORT CALLBACK", "ON SUCCESS")
    }

    override fun onFail(p0: Int, p1: String?) {
        Log.w("EXPORT CALLBACK", "ON FAIL: $p0 | $p1")
    }

    override fun onCancel() {
        Log.w("EXPORT CALLBACK", "ON CANCEL")
    }

    override fun onProgress(progress: Float) {
        Log.w("EXPORT CALLBACK", "ON PROGRESS: $progress")
        exportProgress = progress.toDouble()
    }
}