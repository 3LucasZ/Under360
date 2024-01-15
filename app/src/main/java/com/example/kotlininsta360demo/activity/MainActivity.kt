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
    IExportCallback {
    //UI components to assign to later
    private var connectBtn: Button? = null
    private var disconnectBtn: Button? = null
    private var captureBtn: Button? = null
    private var startPreviewBtn: Button? = null
    private var stopPreviewBtn: Button? = null
    private var startLivestreamBtn: Button? = null
    private var stopLivestreamBtn: Button? = null
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
    private var exportId = -1;
    private var exportProgress = 0.0;

    //export configuration
    private val exportDirPath =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            .toString() + "/SDK_DEMO_EXPORT"

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
        startLivestreamBtn = findViewById(R.id.btn_start_livestream)
        startLivestreamBtn?.setOnClickListener {
            startLivestream()
        }
        stopLivestreamBtn = findViewById(R.id.btn_stop_livestream)
        stopLivestreamBtn?.setOnClickListener {
            stopLivestream()
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
                get("/command/connect") {
                    connectCamera()
                    call.respond(mapOf("msg" to "ok"))
                }
                get("/command/disconnect") {
                    disconnectCamera()
                    call.respond(mapOf("msg" to "ok"))
                }
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
                get("/command/startPreview") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != -1) {
                        startPreview()
                        call.respond(mapOf("msg" to "ok"))
                    } else {
                        call.respond(mapOf("err" to "camera not connected"))
                    }
                }
                get("/command/stopPreview") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != -1) {
                        stopPreview()
                        call.respond(mapOf("msg" to "ok"))
                    } else {
                        call.respond(mapOf("err" to "camera not connected"))
                    }
                }
                get("/command/startLivestream") {
                    startLivestream()
                    call.respond(mapOf("msg" to "ok"))
                }
                get("/command/stopLivestream") {
                    stopLivestream()
                    call.respond(mapOf("msg" to "ok"))
                }
                get("/inspect") {
                    val request = call.request.queryParameters
                    val url = request["url"]
                    Log.w("url", url.toString())
                    val workWrapper = WorkWrapper(url)
                    val response = HashMap<String, String>()
                    response["urlsRaw"] = workWrapper.getUrls(true).joinToString();
                    response["urls"] = workWrapper.getUrls(false).joinToString();
                    response["width"] = workWrapper.width.toString();
                    response["height"] = workWrapper.height.toString();
                    response["bitrate"] = workWrapper.bitrate.toString();
                    response["fps"] = workWrapper.fps.toString();
                    response["isPhoto"] = workWrapper.isPhoto.toString();
                    response["isVideo"] = workWrapper.isVideo.toString();
                    response["isCameraFile"] = workWrapper.isCameraFile.toString();
                    response["isLocalFile"] = workWrapper.isLocalFile.toString();
                    response["creationTime"] = workWrapper.creationTime.toString();
                    response["isPanoramaFile"] = workWrapper.isPanoramaFile.toString();
                    call.respond(response)
                }
                get("/download/image"){
                    val request = call.request.queryParameters
                    val url = request["url"]
                    Log.w("url", url.toString())
                    val workWrapper = WorkWrapper(url)
                    if (!workWrapper.isPhoto) {
                        call.respond(mapOf("err" to "url is not an image"))
                    } else {
                        val exportImageSettings = ExportImageParamsBuilder()
                            .setExportMode(ExportUtils.ExportMode.PANORAMA).setImageFusion(workWrapper.isPanoramaFile)
                            .setTargetPath(exportDirPath + "/" + System.currentTimeMillis() + ".jpg")
                        exportId = ExportUtils.exportImage(
                            workWrapper,
                            exportImageSettings,
                            this@MainActivity
                        )
                    }
                }
                get("/download/video") {
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
                            ExportVideoParamsBuilder().setTargetPath(exportDirPath + "/" + System.currentTimeMillis()).setBitrate(8 * 1024 * 1024).setFps(10).setWidth(512).setHeight(512);
                        exportId =
                            ExportUtils.exportVideo(
                                workWrapper,
                                exportVideoSettings,
                                this@MainActivity
                            )
                    }
                }
                get("/status/camera") {
                    val request = call.request.queryParameters
                    Log.w("request", request.toString())
                    val response = HashMap<String, String>()
                    //connection
                    if (request.contains("connectedType")) response["connectedType"] =
                        InstaCameraManager.getInstance().cameraConnectedType.toString()
                    //battery
                    if (request.contains("batteryLevel")) response["batteryLevel"] =
                        InstaCameraManager.getInstance().cameraCurrentBatteryLevel.toString()
                    if (request.contains("isCharging")) response["isCharging"] =
                        InstaCameraManager.getInstance().isCameraCharging.toString()
                    //mem
                    if (request.contains("isSdEnabled")) response["isSdEnabled"] =
                        InstaCameraManager.getInstance().isSdCardEnabled.toString()
                    if (request.contains("freeSpace")) response["freeSpace"] =
                        formatSize(InstaCameraManager.getInstance().cameraStorageFreeSpace)
                    if (request.contains("totalSpace")) response["totalSpace"] =
                        formatSize(InstaCameraManager.getInstance().cameraStorageTotalSpace)
                    if (request.contains("urlList")) response["urlList"] =
                        InstaCameraManager.getInstance().allUrlList.toString()
                    if (request.contains("workWrapperList")) response["workWrapperList"] =
                        WorkUtils.getAllCameraWorks(
                            InstaCameraManager.getInstance().cameraHttpPrefix,
                            InstaCameraManager.getInstance().cameraInfoMap,
                            InstaCameraManager.getInstance().allUrlList,
                            InstaCameraManager.getInstance().rawUrlList
                        ).toString()
                    //livestream
                    if (request.contains("livestreamFPS")) response["livestreamFPS"] =
                        livestreamFPS.toString()
                    //settings
                    if (request.contains("exportDirPath")) response["exportDirPath"] = exportDirPath
                    //export
                    if (request.contains("exportId")) response["exportId"] = exportId.toString();
                    if (request.contains("exportProgress")) response["exportProgress"] =
                        exportProgress.toString()
                    //ret
                    call.respond(response)
                }
                get("/status/khadas") {
                    val request = call.request.queryParameters
                    Log.w("request", request.toString())
                    val response = HashMap<String, String>()
                    //mem
                    if (request.contains("freeSpace")) response["freeSpace"] =
                        totalMemory().toString()
                    if (request.contains("totalSpace")) response["totalSpace"] =
                        totalMemory().toString()
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
        InstaCameraManager.getInstance().startNormalRecord();
    }

    private fun stopRecord() {
        InstaCameraManager.getInstance().stopNormalRecord();
    }

    private fun startPreview() {
        val list = InstaCameraManager.getInstance()
            .getSupportedPreviewStreamResolution(InstaCameraManager.PREVIEW_TYPE_LIVE)
        if (list.isNotEmpty()) {
            InstaCameraManager.getInstance().setPreviewStatusChangedListener(this)
            previewResolution = list[0]
        }
        val previewSettings = PreviewParamsBuilder()
            .setStreamResolution(previewResolution)
            .setPreviewType(InstaCameraManager.PREVIEW_TYPE_LIVE)
            .setAudioEnabled(audioEnabled)
        InstaCameraManager.getInstance().startPreviewStream(previewSettings)
    }

    private fun stopPreview() {
        stopLivestream()
        InstaCameraManager.getInstance().closePreviewStream()
        InstaCameraManager.getInstance().setPreviewStatusChangedListener(null)
        previewView!!.destroy()
    }

    private fun startLivestream() {
        previewView?.setLiveType(InstaCapturePlayerView.LIVE_TYPE_PANORAMA)
        InstaCameraManager.getInstance().startLive(livestreamSettings, this)
    }

    private fun stopLivestream() {
        InstaCameraManager.getInstance().stopLive()
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
    //Activity is no longer the one being viewed callback
    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            // Auto close preview after page loses focus
            stopPreview()
        }
    }

    //Preview steam started and playable callback
    override fun onOpened() {

        InstaCameraManager.getInstance().setStreamEncode()
        previewView!!.setPlayerViewListener(object : PlayerViewListener {
            override fun onLoadingFinish() {
                InstaCameraManager.getInstance().setPipeline(previewView!!.pipeline)
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
            stopLivestream()
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
        TODO("Not yet implemented")
    }

    override fun onFail(p0: Int, p1: String?) {
        TODO("Not yet implemented")
    }

    override fun onCancel() {
        TODO("Not yet implemented")
    }

    override fun onProgress(progress: Float) {
        exportProgress = progress.toDouble()
    }

}