package net.touchcapture.qr.flutterqr

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.hardware.Camera.CameraInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.platform.PlatformView

class QRView(private val registrar: PluginRegistry.Registrar, id: Int) :
        PlatformView, MethodChannel.MethodCallHandler, Application.ActivityLifecycleCallbacks {

    companion object {
        const val CAMERA_REQUEST_ID = 513469796
    }

    private var barcodeView: BarcodeView? = null
    private val activity = registrar.activity()
    private var requestingPermission = false
    private var isTorchOn: Boolean = false

    var cameraPermissionContinuation: Runnable? = null
    val channel: MethodChannel

    init {
        registrar.addRequestPermissionsResultListener(CameraRequestPermissionsListener())
        channel = MethodChannel(registrar.messenger(), "net.touchcapture.qr.flutterqr/qrview_$id")
        channel.setMethodCallHandler(this)
        checkAndRequestPermission(null)
        activity?.application?.registerActivityLifecycleCallbacks(this)
    }

    private fun flipCamera() {
        barcodeView?.pause()
        val settings = barcodeView?.cameraSettings

        if (settings?.requestedCameraId == CameraInfo.CAMERA_FACING_FRONT) {
            settings.requestedCameraId = CameraInfo.CAMERA_FACING_BACK
        } else {
            settings?.requestedCameraId = CameraInfo.CAMERA_FACING_FRONT
        }

        barcodeView?.cameraSettings = settings
        barcodeView?.resume()
    }

    private fun toggleFlash() {
        if (hasFlash()) {
            barcodeView?.setTorch(!isTorchOn)
            isTorchOn = !isTorchOn
        }

    }

    private fun pauseCamera() {
        if (barcodeView?.isPreviewActive == true) {
            barcodeView?.pause()
        }
    }

    private fun resumeCamera() {
        if (barcodeView?.isPreviewActive == false) {
            barcodeView?.resume()
        }
    }

    private fun hasFlash(): Boolean {
        return registrar.activeContext().packageManager
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    override fun getView(): View {
        return initBarCodeView()?.apply {
            resume()
        }!!
    }

    private fun initBarCodeView(): BarcodeView? {
        if (barcodeView == null) {
            barcodeView = createBarCodeView()
        }
        return barcodeView
    }

    private fun createBarCodeView(): BarcodeView? {
        val barcode = BarcodeView(activity)
        val defaultDecoderFactory = DefaultDecoderFactory()
        barcode.decoderFactory = defaultDecoderFactory
        val decodeCallback = object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                channel.invokeMethod("onRecognizeQR", result.text)
            }

            override fun possibleResultPoints(resultPoints: List<ResultPoint>) {
                // not required
            }
        }
        barcode.decodeContinuous(decodeCallback)
        return barcode
    }

    override fun dispose() {
        barcodeView?.pause()
        barcodeView = null
    }

    private inner class CameraRequestPermissionsListener : PluginRegistry.RequestPermissionsResultListener {
        override fun onRequestPermissionsResult(id: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
            if (id == CAMERA_REQUEST_ID && grantResults[0] == PERMISSION_GRANTED) {
                cameraPermissionContinuation?.run()
                return true
            }
            return false
        }
    }

    private fun hasCameraPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                activity?.checkSelfPermission(Manifest.permission.CAMERA) == PERMISSION_GRANTED
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "checkAndRequestPermission" -> {
                checkAndRequestPermission(result)
            }
            "flipCamera" -> {
                flipCamera()
            }
            "toggleFlash" -> {
                toggleFlash()
            }
            "pauseCamera" -> {
                pauseCamera()
            }
            "resumeCamera" -> {
                resumeCamera()
            }
            "stopCamera" -> {
                dispose()
            }
        }
    }

    private fun checkAndRequestPermission(result: MethodChannel.Result?) {
        if (cameraPermissionContinuation != null) {
            result?.error("cameraPermission", "Camera permission request ongoing", null)
        }

        cameraPermissionContinuation = Runnable {
            cameraPermissionContinuation = null
            if (!hasCameraPermission()) {
                result?.error(
                        "cameraPermission", "MediaRecorderCamera permission not granted", null)
                return@Runnable
            }
        }

        requestingPermission = false
        if (hasCameraPermission()) {
            cameraPermissionContinuation?.run()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestingPermission = true
                activity?.requestPermissions(
                        arrayOf(Manifest.permission.CAMERA),
                        CAMERA_REQUEST_ID)
            }
        }
    }

    //=======================================================================
    // MARK - Application.ActivityLifecycleCallbacks Implementation
    //=======================================================================


    override fun onActivityPaused(activity: Activity) {
        if (activity == this.activity) {
            barcodeView?.pause()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity == this.activity) {
            barcodeView?.resume()
        }
    }

    override fun onActivityStarted(activity: Activity) {
        // not required
    }

    override fun onActivityDestroyed(activity: Activity) {
        // not required
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
        // not required
    }

    override fun onActivityStopped(activity: Activity) {
        // not required
    }

    override fun onActivityCreated(acitivy: Activity, bundle: Bundle?) {
        // not required
    }

}
