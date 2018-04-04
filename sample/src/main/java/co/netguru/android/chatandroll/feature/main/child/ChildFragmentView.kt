package co.netguru.android.chatandroll.feature.main.video

import co.netguru.android.chatandroll.data.model.Child
import co.netguru.android.chatandroll.feature.base.MvpView
import co.netguru.android.chatandroll.feature.main.child.ChildAdapter

interface ChildFragmentView : MvpView {
    val remoteUuid: String?
    val adapter: ChildAdapter
    fun connectTo(uuid: String)
    fun showCamViews()
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
    fun showSetChildNameDialog(currentChildName: String? = null)
    fun showSnackbarFromString(message: String)
    fun showSnackbarFromRes(stringRes: Int)
    fun showMessageDeviceStoppedPairing(deviceName: String)
    fun updateChildRecycler(children: List<Child>)

}