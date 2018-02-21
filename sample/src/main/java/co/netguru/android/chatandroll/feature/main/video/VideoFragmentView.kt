package co.netguru.android.chatandroll.feature.main.video

import co.netguru.android.chatandroll.feature.base.MvpView
import io.reactivex.Maybe

interface VideoFragmentView : MvpView {
    val remoteUuid: String?

    fun connectTo(uuid: String)
    fun showCamViews()
    fun saveFirebaiseKey(key: String)
    fun showPairPhones(PairedPhones : Map<String, String>)
    fun showStartRouletteView()
    fun disconnect()
    fun attachService()
    fun attachServiceWifi()
    fun showErrorWhileChoosingRandom()
    fun showNoOneAvailable()
    fun showLookingForPartnerMessage()
    fun showOtherPartyFinished()
    fun showConnectedMsg()
    fun showWillTryToRestartMsg()
    fun hideConnectButtonWithAnimation()
}