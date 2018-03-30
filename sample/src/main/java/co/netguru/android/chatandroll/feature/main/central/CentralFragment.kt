package co.netguru.android.chatandroll.feature.main.central

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.design.widget.Snackbar
import android.support.v7.widget.LinearLayoutManager
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import co.netguru.android.chatandroll.R
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.areAllPermissionsGranted
import co.netguru.android.chatandroll.common.extension.startAppSettings
import co.netguru.android.chatandroll.data.model.PairedDevice
import co.netguru.android.chatandroll.data.model.PairingDevice
import co.netguru.android.chatandroll.data.model.Role
import co.netguru.android.chatandroll.feature.base.BaseMvpFragment
import co.netguru.android.chatandroll.feature.main.video.PairedDevicesAdapter
import kotlinx.android.synthetic.main.fragment_central.*
import kotlinx.android.synthetic.main.fragment_video.*
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.indeterminateProgressDialog
import org.jetbrains.anko.support.v4.toast
import timber.log.Timber

/**
 * Центральный фрагмент, отображается на старте приложения
 * связан с [CentralFragmentPresenter]
 * Created by Gleb on 23.03.2018.
 */
@SuppressLint("Range")
class CentralFragment :
        BaseMvpFragment<CentralFragmentView, CentralFragmentPresenter>(), CentralFragmentView {


    companion object {
        val TAG: String = CentralFragment::class.java.name
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

    private var pairingConfirmationDialog: AlertDialog? = null
    private var chooseRoleDialog: AlertDialog? = null
    private var pairingProgeressDialog: AlertDialog? = null
    private lateinit var deviceToConfirm: PairingDevice
    private val colorUncheckedButton by lazy { resources.getColor(R.color.accent) }
    private val colorCheckedButton by lazy { resources.getColor(R.color.button_material_dark) } // TODO save in colorRes



    //<editor-fold desc="[BaseMvpFragment] methods">
    override fun retrievePresenter(): CentralFragmentPresenter =
            App.get(activity.application)
                    .getFragmentComponent()
                    .centralFragmentPresenter()

    override fun getLayoutId() = R.layout.fragment_central
    //</editor-fold>


    //<editor-fold desc="Fragment lifecycle events">
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        retainInstance = true
        btnPair.setOnClickListener { getPresenter().pairButtonClicked() }
        btnPairMore.setOnClickListener { getPresenter().pairMoreButtonClicked() }
        btnLeaveRoom.setOnClickListener { getPresenter().leaveRoomButtonClicked() }
        btnParentRole.setOnClickListener { getPresenter().parentRoleButtonClicked() }
        btnChildRole.setOnClickListener { getPresenter().childRoleButtonClicked() }
        btnNameOfChild.setOnClickListener { getPresenter().childNameButtonClicked() }
        recyclePairedDevices.layoutManager = LinearLayoutManager(activity.ctx)

    }


    override fun onStart() {
        super.onStart()
        checkPermissionsAndConnect()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onStop() {
        super.onStop()
        Timber.d("on Stop")
        pairingProgeressDialog?.dismiss()
        if (!activity.isChangingConfigurations) {
            App.get(activity.application).destroyFragmentComponent()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        getPresenter().onDestroyView()

    }


    override fun onDestroy() {
        super.onDestroy()
    }

    //</editor-fold>

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


    //<editor-fold desc="CentralFragmentView methods">

    //<editor-fold desc="Dialogs">

    override fun showPairingConfirmationDialog(device: PairingDevice) {
        pairingConfirmationDialog?.dismiss() // TODO заменить на очередь устойств на сопряжение
        pairingConfirmationDialog = alert("Pair with ${device.name}?") {
            //TODO из res.strings
            yesButton { getPresenter().confirmPairingAndWaitForOther(device) }
            noButton {
                it.cancel()
            }
            onCancelled {
                Timber.d("showPairingConfirmationDialog onCancel")
                it.dismiss()
                getPresenter().onPairingConfirmationCancel()
            }
        }.show()
    }

    override fun closePairingConfirmationDialog() { // TODO not used
        pairingConfirmationDialog?.dismiss()
    }

    override fun showSetChildNameDialog(currentChildName: String?) {
        Timber.d("showSetChildNameDialog")
        alert("Write the name of child") {
            customView {
                verticalLayout {
                    val childName = editText {
                        currentChildName?.let { setText(it) }
                        hint = "child name" // TODO to strRes
                        padding = dip(20)
                    }
                    yesButton {
                        if (childName.text.isNotBlank()) {
                            getPresenter().onChildNameChanged(childName.text.toString())
                            btnNameOfChild.text = childName.text.toString()  // TODO через презентер
                        } else
                            toast("Child name is blank!") // TODO to strRes
                    }
                }
            }
        }.show()
    }

    override fun showChooseRoleDialog() {  // TODO not used
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


    override fun closePairingProgressDialog() {
        pairingProgeressDialog?.dismiss()
    }
    //</editor-fold>


    //<editor-fold desc="mvpView State">

    override fun setNotPairedState() {
        pairingProgeressDialog?.dismiss()  // TODO ??
        pairingConfirmationDialog?.dismiss()
        btnPair.visibility = View.VISIBLE //TODO move to center
        btnChildRole.visibility = View.GONE
        btnParentRole.visibility = View.GONE
        btnNameOfChild.visibility = View.GONE
        btnPairMore.visibility = View.GONE
        btnLeaveRoom.visibility = View.GONE
        recyclePairedDevices.visibility = View.GONE
    }

    override fun setPairingState() {
        //setNotPairedState() //TODO провороте экрана проподают уже соед. устр-ва
        showPairingProgressDialog()
    }


    override fun setConfirmationState(device: PairingDevice) {
        pairingProgeressDialog?.dismiss()
       // setNotPairedState()   //TODO провороте экрана проподают уже соед. устр-ва
        showPairingConfirmationDialog(device)
    }


    override fun setPairedState(role: Role, pairedDevices: List<PairedDevice>, childName: String) {
        pairingProgeressDialog?.dismiss()
        pairingConfirmationDialog?.dismiss()
        btnPair.visibility = View.GONE  // TODO семестить вниз
        btnPairMore.visibility = View.VISIBLE
        btnChildRole.visibility = View.VISIBLE
        btnParentRole.visibility = View.VISIBLE
        btnLeaveRoom.visibility = View.VISIBLE
        when (role) {
            Role.ROLE_NOT_SET -> {
                btnNameOfChild.visibility = View.GONE
                recyclePairedDevices.visibility = View.VISIBLE
                updateDevicesRecycler(pairedDevices)
                btnChildRole.backgroundColor = colorUncheckedButton
                btnParentRole.backgroundColor = colorUncheckedButton
            }
            Role.PARENT -> {
                btnNameOfChild.visibility = View.GONE
                recyclePairedDevices.visibility = View.VISIBLE
                updateDevicesRecycler(pairedDevices)

                btnChildRole.backgroundColor = colorCheckedButton
                btnParentRole.backgroundColor = colorUncheckedButton
            }
            Role.CHILD -> {
                btnNameOfChild.visibility = View.VISIBLE
                recyclePairedDevices.visibility = View.GONE
                btnParentRole.backgroundColor = colorCheckedButton
                btnChildRole.backgroundColor = colorUncheckedButton
                btnNameOfChild.text = childName
            }
        }
    }


    //</editor-fold>


    //<editor-fold desc="Buttons">

    override fun setPairButtonText(text: String) {
        btnPair.text = text
    }


    override fun hideChildButtonWithAnimation() {
        btnChildRole.animate().scaleX(0f).scaleY(0f)
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


    override fun hideChildName() {
        btnNameOfChild.visibility = View.GONE
    }

    override fun showChildName(childName: String) {
        btnNameOfChild.text = childName
        btnNameOfChild.visibility = View.VISIBLE
    }


    override fun showParentChildButtons() {
        btnChildRole.visibility = View.VISIBLE
        btnParentRole.visibility = View.VISIBLE
    }

    override fun hideParentChildButtons() {
        btnChildRole.visibility = View.GONE
        btnParentRole.visibility = View.GONE
    }
    //</editor-fold>


    //<editor-fold desc="Snackbar and messages">

    @SuppressLint("Range")
    override fun showSnackbarFromString(message: String) {
        showSnackbarMessage(message, Snackbar.LENGTH_LONG)
    }

    override fun showSnackbarFromRes(@StringRes stringRes: Int) {
        showSnackbarMessage(stringRes, Snackbar.LENGTH_LONG)
    }


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
        if (devices.isNotEmpty()) {
            Timber.d("is NOT Empty")
            recyclePairedDevices.visibility = View.VISIBLE
            tvEmptyRecycle.visibility = View.GONE
            val adapter = PairedDevicesAdapter(devices, { showSnackbarFromString("Clicked ${it.deviceName}") })
            recyclePairedDevices.adapter = adapter
        }
        else {
            Timber.d("is Empty")
            recyclePairedDevices.visibility = View.GONE
            tvEmptyRecycle.visibility = View.VISIBLE
        }
    }
    //</editor-fold>


    //</editor-fold>


}