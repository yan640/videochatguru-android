package co.netguru.android.chatandroll.feature.main.video

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.support.annotation.StringRes
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.v7.widget.LinearLayoutManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import co.netguru.android.chatandroll.R
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.areAllPermissionsGranted
import co.netguru.android.chatandroll.common.extension.startAppSettings
import co.netguru.android.chatandroll.data.SharedPreferences.SharedPreferences
import co.netguru.android.chatandroll.data.model.PairedDevice
import co.netguru.android.chatandroll.data.model.PairingDevice
import co.netguru.android.chatandroll.feature.base.BaseMvpFragment
import co.netguru.android.chatandroll.webrtc.service.WebRtcService
import co.netguru.android.chatandroll.webrtc.service.WebRtcServiceListener
import kotlinx.android.synthetic.main.fragment_video.*
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.indeterminateProgressDialog
import org.jetbrains.anko.support.v4.toast
import org.webrtc.PeerConnection
import timber.log.Timber

@SuppressLint("Range")
class VideoFragment : BaseMvpFragment<VideoFragmentView, VideoFragmentPresenter>(), VideoFragmentView, WebRtcServiceListener {


    companion object {  // TODO  переделать на const для эффективности
        val TAG: String = VideoFragment::class.java.name
        fun newInstance(): VideoFragment {
            Timber.d("new Instance = VideoFragment")
            return VideoFragment()
        }


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

    private var pairingConfirmationDialog: AlertDialog? = null
    private var chooseRoleDialog: AlertDialog? = null
    private var pairingProgeressDialog: AlertDialog? = null
    override fun getLayoutId() = R.layout.fragment_video


    var service: WebRtcService? = null

    override val remoteUuid
        get() = service?.getRemoteUuid()  // TODO проверить где используется

    override lateinit var adapter: PairedDevicesAdapter

    override fun retrievePresenter(): VideoFragmentPresenter = App
            .getApplicationComponent(context)
            .videoFragmentComponent()
            .videoFragmentPresenter()


    override fun saveFirebaseDeviceKey(key: String) {
        SharedPreferences.saveToken(context, key)
        App.THIS_DEVICE_UUID = key
        Toast.makeText(context, "key: $key", Toast.LENGTH_LONG).show()
    }


    override fun showFirebaiseKey(key: String) {
        Toast.makeText(context, "my room key: $key", Toast.LENGTH_LONG).show()
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

    override fun connectionStateChange(iceConnectionState: PeerConnection.IceConnectionState) {
        getPresenter().connectionStateChange(iceConnectionState)
    }


    //<editor-fold desc="Fragment Lifecycle">
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (buttonPanel.layoutParams as CoordinatorLayout.LayoutParams).behavior = MoveUpBehavior()
        (localVideoView.layoutParams as CoordinatorLayout.LayoutParams).behavior = MoveUpBehavior()
        activity.volumeControlStream = AudioManager.STREAM_VOICE_CALL

        getPresenter().onViewCreated()

        if (savedInstanceState?.getBoolean(KEY_IN_CHAT) == true) {
            initAlreadyRunningConnection()
        }
        connectButton.setOnClickListener {
            //getPresenter().connect()
            getPresenter().startChildVideo()
        }
        pairButton.setOnClickListener {
            getPresenter().pairButtonClicked()
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
        devicesRecycler.layoutManager = LinearLayoutManager(activity.ctx)
        parenRoleButton.setOnClickListener { getPresenter().parentRoleButtonClicked() }
        childRoleButton.setOnClickListener { getPresenter().childRoleButtonClicked() }
        //childNameButton.setOnClickListener { getPresenter().childNameButtonClicked() }
    }

    override fun onStart() {
        super.onStart()
        service?.hideBackgroundWorkWarning()
        checkPermissionsAndConnect()
        Timber.d("onStart = $this")

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        Timber.d("onCreate = $this")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Timber.d("onCreateView = $this")
        return super.onCreateView(inflater, container, savedInstanceState)
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
        pairingProgeressDialog?.cancel()
        pairingConfirmationDialog?.cancel()
        getPresenter().onDestroyView()
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


    //</editor-fold>


    //<editor-fold desc="Dialogs">
    override fun showPairingConfirmationDialog(device: PairingDevice) {
        pairingConfirmationDialog?.cancel() // TODO заменить на очередь устойств на сопряжение
        pairingConfirmationDialog = alert("Pair with ${device.name}?") {
            //TODO из res.strings
            yesButton { getPresenter().confirmPairingAndWaitForOther(device) }
            noButton {
                it.cancel()
                // getPresenter().stopPairing()
            }
            onCancelled {
                it.dismiss()
                getPresenter().stopPairing()
            }
        }.show()
    }

    override fun closePairingConfirmationDialog() {
        pairingConfirmationDialog?.cancel()
    }

    override fun showSetChildNameDialog(currentChildName: String?) {
        alert("Write the name of child") {
            customView {
                verticalLayout {
                    val childName = editText {
                        currentChildName?.let { setText(it) }
                        hint = "child name"
                        padding = dip(20)
                    }
                    yesButton {
                        if (childName.text.isNotBlank()) {
                            getPresenter().setChildName(childName.text.toString())
                           // childNameButton.text = childName.text.toString()  // TODO через презентер
                        } else
                            toast("Child name is blank!")
                    }
                }
            }
        }.show()

    }

    override fun showChooseRoleDialog() {
        if (chooseRoleDialog?.isShowing != true) {
            chooseRoleDialog = alert("you can easly change your role any time at the bottom buttons") {
                title = "Parent or child?"
                customView {
                    linearLayout {
                        gravity = Gravity.CENTER_HORIZONTAL
                        button("Child") {
                            padding = dip(16)
                            setOnClickListener {
                                getPresenter().childRoleButtonClicked()
                                chooseRoleDialog?.cancel()
                            }
                        }
                        button("Parent") {
                            padding = dip(16)
                            setOnClickListener {
                                getPresenter().parentRoleButtonClicked()
                                chooseRoleDialog?.cancel()
                            }
                        }
                    }
                }
            }.show()
        }
    }

    override fun showPairingProgressDialog() {
        pairingProgeressDialog = indeterminateProgressDialog(
                "Looking for pairing device...")
        pairingProgeressDialog?.setOnCancelListener {
            it.dismiss()
            getPresenter().stopPairing()
        }
        pairingProgeressDialog?.show()
    }

    override fun closePairingProgessDialog() {
        pairingProgeressDialog?.dismiss()
    }
    //</editor-fold>


    //<editor-fold desc="Buttons">

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


    override fun setParentButtonChecked(isChecked: Boolean) {
        if (isChecked)
            parenRoleButton.backgroundColor = resources.getColor(R.color.material_deep_teal_500)
        else
            parenRoleButton.backgroundColor = resources.getColor(R.color.primary)
    }

    override fun setChildButtonChecked(isChecked: Boolean) {
        if (isChecked)
            childRoleButton.backgroundColor = resources.getColor(R.color.material_deep_teal_500)
        else
            childRoleButton.backgroundColor = resources.getColor(R.color.primary)
    }

    override fun setPairButtonText(text: String) {
        pairButton.text = text
    }

    override fun hideChildName() {
        childNameButton.visibility = View.GONE
    }

    override fun showChildName(childName: String) {
        childNameButton.text = childName
        childNameButton.visibility = View.VISIBLE
    }

    override fun showSnackbarFromString(message: String) {
        showSnackbarMessage(message, Snackbar.LENGTH_LONG)
    }

    override fun showSnackbarFromRes(@StringRes stringRes: Int) {
        showSnackbarMessage(stringRes, Snackbar.LENGTH_LONG)
    }

    override fun showParentChildButtons() {
        childRoleButton.visibility = View.VISIBLE
        parenRoleButton.visibility = View.VISIBLE
    }

    override fun hideParentChildButtons() {
        childRoleButton.visibility = View.GONE
        parenRoleButton.visibility = View.GONE
    }


    override fun showCamViews() {
        buttonPanel.visibility = View.VISIBLE
        remoteVideoView.visibility = View.VISIBLE
        localVideoView.visibility = View.VISIBLE
        connectButton.visibility = View.GONE
        pairButton.visibility = View.GONE
    }


    override fun showStartRouletteView() {
        buttonPanel.visibility = View.GONE
        remoteVideoView.visibility = View.GONE
        localVideoView.visibility = View.GONE
        connectButton.visibility = View.VISIBLE
        pairButton.visibility = View.VISIBLE
    }

    private fun syncButtonsState(service: WebRtcService) {
        cameraEnabledToggle.isChecked = service.isCameraEnabled()
        microphoneEnabledToggle.isChecked = service.isMicrophoneEnabled()
    }

    //</editor-fold>


    //<editor-fold desc="Services">

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
        TODO("attachServiceWifi not impemented, I think it's useless")
        // getPresenter().startWifiPairing()
//        serviceConnection = object : ServiceConnection {
//            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
//                onWebRtcServiceConnected((iBinder as (WebRtcService.LocalBinder)).service)
//
//            }
//
//            override fun onServiceDisconnected(componentName: ComponentName) {
//                onWebRtcServiceDisconnected()
//            }
//        }
//        startAndBindWebRTCService(serviceConnection)
    }


    override fun criticalWebRTCServiceException(throwable: Throwable) {
        unbindService()
        showSnackbarMessage(R.string.error_web_rtc_error, Snackbar.LENGTH_LONG)
        Timber.e(throwable, "Critical WebRTC service error")
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

    private fun onWebRtcServiceConnected(service: WebRtcService) {
        Timber.d("Service connected")
        this.service = service
        service.attachLocalView(localVideoView)
        service.attachRemoteView(remoteVideoView)
        syncButtonsState(service)
        service.attachServiceActionsListener(webRtcServiceListener = this)
    }


    private fun onWebRtcServiceDisconnected() {
        Timber.d("Service disconnected")
    }

    //</editor-fold>


    //<editor-fold desc="SnackBars">
    override fun showErrorWhileChoosingForPairing() {
        showSnackbarMessage(R.string.error_choosing_pairing_device, Snackbar.LENGTH_LONG)
    }

    override fun showMessageDeviceStoppedPairing(deviceName: String) {
        val message = getString(R.string.the_device_has_stopped_pairing, deviceName)
        showSnackbarMessage(message, Snackbar.LENGTH_LONG)
    }

    override fun showNoOneAvailable() {
        showSnackbarMessage(R.string.msg_no_one_available, Snackbar.LENGTH_LONG)
    }

    override fun showLookingForPartnerMessage() {
        showSnackbarMessage(R.string.msg_looking_for_partner, Snackbar.LENGTH_SHORT)
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


    //</editor-fold>


    //<editor-fold desc="Recycler">
    override fun updateDevicesRecycler(devices: List<PairedDevice>) {
        val adapter = PairedDevicesAdapter(devices, { showSnackbarFromString("Clicked ${it.deviceName}") })
        devicesRecycler.adapter = adapter
    }
    //</editor-fold>
}