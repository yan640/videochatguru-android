package co.netguru.android.chatandroll.feature.main.video

import co.netguru.android.chatandroll.data.model.DeviceInfoFirebase
import co.netguru.android.chatandroll.data.model.PairedDevice
import co.netguru.android.chatandroll.feature.base.MvpView

interface VideoFragmentView : MvpView {
    val remoteUuid: String?

    fun connectTo(uuid: String)
    fun showCamViews()
    fun showPairingConfirmationDialog(device: DeviceInfoFirebase)
    fun getAppContext():Context
    fun saveFirebaseDeviceKey(key: String)
    fun closePairingConfirmationDialog()
    //fun showPairPhones(PairedPhones : Map<String, String>)
    fun saveFirebaiseKey(key: String)
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
    fun showSetChildNameDialog(device: PairedDevice)
    fun showParentChildButtons()
    var roomUUID:String
}