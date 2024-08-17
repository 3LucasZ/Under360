package com.example.kotlininsta360demo.activity
//---IMPORTS---
//-Android-
//-Insta360-
//-Server-
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.arashivision.sdkcamera.InstaCameraSDK
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ICameraOperateCallback
import com.arashivision.sdkcamera.camera.callback.ICaptureStatusListener
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
import com.example.kotlininsta360demo.MyCaptureStatus
import com.example.kotlininsta360demo.MyPreviewStatus
import com.example.kotlininsta360demo.R
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.gson.gson
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.date.getTimeMillis
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.time.Duration

class MainActivity : BaseObserveCameraActivity(), IPreviewStatusListener, ILiveStatusListener,
    IExportCallback, PlayerViewListener, ICameraOperateCallback, ICaptureStatusListener {
    //---UI Components (assigned later)---
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
    private val bitRate = 8 * 1024 * 1024
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
    private var exporting = false
    private var exportId = -1
    private var exportProgress = 0.0
    //-Export Config-
    private val exportDirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/SDK_DEMO_EXPORT/"
    //-Preview State-
    private var previewImageStr: String = "";
    //-General State-
    var connectionIds = mutableListOf<Long>()
    private var previewStatus = MyPreviewStatus.IDLE // Idle | Normal | Live
    private val functionModes = intArrayOf(InstaCameraManager.FUNCTION_MODE_CAPTURE_NORMAL, InstaCameraManager.FUNCTION_MODE_RECORD_NORMAL, InstaCameraManager.FUNCTION_MODE_PREVIEW_STREAM)
    //---Initialize (run on app load)---
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        //-Base-
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //-Initialize Camera SDK-
        InstaCameraSDK.init(this.application)
        InstaMediaSDK.init(this.application)
        InstaCameraManager.getInstance().openCamera(CONNECT_TYPE_USB)
        //-Mobilize UI-
        previewView = findViewById(R.id.player_capture)
        previewView!!.setLifecycle(lifecycle)
        //---WS ROUTES--
        embeddedServer(Jetty, 8081) {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(15)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            routing {
                webSocket("/stream") {
                    val id = getTimeMillis()
                    connectionIds.add(id)
                    Log.w("websocket","websocket connected")
                    try {
                        while (true) {
                            Thread.sleep(200)
                            send(previewImageStr)
                            if (connectionIds.size > 1 && id < connectionIds.max()) {
                                close(CloseReason(CloseReason.Codes.NORMAL, "Too many clients!!"))
                            }
                        }
                    }catch (e: ClosedReceiveChannelException) {
                        println("onClose ${closeReason.await()}")
                        connectionIds.remove(id)
                    } catch (e: Throwable) {
                        println("onError ${closeReason.await()}")
                        connectionIds.remove(id)
                        e.printStackTrace()
                    }
                }
            }
        }.start(wait = false)
        //---REST API ROUTES---
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
                    InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_USB)
                    call.respond(mapOf("msg" to "ok"))
                }
                get("/command/disconnect") {
                    InstaCameraManager.getInstance().closeCamera()
                    call.respond(mapOf("msg" to "ok"))
                }
                //-Settings Routes-
                get("/get/metadata") {
                    val response = HashMap<String, Any>()
                    response["cameraType"] = InstaCameraManager.getInstance().cameraType
                    response["cameraVersion"] = InstaCameraManager.getInstance().cameraVersion
                    response["cameraSerial"] = InstaCameraManager.getInstance().cameraSerial
                    call.respond(response)
                }
                get("/get/all") {
                    val response = HashMap<String, Any>()
                    response["whiteBalance"] = InstaCameraManager.getInstance().getWhiteBalance(
                        FUNCTION_MODE_PREVIEW_STREAM
                    )
                    response["whiteBalanceValue"] = InstaCameraManager.getInstance().getWhiteBalanceValue(
                        FUNCTION_MODE_PREVIEW_STREAM
                    )
                    response["ISO"] = InstaCameraManager.getInstance().getISO(
                        FUNCTION_MODE_PREVIEW_STREAM
                    )
                    response["ISOTopLimit"] = InstaCameraManager.getInstance().getISOTopLimit(
                        FUNCTION_MODE_PREVIEW_STREAM
                    )
                    response["exposureMode"] = InstaCameraManager.getInstance().getExposureMode(
                        FUNCTION_MODE_PREVIEW_STREAM
                    )
                    response["exposureEV"] = InstaCameraManager.getInstance().getExposureEV(
                        FUNCTION_MODE_PREVIEW_STREAM
                    )
                    response["shutterMode"] = InstaCameraManager.getInstance().getShutterMode(
                        FUNCTION_MODE_PREVIEW_STREAM
                    )
                    response["shutterSpeed"] = InstaCameraManager.getInstance().getShutterSpeed(
                        FUNCTION_MODE_PREVIEW_STREAM
                    )
                    response["captureResolution"] = InstaCameraManager.getInstance().getResolutionFromCamera(
                        FUNCTION_MODE_CAPTURE_NORMAL)
                    response["recordResolution"] = InstaCameraManager.getInstance().getResolutionFromCamera(
                        FUNCTION_MODE_RECORD_NORMAL)
//                    response["batteryType"] = InstaCameraManager.getInstance().
//                    response["photoResolution"] = InstaCameraManager.getInstance().getPhotoResolutionFromCamera(
//                        FUNCTION_MODE_CAPTURE_NORMAL)
                    call.respond(response)
                }
                get("/get/whiteBalance") {
                    call.respond(mapOf("whiteBalance" to InstaCameraManager.getInstance().getWhiteBalance(InstaCameraManager.FUNCTION_MODE_PREVIEW_STREAM)))
                }
                get("/set/whiteBalance") {
                    for (mode in functionModes) InstaCameraManager.getInstance().setWhiteBalance(mode, call.request.queryParameters["whiteBalance"]!!.toInt())
                    call.respond(mapOf("msg" to "ok"))
                }
                //-Capture Routes-
                get("/command/capture") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (previewStatus == MyPreviewStatus.LIVE) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is busy livestreaming to Youtube"))
                    } else if (InstaCameraManager.getInstance().currentCaptureType != InstaCameraManager.CAPTURE_TYPE_IDLE) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is busy with ${InstaCameraManager.getInstance().currentCaptureType}"))
                    } else {
                        InstaCameraManager.getInstance().setCaptureStatusListener(this@MainActivity)
                        InstaCameraManager.getInstance().startNormalCapture(false)
                        call.respond(mapOf("msg" to "ok"))
                    }
                }
                get("/command/startRecord") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (previewStatus == MyPreviewStatus.LIVE) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is busy streaming to Youtube"))
                    } else if (InstaCameraManager.getInstance().currentCaptureType != InstaCameraManager.CAPTURE_TYPE_IDLE) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is busy with capture type ${InstaCameraManager.getInstance().currentCaptureType}"))
                    } else {
                        InstaCameraManager.getInstance().setCaptureStatusListener(this@MainActivity)
                        InstaCameraManager.getInstance().startNormalRecord()
                        call.respond(mapOf("msg" to "ok"))
                    }
                }
                get("/command/stopRecord") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (InstaCameraManager.getInstance().currentCaptureType != InstaCameraManager.CAPTURE_TYPE_NORMAL_RECORD) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is not recording right now"))
                    } else {
                        call.response.status(HttpStatusCode.OK)
                        InstaCameraManager.getInstance().stopNormalRecord()
                        call.respond(mapOf("msg" to "ok"))
                    }
                }
                get("/command/startLive") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (previewStatus != MyPreviewStatus.IDLE){
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is busy with preview type $previewStatus"))
                    }  else {
                        previewStatus = MyPreviewStatus.LIVE
                        startPreviewLive()
                        call.respond(mapOf("msg" to "ok"))
                    }
                }
                get("/command/stopLive") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (previewStatus != MyPreviewStatus.LIVE){
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is not livestreaming to youtube right now"))
                    } else {
                        call.response.status(HttpStatusCode.OK)
                        stopLivestream()
                        stopPreview()
                        call.respond(mapOf("msg" to "ok"))
                    }
                }
                get("/command/startPreviewNormal") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (previewStatus != MyPreviewStatus.IDLE){
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is busy with preview type $previewStatus"))
                    } else {
                        previewStatus = MyPreviewStatus.NORMAL
                        startPreviewNormal()
                        call.respond(mapOf("msg" to "ok"))
                    }
                }
                get("/command/stopPreviewNormal") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (previewStatus != MyPreviewStatus.NORMAL){
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is not livestreaming for preview right now"))
                    } else {
                        call.response.status(HttpStatusCode.OK)
                        call.respond(mapOf("msg" to "ok"))
                        stopPreview()
                    }
                }
                get("/command/showPreview"){
                    val response = HashMap<String, Any>()
                    response["data"] = previewImageStr;
                    call.respond(response)
                }
                //-Export Routes-
                get("/ls"){
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else {
                        val response = HashMap<String, Any>()
                        val prefix = InstaCameraManager.getInstance().cameraHttpPrefix;
                        val urls = InstaCameraManager.getInstance().allUrlList.map { prefix + it };
                        response["data"] = urls
                        call.respond(response)
                    }
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
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else {
                        // check url
                        val workWrapper = WorkWrapper(url)
                        if (workWrapper.height <= 10) {
                            call.response.status(HttpStatusCode.InternalServerError)
                            call.respond(mapOf("err" to "url does not exist"))
                        }
                        //delete file
                        else {
                            val fileUrls = listOf(*workWrapper.urlsForDelete)
                            InstaCameraManager.getInstance()
                                .deleteFileList(fileUrls, this@MainActivity)
                            call.respond(mapOf("msg" to "ok"))
                        }
                    }
                }
                get("/export/image") {
                    //get url
                    val request = call.request.queryParameters
                    val url = request["url"]
                    Log.w("url", url.toString())
                    // check camera
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (exporting) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is busy exporting"))
                    } else {
                        val exportWorkWrapper = WorkWrapper(url)
                        if (!exportWorkWrapper.isPhoto) {
                            call.response.status(HttpStatusCode.InternalServerError)
                            call.respond(mapOf("err" to "requested file is not a photo"))
                        } else if (exportWorkWrapper.height <= 10) {
                            call.response.status(HttpStatusCode.InternalServerError)
                            call.respond(mapOf("err" to "requested file does not exist"))
                        }else {
                            exporting = true
                            exportProgress = 0.0
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
                            call.respond(mapOf("msg" to "ok"))
                        }
                    }
                }
                get("/export/video") {
                    //get url
                    val request = call.request.queryParameters
                    val url = request["url"]
                    Log.w("url", url.toString())
                    // check camera
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (exporting) {
                        call.response.status(HttpStatusCode.InternalServerError)
                        call.respond(mapOf("err" to "camera is busy exporting"))
                    }else {
                        val workWrapper = WorkWrapper(url)
                        if (!workWrapper.isVideo) {
                            call.response.status(HttpStatusCode.InternalServerError)
                            call.respond(mapOf("err" to "requested file is not a video"))
                        } else if (workWrapper.height <= 10) {
                            call.response.status(HttpStatusCode.InternalServerError)
                            call.respond(mapOf("err" to "requested file does not exist"))
                        } else {
                            exporting = true
                            exportProgress = 0.0
                            val exportFileName = url?.substring(url.lastIndexOf("/")+1,url.lastIndexOf(".")) + ".mp4"
                            val exportVideoSettings = ExportVideoParamsBuilder()
                                .setExportMode(ExportUtils.ExportMode.PANORAMA)
                                .setTargetPath(exportDirPath + exportFileName)
//                                    .setBitrate(8 * 1024 * 1024).setFps(10).setWidth(512).setHeight(512)
                            exportId =
                                ExportUtils.exportVideo(
                                    workWrapper,
                                    exportVideoSettings,
                                    this@MainActivity
                                )
                            call.respond(mapOf("msg" to "ok"))
                        }
                    }
                }
                //-Status Routes-
                get("/status") {
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
                get("/status/settings") {
                    val response = HashMap<String, Any>()
                    response["whiteBalance"] = InstaCameraManager.getInstance().getWhiteBalance(InstaCameraManager.FUNCTION_MODE_PREVIEW_STREAM)
                    response["whiteBalanceValue"] = InstaCameraManager.getInstance().getWhiteBalanceValue(InstaCameraManager.FUNCTION_MODE_PREVIEW_STREAM)
                    response["ISO"] = InstaCameraManager.getInstance().getISO(InstaCameraManager.FUNCTION_MODE_PREVIEW_STREAM)
                    response["exposureMode"] = InstaCameraManager.getInstance().getExposureMode(InstaCameraManager.FUNCTION_MODE_PREVIEW_STREAM)
                    response["exposureEV"] = InstaCameraManager.getInstance().getExposureEV(InstaCameraManager.FUNCTION_MODE_PREVIEW_STREAM)
                    response["shutterMode"] = InstaCameraManager.getInstance().getShutterMode(InstaCameraManager.FUNCTION_MODE_PREVIEW_STREAM)
                    response["shutterSpeed"] = InstaCameraManager.getInstance().getShutterSpeed(InstaCameraManager.FUNCTION_MODE_PREVIEW_STREAM)
                    call.respond(response)
                }
                get("/status/poll") {
                    val response = HashMap<String, Any>()
                    //connection
                    response["connected"] = InstaCameraManager.getInstance().cameraConnectedType == InstaCameraManager.CONNECT_TYPE_USB
                    //ret
                    call.respond(response)
                }
                get("/status/operation") {
                    val response = HashMap<String, Any>()
                    response["captureStatus"] = InstaCameraManager.getInstance().currentCaptureType
                    response["previewStatus"] = previewStatus
                    response["wsStreamConnections"] = connectionIds.size
                    //ret
                    call.respond(response)
                }
                get("/export/status") {
                    val response = HashMap<String, Any>()
                    //export
                    response["exporting"] = exporting
//                    response["exportId"] = exportId
                    response["exportProgress"] = exportProgress
                    //ret
                    call.respond(response)
                }
            }
        }.start(wait = false)
    }
    //---API FUNCTIONS---
    private fun startPreviewNormal() {
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
    private fun stopPreview() {
        //stop preview
        InstaCameraManager.getInstance().closePreviewStream()
        InstaCameraManager.getInstance().setPreviewStatusChangedListener(null)
        previewView!!.destroy()
    }
    private fun startPreviewLive() {
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
    private fun startLivestream() {
        InstaCameraManager.getInstance().startLive(livestreamSettings, this)
    }
    private fun stopLivestream() {
        InstaCameraManager.getInstance().stopLive()
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
            //TODO: (Experimental) hack android.view.surface (RenderSurface) to RTMP stream to custom server super fast
    }
    //---CALLBACKS---
    //-Preview callbacks-
    private var mImageReader: ImageReader? = null
    private var mImageReaderHandlerThread: HandlerThread? = null
    private var mImageReaderHandler: Handler? = null
    private var imageCounter = 0;
    //Preview stream is opening
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
            try {
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
            } catch (e: Throwable) {
                Log.w("Fatal error catch",e.toString())
            }
        }, mImageReaderHandler)
    }
    //Preview stream started and playable
    override fun onOpened() {
        InstaCameraManager.getInstance().setStreamEncode()
        previewView!!.setPlayerViewListener(object : PlayerViewListener {
            override fun onLoadingFinish() {
                InstaCameraManager.getInstance().setPipeline(previewView!!.pipeline)
                if (previewStatus == MyPreviewStatus.LIVE) {
                    startLivestream()
                }
            }
            override fun onReleaseCameraPipeline() {
                InstaCameraManager.getInstance().setPipeline(null)
            }
        })
        if (previewStatus == MyPreviewStatus.NORMAL) {
            previewView!!.prepare(createNormalParams())
        } else if (previewStatus == MyPreviewStatus.LIVE){
            previewView!!.prepare(createLiveParams())
        } else {
            previewView!!.prepare(createNormalParams())
        }
        previewView!!.play()
        previewView!!.keepScreenOn = true
    }
    // Preview Stopped
    override fun onIdle() {
        previewStatus = MyPreviewStatus.IDLE
    }
    // Preview Failed
    override fun onError() {
        previewStatus = MyPreviewStatus.IDLE
    }
    //-Live Callbacks-
    @SuppressLint("SetTextI18n")
    override fun onLivePushError(error: Int, desc: String?) {
    }
    @SuppressLint("SetTextI18n")
    override fun onLiveFpsUpdate(fps: Int) {
        livestreamFPS = fps
    }
    @SuppressLint("SetTextI18n")
    override fun onLivePushStarted() {
    }
    @SuppressLint("SetTextI18n")
    override fun onLivePushFinished() {
    }
    //-Export callbacks-
    override fun onSuccess() {
        exporting = false
    }
    override fun onFail(p0: Int, p1: String?) {
        Log.w("MY ERROR", "EXPORT FAILED: $p0 | $p1")
        exporting = false
    }
    override fun onCancel() {
        exporting = false
    }
    override fun onProgress(progress: Float) {
        exportProgress = progress.toDouble()
    }
    //-Delete file callbacks-
    override fun onSuccessful() {
//        action = "Idle (delete success)"
    }
    override fun onFailed() {
//        action = "Idle (delete failed)"
    }
    override fun onCameraConnectError() {
//        action = "Idle (delete cameraConnectError)"
    }
    //-Main activity is no longer being viewed callback-
    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            // Auto close preview since page loses focus
            stopLivestream()
            stopPreview()
        }
    }
    //-Camera status changed callback-
    //Camera disconnected
    override fun onCameraStatusChanged(enabled: Boolean) {
        super.onCameraStatusChanged(enabled)
        if (!enabled) {
            stopLivestream()
            stopPreview()
        }
    }
    //-Capturing callbacks
    override fun onCaptureStarting() {}
    override fun onCaptureWorking() {}
    override fun onCaptureStopping() {}
    override fun onCaptureFinish(filePaths: Array<String?>?) {
        // If you use sdk api to capture, the filePaths could be callback
        // Otherwise, filePaths will be null
    }
    override fun onCaptureCountChanged(captureCount: Int) {
        // Interval shots
        // Only Interval Capture type will callback this
    }
    override fun onCaptureTimeChanged(captureTime: Long) {
        // Record Duration, in ms
        // Only Record type will callback this
    }
}