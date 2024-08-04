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
import io.ktor.serialization.gson.gson
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
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
    private val exportDirPath =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            .toString() + "/SDK_DEMO_EXPORT/"
    //-Preview State-
    private var previewImageStr: String = "";
    //-General State-
    var connections = 0
    private var captureStatus = MyCaptureStatus.IDLE // Idle | Capture | Record | Live
    private var previewStatus = MyPreviewStatus.IDLE // Idle | Normal | Live
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
        previewView = findViewById(R.id.player_capture)
        previewView!!.setLifecycle(lifecycle)
        //---WS ROUTES--
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
                //-Capture Routes-
                get("/command/capture") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (previewStatus == MyPreviewStatus.LIVE) {
                        call.respond(mapOf("err" to "camera is busy livestreaming to Youtube"))
                    } else if (captureStatus == MyCaptureStatus.RECORD) {
                        call.respond(mapOf("err" to "camera is busy recording"))
                    } else if (captureStatus == MyCaptureStatus.CAPTURE) {
                        call.respond(mapOf("err" to "camera is busy capturing"))
                    } else {
                        captureStatus = MyCaptureStatus.CAPTURE
                        InstaCameraManager.getInstance().setCaptureStatusListener(this@MainActivity)
                        InstaCameraManager.getInstance().startNormalCapture(false)
                        call.respond(mapOf("msg" to "ok"))
                    }
                }
                get("/command/startRecord") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (previewStatus == MyPreviewStatus.LIVE) {
                        call.respond(mapOf("err" to "camera is busy streaming to Youtube"))
                    } else if (captureStatus == MyCaptureStatus.RECORD) {
                        call.respond(mapOf("err" to "camera is busy recording"))
                    } else if (captureStatus == MyCaptureStatus.CAPTURE) {
                        call.respond(mapOf("err" to "camera is busy capturing"))
                    } else {
                        captureStatus = MyCaptureStatus.RECORD
                        InstaCameraManager.getInstance().setCaptureStatusListener(this@MainActivity)
                        InstaCameraManager.getInstance().startNormalRecord()
                        call.respond(mapOf("msg" to "ok"))
                    }
                }
                get("/command/stopRecord") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (captureStatus != MyCaptureStatus.RECORD) {
                        call.respond(mapOf("err" to "camera is not recording right now"))
                    } else {
                        InstaCameraManager.getInstance().stopNormalRecord()
                        call.respond(mapOf("msg" to "ok"))
                    }
                }
                get("/command/startLive") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (previewStatus == MyPreviewStatus.NORMAL){
                        call.respond(mapOf("err" to "camera is busy streaming for preview"))
                    } else if (previewStatus == MyPreviewStatus.LIVE){
                        call.respond(mapOf("err" to "camera is busy livestreaming to youtube"))
                    } else {
                        previewStatus = MyPreviewStatus.LIVE
                        startPreviewLive()
                        call.respond(mapOf("msg" to "ok"))
                    }
                }
                get("/command/stopLive") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (previewStatus != MyPreviewStatus.LIVE){
                        call.respond(mapOf("err" to "camera is not livestreaming to youtube right now"))
                    } else {
                        stopLivestream()
                        stopPreview()
                        call.respond(mapOf("msg" to "ok"))
                    }
                }
                get("/command/startPreviewNormal") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (previewStatus == MyPreviewStatus.NORMAL){
                        call.respond(mapOf("err" to "camera is busy livestreaming for preview"))
                    } else if (previewStatus == MyPreviewStatus.LIVE){
                        call.respond(mapOf("err" to "camera is busy livestreaming to youtube"))
                    } else {
                        previewStatus = MyPreviewStatus.NORMAL
                        startPreviewNormal()
                        call.respond(mapOf("msg" to "ok"))
                    }
                }
                get("/command/stopPreviewNormal") {
                    if (InstaCameraManager.getInstance().cameraConnectedType != InstaCameraManager.CONNECT_TYPE_USB) {
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (previewStatus != MyPreviewStatus.NORMAL){
                        call.respond(mapOf("err" to "camera is not livestreaming for preview right now"))
                    } else {
                        stopPreview()
                        call.respond(mapOf("msg" to "ok"))
                    }
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
                    } else {
                        // check url
                        val workWrapper = WorkWrapper(url)
                        if (workWrapper.height <= 10) {
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
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (exporting) {
                        call.respond(mapOf("err" to "camera is busy exporting"))
                    } else {
                        val exportWorkWrapper = WorkWrapper(url)
                        if (!exportWorkWrapper.isPhoto) {
                            call.respond(mapOf("err" to "requested file is not a photo"))
                        } else if (exportWorkWrapper.height <= 10) {
                            call.respond(mapOf("err" to "requested file does not exist"))
                        }else {
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
                        call.respond(mapOf("err" to "camera is not connected"))
                    } else if (exporting) {
                        call.respond(mapOf("err" to "camera is busy exporting"))
                    }else {
                        val workWrapper = WorkWrapper(url)
                        if (!workWrapper.isVideo) {
                            call.respond(mapOf("err" to "requested file is not a video"))
                        } else if (workWrapper.height <= 10) {
                            call.respond(mapOf("err" to "requested file does not exist"))
                        } else {
                            val exportFileName = url?.substring(url.lastIndexOf("/")+1,url.lastIndexOf(".")) + ".mp4"
                            val exportVideoSettings =
                                ExportVideoParamsBuilder().setTargetPath(exportDirPath + exportFileName)
                                    .setBitrate(8 * 1024 * 1024).setFps(10).setWidth(512).setHeight(512)
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
                get("/export/status") {
                    val response = HashMap<String, Any>()
                    //export
                    response["exporting"] = exporting
                    response["exportId"] = exportId
                    response["exportProgress"] = exportProgress
                    //ret
                    call.respond(response)
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
        captureStatus = MyCaptureStatus.IDLE
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