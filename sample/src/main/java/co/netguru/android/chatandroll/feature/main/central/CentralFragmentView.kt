package co.netguru.android.chatandroll.feature.main.central

import co.netguru.android.chatandroll.data.model.PairedDevice
import co.netguru.android.chatandroll.data.model.PairingDevice
import co.netguru.android.chatandroll.data.model.Role
import co.netguru.android.chatandroll.feature.base.MvpView

interface CentralFragmentView:MvpView {


    //<editor-fold desc="Dialogs">
    fun showSetChildNameDialog(currentChildName: String? = null)
    fun showPairingConfirmationDialog(device: PairingDevice)
    fun closePairingConfirmationDialog()
    fun showPairingProgressDialog()
    fun closePairingProgressDialog()
    fun showChooseRoleDialog()

    //</editor-fold>


    //<editor-fold desc="Buttons">
    fun hideChildButtonWithAnimation()
    fun showParentChildButtons()
    fun hideParentChildButtons()
    fun setPairButtonText(text:String)
//    fun setParentButtonChecked(isChecked:Boolean)
//    fun setChildButtonChecked(isChecked:Boolean)
    //</editor-fold>


    //<editor-fold desc="Snackbar and messages">
    fun showErrorWhileChoosingForPairing()
    fun showSnackbarFromString(message:String)
    fun showSnackbarFromRes(stringRes: Int)
    fun showMessageDeviceStoppedPairing(deviceName:String)
    fun showNoOneAvailable()
    //</editor-fold>

    fun updateDevicesRecycler(devices: List<PairedDevice>)
    fun hideChildName()
    fun showChildName(childName:String)

    fun setNotPairedState()
    fun setPairingState()
    fun setConfirmationState(device: PairingDevice)
    fun setPairedState(role: Role, pairedDevices:List<PairedDevice>, childName: String)











}
