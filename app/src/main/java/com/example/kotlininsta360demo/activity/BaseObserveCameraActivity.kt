package com.example.kotlininsta360demo.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ICameraChangedCallback
import androidx.annotation.Nullable;

abstract class BaseObserveCameraActivity : AppCompatActivity(),
    ICameraChangedCallback {
    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InstaCameraManager.getInstance().registerCameraChangedCallback(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        InstaCameraManager.getInstance().unregisterCameraChangedCallback(this)
    }

    /**
     * Camera status changed
     *
     * @param enabled: Whether the camera is available
     */
    override fun onCameraStatusChanged(enabled: Boolean) {}

    /**
     * Camera connection failed
     *
     *
     * A common situation is that other phones or other applications of this phone have already
     * established a connection with this camera, resulting in this establishment failure,
     * and other phones need to disconnect from this camera first.
     */
    override fun onCameraConnectError(errorCode: Int) {}

    /**
     * SD card insertion notification
     *
     * @param enabled: Whether the current SD card is available
     */
    override fun onCameraSDCardStateChanged(enabled: Boolean) {}

    /**
     * SD card storage status changed
     *
     * @param freeSpace:  Currently available size
     * @param totalSpace: Total size
     */
    override fun onCameraStorageChanged(freeSpace: Long, totalSpace: Long) {}

    /**
     * Low battery notification
     */
    override fun onCameraBatteryLow() {}

    /**
     * Camera power change notification
     *
     * @param batteryLevel: Current power (0-100, always returns 100 when charging)
     * @param isCharging:   Whether the camera is charging
     */
    override fun onCameraBatteryUpdate(batteryLevel: Int, isCharging: Boolean) {}
}