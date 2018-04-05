package co.netguru.android.chatandroll.common.extension

import org.webrtc.CameraVideoCapturer
import timber.log.Timber





private val cameraSwitchHandler = object : CameraVideoCapturer.CameraSwitchHandler {

    override fun onCameraSwitchDone(isFront: Boolean) {
        Timber.d("WebRtcServiceController", "camera switched to Front: $isFront" )
    }

    override fun onCameraSwitchError(msg: String?) {
        Timber.d("WebRtcServiceController", "failed to switch camera " + msg)
    }
}