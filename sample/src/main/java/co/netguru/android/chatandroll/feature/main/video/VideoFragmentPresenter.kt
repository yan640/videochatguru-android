package co.netguru.android.chatandroll.feature.main.video

import co.netguru.android.chatandroll.common.util.RxUtils
import co.netguru.android.chatandroll.data.firebase.*
import co.netguru.android.chatandroll.feature.base.BasePresenter
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import org.webrtc.PeerConnection
import timber.log.Timber
import javax.inject.Inject


class VideoFragmentPresenter @Inject constructor(
        private val firebaseSignalingOnline: FirebaseSignalingOnline,
        private val firebasePairedOnline: FirebasePairedOnline,
        private val firebaseSignalingDisconnect: FirebaseSignalingDisconnect,
        private val firebasePairingWifi: FirebasePairingWifi,
        private val firebaseNewRoom: FirebaseNewRoom
) : BasePresenter<VideoFragmentView>() {

    private val disposables = CompositeDisposable()
    private var disconnectOrdersSubscription: Disposable = Disposables.disposed()

    override fun detachView() {
        super.detachView()
    }

    fun startChildVideo() {
        disposables += firebasePairedOnline.connect()
                .andThen(firebaseSignalingDisconnect.cleanDisconnectOrders())
                .doOnComplete { listenForDisconnectOrders() }
                .andThen(firebasePairedOnline.setOnlineAndRetrieveRandomDevice())
                .compose(RxUtils.applyMaybeIoSchedulers())
                .subscribeBy(
                        onSuccess = {
                            Timber.d("Next $it")
                            getView()?.showCamViews()
                            getView()?.connectTo(it)
                        },
                        onError = {
                            Timber.e(it, "Error while choosing random")
                            getView()?.showErrorWhileChoosingRandom()
                        },
                        onComplete = {
                            Timber.d("Done")
                            getView()?.showCamViews()
                            getView()?.showNoOneAvailable()
                        }
                )

    }

    fun startRoulette() {
        disposables += firebaseSignalingOnline.connect()
                .andThen(firebaseSignalingDisconnect.cleanDisconnectOrders())
                .doOnComplete { listenForDisconnectOrders() }
                .andThen(firebaseSignalingOnline.setOnlineAndRetrieveRandomDevice())
                .compose(RxUtils.applyMaybeIoSchedulers())
                .subscribeBy(
                        onSuccess = {
                            Timber.d("Next $it")
                            getView()?.showCamViews()
                            getView()?.connectTo(it)
                        },
                        onError = {
                            Timber.e(it, "Error while choosing random")
                            getView()?.showErrorWhileChoosingRandom()
                        },
                        onComplete = {
                            Timber.d("Done")
                            getView()?.showCamViews()
                            getView()?.showNoOneAvailable()
                        }
                )

    }


    fun startWifiPair( ) {
        disposables += firebasePairingWifi.connect()
                .doOnComplete { listenForDisconnectOrders() }
                .andThen(firebasePairingWifi.setOnlineAndRetrieveRandomDevice( ))
                .compose(RxUtils.applyMaybeIoSchedulers())
                .subscribeBy(
                        onSuccess = {
                            Timber.d("Next $it")
                            getView()?.showPairPhones(it)

                        },
                        onError = {
                            Timber.e(it, "Error while choosing random")
                            getView()?.showErrorWhileChoosingRandom()
                        },
                        onComplete = {
                            Timber.d("Done")
                            // getView()?.showCamViews()
                            getView()?.showNoOneAvailable()
                        }
                )

    }


    fun NewPaire(NewPhone : String ) {
        firebaseNewRoom.connect()
        firebaseNewRoom.setOnlineAndRetrieveRandomDevice(NewPhone )
        disconnect()
//        disposables += firebaseNewRoom.connect()
//                .doOnComplete(firebaseNewRoom.setOnlineAndRetrieveRandomDevice(NewPhone ))
//                .compose(RxUtils.applyMaybeIoSchedulers())
//                .subscribeBy(
//                        onSuccess = {
//                            Timber.d("Next $it")
//                            getView()?.showPairPhones(it)
//
//                        },
//                        onError = {
//                            Timber.e(it, "Error while choosing random")
//                            getView()?.showErrorWhileChoosingRandom()
//                        },
//                        onComplete = {
//                            Timber.d("Done")
//                            // getView()?.showCamViews()
//                            getView()?.showNoOneAvailable()
//                        }
//                )

    }


    fun listenForIncomePairing() {
        disconnectOrdersSubscription = firebaseSignalingDisconnect.cleanDisconnectOrders()
                .andThen(firebaseSignalingDisconnect.listenForDisconnectOrders())
                .compose(RxUtils.applyFlowableIoSchedulers())
                .subscribeBy(
                        onNext = {
                            Timber.d("Disconnect order")
                            getView()?.showOtherPartyFinished()
                            disconnect()
                        }
                )
    }

    fun listenForDisconnectOrders() {
        disconnectOrdersSubscription = firebaseSignalingDisconnect.cleanDisconnectOrders()
                .andThen(firebaseSignalingDisconnect.listenForDisconnectOrders())
                .compose(RxUtils.applyFlowableIoSchedulers())
                .subscribeBy(
                        onNext = {
                            Timber.d("Disconnect order")
                            getView()?.showOtherPartyFinished()
                            disconnect()
                        }
                )
    }

    private fun disconnect() {
        disposables += firebaseSignalingOnline.disconnect()
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onError = {
                            Timber.d(it)
                        },
                        onComplete = {
                            disconnectOrdersSubscription.dispose()
                            getView()?.disconnect()
                            getView()?.showStartRouletteView()
                        }
                )

    }

    fun connect() = getView()?.run {
        attachService()
        showLookingForPartnerMessage()
        hideConnectButtonWithAnimation()
    }

    fun createWifiGroup() = getView()?.run {
        attachServiceWifi()
        showLookingForPartnerMessage()
        //hideConnectButtonWithAnimation()
    }

    fun disconnectByUser() {
        val remoteUuid = getView()?.remoteUuid
        if (remoteUuid != null) {
            disposables += firebaseSignalingDisconnect.sendDisconnectOrderToOtherParty(remoteUuid)
                    .compose(RxUtils.applyCompletableIoSchedulers())
                    .subscribeBy(
                            onComplete = {
                                disconnect()
                            }
                    )
        } else {
            disconnect()
        }

    }

    fun connectionStateChange(iceConnectionState: PeerConnection.IceConnectionState) {
        Timber.d("Ice connection state changed: $iceConnectionState")
        when (iceConnectionState) {
            PeerConnection.IceConnectionState.CONNECTED -> {
                getView()?.showConnectedMsg()
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                getView()?.showWillTryToRestartMsg()
            }
            else -> {
                //no-op for now - could show or hide progress bars or messages on given event
            }
        }
    }
}