package co.netguru.android.chatandroll.feature.main.video

import android.content.Context
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.ChildEventAdded
import co.netguru.android.chatandroll.common.extension.ChildEventChanged
import co.netguru.android.chatandroll.common.extension.ChildEventRemoved
import co.netguru.android.chatandroll.common.util.RxUtils
import co.netguru.android.chatandroll.data.firebase.*
import co.netguru.android.chatandroll.data.model.DeviceInfoFirebase
import co.netguru.android.chatandroll.data.model.PairedDevice
import co.netguru.android.chatandroll.feature.base.BasePresenter
import com.google.firebase.database.DataSnapshot
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import org.webrtc.PeerConnection
import timber.log.Timber
import javax.inject.Inject


class VideoFragmentPresenter @Inject constructor(
        private val appContext: Context,
        private val firebaseSignalingOnline: FirebaseSignalingOnline,
        private val firebasePairedOnline: FirebasePairedOnline,
        private val firebaseSignalingDisconnect: FirebaseSignalingDisconnect,
        private val firebasePairingWifi: FirebasePairingWifi,
        private val firebaseNewRoom: FirebaseNewRoom
) : BasePresenter<VideoFragmentView>() {

    private val disposables = CompositeDisposable()
    private var closeDialogDisposable = Disposables.disposed()
    private var disposableForRetrieveKey: Disposable = Disposables.disposed()
    private var disconnectOrdersSubscription: Disposable = Disposables.disposed()
    private var listenForPairedDisposable: Disposable = Disposables.disposed()
    private val app: App by lazy { App.get(appContext) }
    private val listOfPairedDevices = mutableListOf<PairedDevice>()

    override fun detachView() {
        super.detachView()
    }

    fun GetKeyFromFirebase() {
        disposableForRetrieveKey = firebasePairedOnline.connect()
                .andThen(firebasePairedOnline.getMeNewKey())
                .compose(RxUtils.applySingleIoSchedulers())
                .subscribeBy(
                        onSuccess = {
                            Timber.d("Next $it")
                            App.CURRENT_DEVICE_UUID = it

                            getView()?.saveFirebaseDeviceKey(it)
                            disposableForRetrieveKey.dispose()
//                            getView()?.showCamViews()
//                            getView()?.connectTo(it)
                        },
                        onError = {
                            Timber.e(it, "Error while choosing random")
                            getView()?.showErrorWhileChoosingForPairing()
                            disposableForRetrieveKey.dispose()
                        }

                )

    }

    fun startChildVideo() {
        disposables += firebasePairedOnline.connect()
                .andThen(firebasePairedOnline.getRoomId())

                .compose(RxUtils.applySingleIoSchedulers())
                .subscribeBy(
                        onSuccess = {
                            Timber.d("Next $it")
                            //getView()?.showCamViews()
                            App.CURRENT_ROOM_ID =it
                            connect()
                            getView()?.showFirebaiseKey(it)
                        },
                        onError = {
                            Timber.e(it, "Error while choosing random")
                            getView()?.showErrorWhileChoosingForPairing()
                        }

                )
    }

    fun startConnection() {
        disposables += firebaseSignalingOnline.connect()
                .andThen(firebaseSignalingDisconnect.cleanDisconnectOrders()) // повторяется в след методе
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
                            getView()?.showErrorWhileChoosingForPairing()
                        },
                        onComplete = {
                            Timber.d("Done")
                            getView()?.showCamViews()
                            getView()?.showNoOneAvailable()
                        }
                )
    }


    /**
     * Add your device to FDB folder "wifi_pair_devices/YOUR_WIFI"
     * and listen for ready in that folder
     */
    fun startWifiPair() {
        disposables += firebasePairingWifi.connect() // check connection to FDB
                .andThen(firebasePairingWifi.addDeviceToPairingFolder())
                .andThen(firebasePairingWifi.listenForWaitingDevices())
                .compose(RxUtils.applyFlowableIoSchedulers())
                .subscribeBy(
                        onNext = {
                            Timber.d("Next $it")
                            getView()?.showPairingConfirmationDialog(it)

                            // При закрытии приложении на другом устройстве закрыть диалог
                            closeDialogOnOtherDeviceEscaped(it)

                        },
                        onError = {
                            Timber.e(it, "Error while finding ready for pairing devices")
                            getView()?.showErrorWhileChoosingForPairing()
                        },
                        onComplete = {
                            Timber.d("Done")
                            // getView()?.showCamViews()
                            getView()?.showNoOneAvailable()
                        }
                )
    }

    fun disposeListenerIfPaired(otherDevice: DeviceInfoFirebase) {
        listenForPairedDisposable = firebasePairingWifi
                .listenForOtherConfirmedPairing(otherDevice)
                .ignoreElement() // переобразует Maybe -> Completable
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onComplete = {
                            if (!closeDialogDisposable.isDisposed) {
                                closeDialogDisposable.dispose()
                            }

                        }
                )
    }

    /**
     * Закрывает диалог подтверждающий сопряжение, если закрыть приложение на
     * [otherDevice]
     */
    fun closeDialogOnOtherDeviceEscaped(otherDevice: DeviceInfoFirebase) {
        disposeListenerIfPaired(otherDevice)
        closeDialogDisposable = firebasePairingWifi
                .listenForDeviceEscaping(otherDevice)
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onComplete = {
                            getView()?.closePairingConfirmationDialog() // TODO  отключить листнера на поиск pairingDevices
                        }
                )
    }


    fun confirmPairingAndWaitForOther(otherDevice: DeviceInfoFirebase) {
        getView()?.hidePairingStatus()
        disposables.clear()
        if (!listenForPairedDisposable.isDisposed) {
            listenForPairedDisposable.dispose()
        }
        disposables += firebasePairingWifi.saveOtherDeviceAsPaired(otherDevice)
                .andThen(firebasePairingWifi.removerThisDeviceFromFolder())
                .andThen(firebasePairingWifi.listenForOtherConfirmedPairing(otherDevice))
                .doOnSuccess { firebasePairingWifi.saveDeviceToRoom(it.roomName) }
                .compose(RxUtils.applyMaybeIoSchedulers())
                .subscribeBy(
                        onSuccess = {
                            Timber.d("You and device ${it.name} paired! ")
                            getView()?.showParentChildButtons()// TODO вывести устойтво на экран для подключения
                        }
                )
    }

    fun stopPairing() {
        firebasePairingWifi.removerThisDeviceFromFolder()
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onError = { Timber.d(it.fillInStackTrace()) }
                )
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

    fun listenForDisconnectOrders() { // TODO будет disconn при любом добавленом в "should_disconnect
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


    fun getRoomNameForDevice() {
        disposables += firebasePairingWifi.listenForDeviceToRoom()
                .compose(RxUtils.applyFlowableIoSchedulers())
                .subscribeBy(
                        onNext = {
                            app.roomUUID = it
                        }
                )
    }

//    fun getPairedDevices(roomName: String) {
//        disposables += firebasePairingWifi.listenForPairedDevicesInRoom(roomName)
//                .compose(RxUtils.applyFlowableIoSchedulers())
//                .subscribeBy(
//                        onNext = {
//                            when (it) {
//                                is ChildEventAdded<DataSnapshot> ->
//                                    listOfPairedDevices += it.data
//                                            .getValue(PairedDevice::class.java) as PairedDevice
//                                is ChildEventRemoved<DataSnapshot> ->
//                                    listOfPairedDevices -= it.data
//                                            .getValue(PairedDevice::class.java) as PairedDevice
//                                is ChildEventChanged ->
//                                        listOfPairedDevices.refilter { device -> device.uuid == it.previousChildName }.get(0) = it.data.getValue(PairedDevice::class.java) as PairedDevice
//                            }
//                        }
//                )
//
//    }
}