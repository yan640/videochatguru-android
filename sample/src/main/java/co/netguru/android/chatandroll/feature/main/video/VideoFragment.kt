package co.netguru.android.chatandroll.feature.main.video

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.IBinder
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import co.netguru.android.chatandroll.R
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.areAllPermissionsGranted
import co.netguru.android.chatandroll.common.extension.startAppSettings
import co.netguru.android.chatandroll.data.SharedPreferences.SharedPreferences
import co.netguru.android.chatandroll.data.model.DeviceInfoFirebase
import co.netguru.android.chatandroll.feature.base.BaseMvpFragment
import co.netguru.android.chatandroll.webrtc.service.WebRtcService
import co.netguru.android.chatandroll.webrtc.service.WebRtcServiceListener
import kotlinx.android.synthetic.main.fragment_video.*
import org.jetbrains.anko.noButton
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.yesButton
import org.webrtc.PeerConnection
import timber.log.Timber
import java.util.*

@SuppressLint("Range")
class VideoFragment : BaseMvpFragment<VideoFragmentView, VideoFragmentPresenter>(), VideoFragmentView, WebRtcServiceListener {


    companion object {  // TODO  переделать на const для эффективности
        val TAG: String = VideoFragment::class.java.name

        fun newInstance() = VideoFragment()
        var CURRENT_WIFI_BSSID = ""

        private const val KEY_IN_CHAT = "key:in_chat"
        private const val CHECK_PERMISSIONS_AND_CONNECT_REQUEST_CODE = 1
        private val NECESSARY_PERMISSIONS = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.KILL_BACKGROUND_PROCESSES,
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.GET_ACCOUNTS)
        private const val CONNECT_BUTTON_ANIMATION_DURATION_MS = 500L
    }

    private lateinit var serviceConnection: ServiceConnection
    override fun showFirebaiseKey(key: String){

        Toast.makeText(context, "my room key: $key", Toast.LENGTH_LONG).show()
    }
    override fun getLayoutId() = R.layout.fragment_video

    override fun retrievePresenter() = App.getApplicationComponent(context).videoFragmentComponent().videoFragmentPresenter()

    var service: WebRtcService? = null

    override val remoteUuid
        get() = service?.getRemoteUuid()
    override fun saveFirebaiseKey(key: String){
        SharedPreferences.saveToken(context,key)
        App.CURRENT_DEVICE_UUID=key
        Toast.makeText(context, "key: $key", Toast.LENGTH_LONG).show()
    }

    private fun checkOrGetMyFirebaiseKey(){


        if (SharedPreferences.hasToken(context)) {
            App.CURRENT_DEVICE_UUID = SharedPreferences.getToken(context)
        } else {
            getPresenter().getKeyFromFirebase()

        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkOrGetMyFirebaiseKey()
        (buttonPanel.layoutParams as CoordinatorLayout.LayoutParams).behavior = MoveUpBehavior()
        (localVideoView.layoutParams as CoordinatorLayout.LayoutParams).behavior = MoveUpBehavior()
        activity.volumeControlStream = AudioManager.STREAM_VOICE_CALL

        if (savedInstanceState?.getBoolean(KEY_IN_CHAT) == true) {
            initAlreadyRunningConnection()
        }
        connectButton.setOnClickListener {
            // getPresenter().connect()
             getPresenter().startChildVideo()
        }
        pairButton.setOnClickListener {
            pairViaSameWifi() // TODO добавить альтернативный вариант подключения при отсутствии общего wifi
        }

        disconnectButton.setOnClickListener {
            getPresenter().disconnectByUser()
        }

        switchCameraButton.setOnClickListener {
            service?.switchCamera()
        }

        cameraEnabledToggle.setOnCheckedChangeListener { _, enabled ->
            service?.enableCamera(enabled)
        }

        microphoneEnabledToggle.setOnCheckedChangeListener { _, enabled ->
            service?.enableMicrophone(enabled)
        }
    }

    override fun onStart() {
        super.onStart()
        service?.hideBackgroundWorkWarning()
        checkPermissionsAndConnect()
    }

    override fun onStop() {
        super.onStop()
        if (!activity.isChangingConfigurations) {
            service?.showBackgroundWorkWarning()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        service?.let {
            it.detachViews()
            unbindService()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (remoteVideoView.visibility == View.VISIBLE) {
            outState.putBoolean(KEY_IN_CHAT, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!activity.isChangingConfigurations) disconnect()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) = when (requestCode) {
        CHECK_PERMISSIONS_AND_CONNECT_REQUEST_CODE -> {
            val grantResult = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (grantResult) {
                checkPermissionsAndConnect()
            } else {
                showNoPermissionsSnackbar()
            }
        }
        else -> {
            error("Unknown permission request code $requestCode")
        }
    }

    override fun showPairingStatus() {
        pairButton.isEnabled = false
        progressBar.visibility = View.VISIBLE

    }

    override fun hidePairingStatus() {
        pairButton.isEnabled = true
        progressBar.visibility = View.GONE

    }



    override fun showPairingConfirmationDialog(device: DeviceInfoFirebase) {
        alert("Pair with ${device.name}?") {  //TODO из res.strings
            yesButton { getPresenter().confirmPairnigAndWaitForOther(device) } //TODO добавить устройство в подтвержденные и отключить listener
            noButton {  }
        }.show()
    }

    override fun attachService() {
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                onWebRtcServiceConnected((iBinder as (WebRtcService.LocalBinder)).service)
                getPresenter().startConnection() //TODO
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                onWebRtcServiceDisconnected()
            }
        }
        startAndBindWebRTCService(serviceConnection)
    }

    override fun attachServiceWifi() {
        getPresenter().startWifiPair()
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                onWebRtcServiceConnected((iBinder as (WebRtcService.LocalBinder)).service)

            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                onWebRtcServiceDisconnected()
            }
        }
        startAndBindWebRTCService(serviceConnection)
    }


    override fun criticalWebRTCServiceException(throwable: Throwable) {
        unbindService()
        showSnackbarMessage(R.string.error_web_rtc_error, Snackbar.LENGTH_LONG)
        Timber.e(throwable, "Critical WebRTC service error")
    }

    override fun connectionStateChange(iceConnectionState: PeerConnection.IceConnectionState) {
        getPresenter().connectionStateChange(iceConnectionState)
    }

    override fun connectTo(uuid: String) {
        service?.offerDevice(uuid)
    }

    override fun disconnect() {
        service?.let {
            it.stopSelf()
            unbindService()
        }
    }

    private fun unbindService() {
        service?.let {
            it.detachServiceActionsListener()
            context.unbindService(serviceConnection)
            service = null
        }
    }

    override fun showCamViews() {
        buttonPanel.visibility = View.VISIBLE
        remoteVideoView.visibility = View.VISIBLE
        localVideoView.visibility = View.VISIBLE
        connectButton.visibility = View.GONE
        pairButton.visibility = View.GONE
    }

