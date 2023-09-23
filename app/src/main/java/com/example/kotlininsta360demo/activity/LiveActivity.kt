package com.example.kotlininsta360demo.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import android.widget.ToggleButton
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ILiveStatusListener
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener
import com.arashivision.sdkcamera.camera.live.LiveParamsBuilder
import com.arashivision.sdkcamera.camera.preview.PreviewParamsBuilder
import com.arashivision.sdkcamera.camera.resolution.PreviewStreamResolution
import com.arashivision.sdkmedia.player.capture.CaptureParamsBuilder
import com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView
import com.arashivision.sdkmedia.player.config.InstaStabType
import com.arashivision.sdkmedia.player.listener.PlayerViewListener
import com.example.kotlininsta360demo.R


class LiveActivity : BaseObserveCameraActivity(), IPreviewStatusListener, ILiveStatusListener {
    private val rtmp = "rtmp://a.rtmp.youtube.com/live2/g1eg-y0x6-r3e9-mfr3-6twg"
    private val width = 1920;
    private val height = 1080;
    private val fps = 30;
    private val bitRate =  8 * 1024 * 1024;

    //private val panorama = true;
    //private val audioEnabled = true;

    //UI Components to assign to later
    private var mTvLiveStatus: TextView? = null
    private var mCapturePlayerView: InstaCapturePlayerView? = null
    private var mBtnLive: ToggleButton? = null
    private var mCurrentResolution: PreviewStreamResolution? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)

        mBtnLive = findViewById(R.id.btn_live)
        mCapturePlayerView = findViewById(R.id.player_capture);
        mTvLiveStatus = findViewById(R.id.tv_live_status);

        mBtnLive?.setOnClickListener {
            if (mBtnLive?.isChecked == true){
                mBtnLive?.isChecked = checkToStartLive();
            } else {
                stopLive();
            }
        }

        mCapturePlayerView!!.setLifecycle(lifecycle);

            val list = InstaCameraManager.getInstance()
                .getSupportedPreviewStreamResolution(InstaCameraManager.PREVIEW_TYPE_LIVE)
        if (list.isNotEmpty()) {
            mCurrentResolution = list[0]
            InstaCameraManager.getInstance().setPreviewStatusChangedListener(this)

        }

        restartPreview();
    }
    private fun restartPreview() {
        val builder = PreviewParamsBuilder()
            .setStreamResolution(mCurrentResolution)
            .setPreviewType(InstaCameraManager.PREVIEW_TYPE_LIVE)
            .setAudioEnabled(true)
        InstaCameraManager.getInstance().closePreviewStream()
        InstaCameraManager.getInstance().startPreviewStream(builder)
    }
    private fun checkToStartLive(): Boolean {
        mCapturePlayerView?.setLiveType(InstaCapturePlayerView.LIVE_TYPE_PANORAMA);
        // 设置网络ID即可在使用WIFI连接相机时使用4G网络推流
        val builder = LiveParamsBuilder()
            .setRtmp(rtmp)
            .setWidth(width)
            .setHeight(height)
            .setFps(fps)
            .setBitrate(bitRate)
            .setPanorama(true)
        InstaCameraManager.getInstance().startLive(builder, this)
        return true;
    }
    private fun stopLive() {
        InstaCameraManager.getInstance().stopLive()
    }
    override fun onOpened() {
        // Preview stream is on and can be played
        InstaCameraManager.getInstance().setStreamEncode()
        mCapturePlayerView!!.setPlayerViewListener(object : PlayerViewListener {
            override fun onLoadingFinish() {
                InstaCameraManager.getInstance().setPipeline(mCapturePlayerView!!.pipeline)
            }

            override fun onReleaseCameraPipeline() {
                InstaCameraManager.getInstance().setPipeline(null)
            }
        })
        mCapturePlayerView!!.prepare(createParams())
        mCapturePlayerView!!.play()
        mCapturePlayerView!!.keepScreenOn = true
    }
    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            // Auto close preview after page loses focus
            InstaCameraManager.getInstance().stopLive()
            InstaCameraManager.getInstance().closePreviewStream()
            InstaCameraManager.getInstance().setPreviewStatusChangedListener(null)
            mCapturePlayerView!!.destroy()
        }
    }
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
                mCurrentResolution!!.width,
                mCurrentResolution!!.height,
                mCurrentResolution!!.fps
            )
    }

    //Status Change
    override fun onCameraStatusChanged(enabled: Boolean) {
        super.onCameraStatusChanged(enabled)
        if (!enabled) {
            stopLive();
        }
    }

    //TV
    @SuppressLint("SetTextI18n")
    override fun onLivePushError(error: Int, desc: String?) {
        mTvLiveStatus?.text = "Live Push Error: ($error) ($desc)"
    }
    @SuppressLint("SetTextI18n")
    override fun onLiveFpsUpdate(fps: Int) {
        mTvLiveStatus?.text = "FPS: $fps";
    }
    @SuppressLint("SetTextI18n")
    override fun onLivePushStarted() {
        mTvLiveStatus?.text = "Live Push Started"
    }
    @SuppressLint("SetTextI18n")
    override fun onLivePushFinished() {
        mTvLiveStatus?.text = "Live Push Finished"
    }
}