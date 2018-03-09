package co.netguru.android.chatandroll.feature.main.video

import co.netguru.android.chatandroll.data.model.DeviceInfoFirebase
import co.netguru.android.chatandroll.data.model.PairedDevice
import co.netguru.android.chatandroll.feature.base.MvpView

interface VideoFragmentView : MvpView {
    val remoteUuid: String?
    val adapter: PairedDevicesAdapter
    fun connectTo(uuid: String)
    fun showCamViews()
    fun showPairingConfirmationDialog(device: DeviceInfoFirebase)
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
    fun showPairingStatus()
    fun hidePairingStatus()
    fun showSetChildNameDialog()
    fun showParentChildButtons()
    fun showSnackbar(message:String)
    fun updateDevicesRecycler(devices: List<PairedDevice>)
    fun setParentButtonEnabled(isEnabled:Boolean)
    fun setChildButtonEnabled(isEnabled:Boolean)
    fun hideParentChildButtons()
}