//    override fun showPairPhones(PairedPhones: Map<String, String>) {
//        Toast.makeText(context, "PairedPhones: ${PairedPhones}", Toast.LENGTH_LONG).show()
//        for (sdf in PairedPhones)
//            getPresenter().NewPaire(sdf.key)
//    }

    override fun showStartRouletteView() {
        buttonPanel.visibility = View.GONE
        remoteVideoView.visibility = View.GONE
        localVideoView.visibility = View.GONE
        connectButton.visibility = View.VISIBLE
        pairButton.visibility = View.VISIBLE
    }




    override fun showErrorWhileChoosingForPairing() {
        showSnackbarMessage(R.string.error_choosing_pairing_device, Snackbar.LENGTH_LONG)
    }


    override fun showNoOneAvailable() {
        showSnackbarMessage(R.string.msg_no_one_available, Snackbar.LENGTH_LONG)
    }

    override fun showLookingForPartnerMessage() {
        showSnackbarMessage(R.string.msg_looking_for_partner, Snackbar.LENGTH_SHORT)
    }

    override fun hideConnectButtonWithAnimation() {
        connectButton.animate().scaleX(0f).scaleY(0f)
                .setInterpolator(OvershootInterpolator())
                .setDuration(CONNECT_BUTTON_ANIMATION_DURATION_MS)
                .withStartAction { connectButton.isClickable = false }
                .withEndAction {
                    connectButton.isClickable = true
                    connectButton.visibility = View.GONE
                    connectButton.scaleX = 1f
                    connectButton.scaleY = 1f
                }
                .start()
    }

    override fun showOtherPartyFinished() {
        showSnackbarMessage(R.string.msg_other_party_finished, Snackbar.LENGTH_SHORT)
    }

    override fun showConnectedMsg() {
        showSnackbarMessage(R.string.msg_connected_to_other_party, Snackbar.LENGTH_LONG)
    }

    override fun showWillTryToRestartMsg() {
        showSnackbarMessage(R.string.msg_will_try_to_restart_msg, Snackbar.LENGTH_LONG)
    }

    private fun initAlreadyRunningConnection() {
        showCamViews()
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                onWebRtcServiceConnected((iBinder as (WebRtcService.LocalBinder)).service)
                getPresenter().listenForDisconnectOrders()
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                onWebRtcServiceDisconnected()
            }
        }
        startAndBindWebRTCService(serviceConnection)
    }

    private fun startAndBindWebRTCService(serviceConnection: ServiceConnection) {
        WebRtcService.startService(context)
        WebRtcService.bindService(context, serviceConnection)
    }

    private fun checkPermissionsAndConnect() {
        if (context.areAllPermissionsGranted(*NECESSARY_PERMISSIONS)) {

        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WAKE_LOCK,
                    Manifest.permission.KILL_BACKGROUND_PROCESSES,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.GET_ACCOUNTS

            ), CHECK_PERMISSIONS_AND_CONNECT_REQUEST_CODE)
        }
    }

    /**
     * Find pair in same wi-fi
     */
    private fun pairViaSameWifi() { // TODO перенести логику работы в Presenter
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val bssid = wifiManager.connectionInfo.bssid
        bssid?.let {
            CURRENT_WIFI_BSSID = it
            showLookingForPartnerMessage()
            getPresenter().startWifiPair()
            showPairingStatus() // TODO внести ограничение 60сек на pair после которого отменить поиск
        } ?: Toast.makeText(context, "Connect phones to one Wifi! ", Toast.LENGTH_LONG).show() //TODO заменить на snackBar
    }




    private fun showNoPermissionsSnackbar() {
        view?.let {
            Snackbar.make(it, R.string.msg_permissions, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_settings) {
                        try {
                            context.startAppSettings()
                        } catch (e: ActivityNotFoundException) {
                            showSnackbarMessage(R.string.error_permissions_couldnt_start_settings, Snackbar.LENGTH_LONG)
                        }
                    }
                    .show()
        }
    }

    private fun onWebRtcServiceConnected(service: WebRtcService) {
        Timber.d("Service connected")
        this.service = service
        service.attachLocalView(localVideoView)
        service.attachRemoteView(remoteVideoView)
        syncButtonsState(service)
        service.attachServiceActionsListener(webRtcServiceListener = this)
    }

    private fun syncButtonsState(service: WebRtcService) {
        cameraEnabledToggle.isChecked = service.isCameraEnabled()
        microphoneEnabledToggle.isChecked = service.isMicrophoneEnabled()
    }

    private fun onWebRtcServiceDisconnected() {
        Timber.d("Service disconnected")
    }
}