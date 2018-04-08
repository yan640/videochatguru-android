package co.netguru.android.chatandroll.feature.main.video

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.support.annotation.StringRes
import android.support.design.widget.Snackbar
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import co.netguru.android.chatandroll.R
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.areAllPermissionsGranted
import co.netguru.android.chatandroll.common.extension.startAppSettings
import co.netguru.android.chatandroll.data.model.Child
import co.netguru.android.chatandroll.feature.base.BaseMvpFragment
import co.netguru.android.chatandroll.feature.main.child.ChildAdapter
import co.netguru.android.chatandroll.feature.main.services.MonitorService
import co.netguru.android.chatandroll.webrtc.service.WebRtcService
import co.netguru.android.chatandroll.webrtc.service.WebRtcServiceListener
import kotlinx.android.synthetic.main.fragment_child.*
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.toast
import org.webrtc.PeerConnection
import timber.log.Timber




@SuppressLint("Range")
class ChildFragment : BaseMvpFragment<ChildFragmentView, ChildFragmentPresenter>(), ChildFragmentView, WebRtcServiceListener {


    companion object {  // TODO  переделать на const для эффективности
        val TAG: String = ChildFragment::class.java.name
        fun newInstance() = ChildFragment()


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

    }

    private lateinit var serviceConnection: ServiceConnection

    private   var volume_changed = "volume_changed"
    override fun getLayoutId() = R.layout.fragment_child
    //private val broadcastReceiver: BroadcastReceiver? = null

    var service: WebRtcService? = null

    override val remoteUuid
        get() = service?.getRemoteUuid()  // TODO проверить где используется

    override lateinit var adapter: ChildAdapter

    override fun retrievePresenter() = App
            .getApplicationComponent(context)
            .childFragmentComponent()
            .childFragmentPresenter()




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
        //(buttonPanelChild.layoutParams as CoordinatorLayout.LayoutParams).behavior = MoveUpBehavior()
       // (localVideoViewChild.layoutParams as CoordinatorLayout.LayoutParams).behavior = MoveUpBehavior()
        activity.volumeControlStream = AudioManager.STREAM_VOICE_CALL
        LocalBroadcastManager.getInstance(context)
                .registerReceiver(broadcastReceiver,   IntentFilter("location_update"))


        getPresenter().onViewCreated()
        //getPresenter().startChildVideo()

        if (savedInstanceState?.getBoolean(KEY_IN_CHAT) == true) {
            initAlreadyRunningConnection()
        }
//        connectButton.setOnClickListener {
//            //getPresenter().connect()
//            getPresenter().startChildVideo()
//        }

        start_monitor.setOnClickListener{


            val intent = Intent(context, MonitorService::class.java)
            intent.putExtra("sensitivity", sensitivity_seekbar.progress )

            if (context != null) {
                context.startService(intent)
            }
        }

        disconnectButtonChild.setOnClickListener {

            //getPresenter().disconnectByUser()
            //          getActivity().finish()
           activity.finish()
//            onDestroy()

        }

//        switchCameraButtonChild.setOnClickListener {
//
//            switchCamera()
//            //.doAsyncResult {  }
//        }

        flashEnabledToggleChild.setOnCheckedChangeListener { _, enabled ->
           // service?.enableCamera(enabled)
        }

        cameraSwitchToggleChild.setOnCheckedChangeListener { _, enabled ->
            service?.switchCamera(getPresenter().cameraSwitchHandler)
        }
        childRecycler.layoutManager = LinearLayoutManager(activity.ctx)

        //        parenRoleButton.setOnClickListener { getPresenter().parentRoleButtonClicked() }
//        childRoleButton.setOnClickListener { getPresenter().childRoleButtonClicked() }
        //childNameButton.setOnClickListener { getPresenter().childNameButtonClicked() }
    }



    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            showSnackbarMessage(intent?.getExtras()?.get("currentVolume").toString(), Snackbar.LENGTH_SHORT)

        }
    }



    override fun onStart() {
        super.onStart()
        service?.hideBackgroundWorkWarning()
        checkPermissionsAndConnect()

    }




    override fun onStop() {
        super.onStop()
        getPresenter().disconnectChild()
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


        getPresenter().onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (remoteVideoViewChild.visibility == View.VISIBLE) {
            outState.putBoolean(KEY_IN_CHAT, true)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        if (!activity.isChangingConfigurations) disconnect()
    }


    //</editor-fold>



    //<editor-fold desc="Dialogs">




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
                                    //childNameButton.text = childName.text.toString()  // TODO через презентер
                        } else
                            toast("Child name is blank!")
                        showSetChildNameDialog()
                    }
                }
            }
        }.show()

    }




    override fun showSnackbarFromString(message: String) {
        showSnackbarMessage(message, Snackbar.LENGTH_LONG)
    }

    override fun showSnackbarFromRes(@StringRes stringRes: Int) {
        showSnackbarMessage(stringRes, Snackbar.LENGTH_LONG)
    }




    override fun showCamViews() {
     //   buttonPanelChild.visibility = View.VISIBLE
        remoteVideoViewChild.visibility = View.GONE
        localVideoViewChild.visibility = View.VISIBLE

    }


    override fun showStartRouletteView() {
        //buttonPanelChild.visibility = View.GONE
        remoteVideoViewChild.visibility = View.GONE
        localVideoViewChild.visibility = View.GONE


    }

//    private fun syncButtonsState(service: WebRtcService) {
//        cameraEnabledToggleChild.isChecked = service.isCameraEnabled()
//        microphoneEnabledToggleChild.isChecked = service.isMicrophoneEnabled()
//    }

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
        service.attachLocalView(localVideoViewChild)
        service.attachRemoteView(remoteVideoViewChild)
        //syncButtonsState(service)
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

    override fun showMessageDeviceStoppedPairing(deviceName:String) {
        val message = getString(R.string.the_device_has_stopped_pairing,deviceName)
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

    private fun usePickedChild(child: Child) {
        childRecycler.visibility= View.GONE
        getPresenter().setChildOnline(child.key)
    }


    //</editor-fold>



    //<editor-fold desc="Recycler">
    override fun updateChildRecycler(children: List<Child>) {
       val adapter = ChildAdapter(children, { usePickedChild(it) })
        childRecycler.adapter = adapter
    }
    //</editor-fold>
}