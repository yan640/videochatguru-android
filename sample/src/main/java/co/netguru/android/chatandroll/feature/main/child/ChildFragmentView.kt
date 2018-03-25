package co.netguru.android.chatandroll.feature.main.video

import co.netguru.android.chatandroll.data.model.PairedDevice
import co.netguru.android.chatandroll.data.model.PairingDevice
import co.netguru.android.chatandroll.feature.base.MvpView

interface ChildFragmentView : MvpView {
    val remoteUuid: String?
    val adapter: PairedDevicesAdapter
    fun connectTo(uuid: String)
    fun showCamViews()
    fun showPairingConfirmationDialog(device: PairingDevice)
    fun saveFirebaseDeviceKey(key: String)
    fun closePairingConfirmationDialog()
    fun showFirebaiseKey(key: String)
    fun showStartRouletteView()
    fun disconnect()
    fun attachService()
    fun attachServiceWifi()
    fun showErrorWhileChoosingForPairing()
    fun showNoOneAvailable()
    fun showLookingForPartnerMessage()
    fun showOtherPartyFinished()
    fun showConnectedMsg()
    fun showWillTryToRestartMsg()
    fun hideConnectButtonWithAnimation()
    fun showPairingProgressDialog()
    fun closePairingProgessDialog()
    fun showSetChildNameDialog(currentChildName: String? = null)
    fun showParentChildButtons()
    fun showSnackbarFromString(message:String)
    fun showSnackbarFromRes(stringRes: Int)
    fun showMessageDeviceStoppedPairing(deviceName:String)
    fun updateDevicesRecycler(devices: List<PairedDevice>)
    fun setParentButtonChecked(isChecked:Boolean)
    fun setChildButtonChecked(isChecked:Boolean)
    fun hideParentChildButtons()
    fun hideChildName()
    fun showChildName(childName:String)
    fun showChooseRoleDialog()
    fun setPairButtonText(text:String)
}