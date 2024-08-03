package com.example.kotlininsta360demo.activity


import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.StatFs
import android.util.Log
import android.widget.Button
import android.widget.TextView
//-Insta360-
import com.arashivision.sdkcamera.InstaCameraSDK
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ICameraOperateCallback
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
import com.arashivision.sdkmedia.work.WorkWrapper
import com.example.kotlininsta360demo.ImageUtil
import com.example.kotlininsta360demo.R
import io.ktor.serialization.gson.gson
//-Server-
import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.Jetty
import io.ktor.server.websocket.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.jetty.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.time.Duration


class MainActivity : BaseObserveCameraActivity(), IPreviewStatusListener, ILiveStatusListener,
    IExportCallback, PlayerViewListener, ICameraOperateCallback {
    //---UI Components (assigned later)---
    private var connectBtn: Button? = null
    private var disconnectBtn: Button? = null
    private var captureBtn: Button? = null
    private var startPreviewBtn: Button? = null
    private var stopPreviewBtn: Button? = null
    private var livestreamStatusText: TextView? = null
    private var previewView: InstaCapturePlayerView? = null

    //---State---
    //-Stream State-
    private var previewResolution: PreviewStreamResolution? = null
    private var livestreamFPS: Int = 0
    //-Stream Config-
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
    //-Export State-
    private var exportId = -1
    private var exportProgress = 0.0
    private var exportWorkWrapper = WorkWrapper("")
    //-Export Config-
    private val exportDirPath =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            .toString() + "/SDK_DEMO_EXPORT/"
    //-Preview State-
    var previewMode = 0; //NORMAL: 0, VIDEO: 1, STREAM: 2
    var previewImageStr: String = "";
    //-General State-
    var action = "Idle"
    var connections = 0

    //---Initialize (run on app load)---
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        //-Base-
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //-Initialize Camera SDK-
        InstaCameraSDK.init(this.application)
        InstaMediaSDK.init(this.application)
        //-Mobilize UI-
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
            startPreviewLive()
        }
        stopPreviewBtn = findViewById(R.id.btn_stop_preview)
        stopPreviewBtn?.setOnClickListener {
            stopLivestream()
        }
        previewView = findViewById(R.id.player_capture)
        previewView!!.setLifecycle(lifecycle)
        livestreamStatusText = findViewById(R.id.tv_live_status)

        //---WS STREAMER--
        embeddedServer(Jetty, 8081) {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(10000000000)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            routing {
                webSocket("/stream") {
                    if (connections >= 1) {
                        close(CloseReason(CloseReason.Codes.NORMAL, "Too many clients!!"))
                    } else {
                        connections += 1
                        Log.w("websocket","websocket connected")
                        try {
                            while (true) {
                                Thread.sleep(200)
                                send(previewImageStr)
                            }
                        }catch (e: ClosedReceiveChannelException) {
                            println("onClose ${closeReason.await()}")
                            connections -= 1
                        } catch (e: Throwable) {
                            println("onError ${closeReason.await()}")
                            connections -= 1
                            e.printStackTrace()
                        }
                    }
                }
            }
        }.start(wait = false)
        //---API ROUTES---
        embeddedServer(Jetty, 8080) {
            install(ContentNegotiation) {
                gson()
            }
            routing {
                get("/") {
                    call.respond(mapOf("msg" to "Welcome to Underwater API!"))
                }
                //-Connect Routes-
                get("/command/connect") {
                    connectCamera()
                    call.respond(mapOf("msg" to "ok"))
                }
                get("/command/disconnect") {
                    disconnectCamera()
                    call.respond(mapOf("msg" to "ok"))
                }
                //-Capture Routes-
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
                    startPreviewLive()
                    call.respond(mapOf("msg" to "ok"))
                }
                get("/command/stopLive") {
                    stopLivestream()
                    call.respond(mapOf("msg" to "ok"))
                }
                get("/command/startPreviewNormal") {
                    startPreviewNormal()
                    call.respond(mapOf("msg" to "ok"))
                }
                get("/command/stopPreviewNormal") {
                    stopPreviewNormal()
                    call.respond(mapOf("msg" to "ok"))
                }
                get("/command/showPreview"){
                    val response = HashMap<String, Any>()
                    response["data"] = previewImageStr;
                    call.respond(response)
                }
                //-Export Routes-
                get("/ls"){
                    val response = HashMap<String, Any>()
                    val prefix = InstaCameraManager.getInstance().cameraHttpPrefix;
                    val urls = InstaCameraManager.getInstance().allUrlList.map{prefix + it};
                    response["data"] = urls
                    call.respond(response)
                }
                get("/ls/verbose"){
                    val response = HashMap<String, Any>()
                    val prefix = InstaCameraManager.getInstance().cameraHttpPrefix;
                    val urls = InstaCameraManager.getInstance().allUrlList.map{prefix + it};
                    val data = mutableListOf<HashMap<String,Any>>();
                    for (url in urls) {
                        val workWrapper = WorkWrapper(url)
                        val map = HashMap<String, Any>()
                        map["url"] = url
                        map["width"] = workWrapper.width;
                        map["height"] = workWrapper.height;
                        map["durationInMs"] = workWrapper.durationInMs;
                        map["fps"] = workWrapper.fps
                        map["fileSize"] = workWrapper.fileSize
                        map["isPhoto"] = workWrapper.isPhoto
                        map["delete"] = workWrapper.urlsForDelete
                        data.add(map)
                    }
                    response["data"] = data
                    call.respond(response)
                }
                get("/inspect") {
                    val request = call.request.queryParameters
                    val url = request["url"]
                    Log.w("url", url.toString())
                    val workWrapper = WorkWrapper(url)
                    val response = HashMap<String, Any>()
                    response["width"] = workWrapper.width
                    response["height"] = workWrapper.height
                    response["durationInMs"] = workWrapper.durationInMs
                    response["bitrate"] = workWrapper.bitrate
                    response["fps"] = workWrapper.fps
                    response["creationTime"] = workWrapper.creationTime
                    response["fileSize"] = workWrapper.fileSize
                    response["delete"] = workWrapper.urlsForDelete
                    call.respond(response)
                }
                get("/rm"){
                    //get url
                    val request = call.request.queryParameters
                    val url = request["url"]
                    Log.w("url", url.toString())
                    // check camera
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.respond(mapOf("err" to "camera is not connected"))
                    }
                    // check url
                    val workWrapper = WorkWrapper(url)
                    if (workWrapper.height <= 10) {
                        call.respond(mapOf("err" to "url does not exist"))
                    }
                    //delete file
                    val fileUrls = listOf(*workWrapper.urlsForDelete)
                    InstaCameraManager.getInstance().deleteFileList(fileUrls, this@MainActivity)
                    call.respond(mapOf("msg" to "ok"))
                }
                get("/export/image") {
                    val request = call.request.queryParameters
                    val response = HashMap<String, Any>()
                    val url = request["url"]
                    Log.w("url", url.toString())
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
                get("/export/status") {
                    val response = HashMap<String, Any>()
                    //export
                    response["exportId"] = exportId
                    response["exportProgress"] = exportProgress
                    //ret
                    call.respond(response)
                }
                //-Status Routes-
                get("/status/camera") {
                    val response = HashMap<String, Any>()
                    //connection
                    response["connected"] = InstaCameraManager.getInstance().cameraConnectedType == InstaCameraManager.CONNECT_TYPE_USB
                    //battery
                    response["batteryLevel"] = InstaCameraManager.getInstance().cameraCurrentBatteryLevel
                    response["isCharging"] = InstaCameraManager.getInstance().isCameraCharging
                    //mem
                    response["freeSpace"] = InstaCameraManager.getInstance().cameraStorageFreeSpace
                    response["totalSpace"] = InstaCameraManager.getInstance().cameraStorageTotalSpace
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

    private fun startPreviewNormal() {
        previewMode = 0
        val list = InstaCameraManager.getInstance()
            .getSupportedPreviewStreamResolution(InstaCameraManager.PREVIEW_TYPE_NORMAL)
        if (list.isNotEmpty()) {
            InstaCameraManager.getInstance().setPreviewStatusChangedListener(this)
            previewResolution = list[0]
            val previewSettings = PreviewParamsBuilder()
                .setStreamResolution(previewResolution)
                .setPreviewType(InstaCameraManager.PREVIEW_TYPE_NORMAL)
                .setAudioEnabled(false)
            InstaCameraManager.getInstance().startPreviewStream(previewSettings)
        }
    }
    private fun stopPreviewNormal() {
        InstaCameraManager.getInstance().closePreviewStream()
        InstaCameraManager.getInstance().setPreviewStatusChangedListener(null)
    }
    private fun startPreviewLive() {
        previewMode = 2
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

    private fun stopLivestream() {
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
    private fun createLiveParams(): CaptureParamsBuilder? {
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

    private fun  createNormalParams(): CaptureParamsBuilder? {
        return  CaptureParamsBuilder()
            .setCameraType(InstaCameraManager.getInstance().cameraType)
            .setMediaOffset(InstaCameraManager.getInstance().mediaOffset)
            .setCameraSelfie(InstaCameraManager.getInstance().isCameraSelfie)
            .setLive(false)
            .setStabType(InstaStabType.STAB_TYPE_AUTO)
            .setResolutionParams(1024, 512, 10)
            //hack
            .setRenderModelType(CaptureParamsBuilder.RENDER_MODE_PLANE_STITCH).setScreenRatio(2, 1)
            .setCameraRenderSurfaceInfo(mImageReader!!.surface, mImageReader!!.width, mImageReader!!.height);
            //TODO: Try to hack android.view.surface (RenderSurface) to RTMP stream to custom server super fast
    }

    private fun totalMemory(): Long { //TODO: NOT FINISHED, CHECKOUT https://stackoverflow.com/questions/7115016/how-to-find-the-amount-of-free-storage-disk-space-left-on-android
        val statFs = StatFs(Environment.getRootDirectory().absolutePath)
        return (statFs.blockCountLong * statFs.blockSizeLong)
    }


    //---CALLBACKS---
    //-Main activity is no longer being viewed callback-
    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            // Auto close preview since page loses focus
            stopLivestream()
        }
    }
    //-Preview callbacks-
    //Stream is loading
    private var mImageReader: ImageReader? = null
    private var mImageReaderHandlerThread: HandlerThread? = null
    private var mImageReaderHandler: Handler? = null
    private var imageCounter = 0;
    override fun onOpening() {
        createSurfaceView()
    }
    private fun createSurfaceView() {
        if (mImageReader != null) {
            return;
        }
        mImageReaderHandlerThread = HandlerThread("camera render surface")
        mImageReaderHandlerThread!!.start()
        mImageReaderHandler = Handler(mImageReaderHandlerThread!!.looper)
        mImageReader = ImageReader.newInstance(1024, 512, PixelFormat.RGBA_8888, 2)
        mImageReader!!.setOnImageAvailableListener({ reader ->
            if (reader.maxImages > 0) {
                val image = reader.acquireLatestImage()
                if (image == null) { //every once in a while, the image is null. This will break the app, so we catch it carefully here.
                    println("NULL")
                }
                else if (imageCounter % 3 == 0) {
                    val plane: Image.Plane = image.planes[0]
                    val pixelStride: Int = plane.pixelStride
                    val rowStride: Int = plane.rowStride
                    val rowPadding: Int = rowStride - pixelStride * image.width
                    val bitmap = Bitmap.createBitmap(
                        image.width + rowPadding / pixelStride,
                        image.height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(plane.buffer)
                    //Here you have the preview as bitmap with equirectangular format
                    previewImageStr = ImageUtil.convert(bitmap)
                    image.close()
                } else {
                    image.close()
                }
            }
            imageCounter++
        }, mImageReaderHandler)
    }
    //Steam started and playable
    override fun onOpened() {
        InstaCameraManager.getInstance().setStreamEncode()
        previewView!!.setPlayerViewListener(object : PlayerViewListener {
            override fun onLoadingFinish() {
                InstaCameraManager.getInstance().setPipeline(previewView!!.pipeline)
                if (previewMode == 2) {
                    startLivestream()
                }
            }
            override fun onReleaseCameraPipeline() {
                InstaCameraManager.getInstance().setPipeline(null)
            }
        })
        if (previewMode == 0) {
            previewView!!.prepare(createNormalParams())
        }
        else if (previewMode == 2){
            previewView!!.prepare(createLiveParams())
        } else {
            previewView!!.prepare(createNormalParams())
        }
        previewView!!.play()
        previewView!!.keepScreenOn = true
    }
    //-Camera status changed callback-
    override fun onCameraStatusChanged(enabled: Boolean) {
        super.onCameraStatusChanged(enabled)
        if (!enabled) {
            stopLivestream()
        }
    }
    //-Live Callbacks-
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
    //-Export callbacks-
    override fun onSuccess() {
        Log.w("EXPORT CALLBACK", "ON SUCCESS")
        action = "Idle (export success)"
    }
    override fun onFail(p0: Int, p1: String?) {
        Log.w("EXPORT CALLBACK", "ON FAIL: $p0 | $p1")
        action = "Idle (export fail) $p0 and $p1"
    }
    override fun onCancel() {
        action = "Idle (export cancel)"
    }
    override fun onProgress(progress: Float) {
        Log.w("EXPORT CALLBACK", "ON PROGRESS: $progress")
        exportProgress = progress.toDouble()
    }

    //-Delete callbacks-
    override fun onSuccessful() {
        action = "Idle (delete success)"
    }
    override fun onFailed() {
        action = "Idle (delete failed)"
    }
    override fun onCameraConnectError() {
        action = "Idle (delete cameraConnectError)"
    }
}