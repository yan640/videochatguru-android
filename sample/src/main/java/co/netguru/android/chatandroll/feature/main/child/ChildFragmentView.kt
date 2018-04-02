package co.netguru.android.chatandroll.feature.main.video

import co.netguru.android.chatandroll.data.model.Child
import co.netguru.android.chatandroll.data.model.PairingDevice
import co.netguru.android.chatandroll.feature.base.MvpView
import co.netguru.android.chatandroll.feature.main.child.ChildAdapter

interface ChildFragmentView : MvpView {
    val remoteUuid: String?
    val adapter: ChildAdapter
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
    fun showPairingProgressDialog()
    fun closePairingProgessDialog()
    fun showSetChildNameDialog(currentChildName: String? = null)
    fun showSnackbarFromString(message: String)
    fun showSnackbarFromRes(stringRes: Int)
    fun showMessageDeviceStoppedPairing(deviceName: String)
    fun updateChildRecycler(childrens: List<Child>)

}