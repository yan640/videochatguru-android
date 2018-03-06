package co.netguru.android.chatandroll.feature.main.video

import android.content.Context
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.ChildEvent
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
import org.jetbrains.anko.collections.forEachWithIndex
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
    private var pairedDisposable = Disposables.disposed()

    private val pairingDisposables = CompositeDisposable()
    private var disposableForRetrieveKey: Disposable = Disposables.disposed()
    private var disconnectOrdersSubscription: Disposable = Disposables.disposed()

    private val app: App by lazy { App.get(appContext) }
    private val listOfPairedDevices = mutableListOf<PairedDevice>() //TODO поменять на Set или предотвратить появления двойников
    var appState = State.NORMAL

    enum class State {
        PAIRING,
        NORMAL
    }

    private var deviceForConfirm: DeviceInfoFirebase? = null //TODO объеденить в класс или Pair для большей логичности
    private var pairingConfirmed = false


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
                            App.CURRENT_ROOM_ID = it
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
        disposables.clear()
        pairingDisposables += firebasePairingWifi.connect()
                .andThen(firebasePairingWifi.addDeviceToPairingFolder())
                .andThen(firebasePairingWifi.listenPairingFolder())
                .compose(RxUtils.applyFlowableIoSchedulers())
                .subscribeBy(
                        onNext = {
                            parsePairingEvents(it)
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


    private fun parsePairingEvents(event: ChildEvent<DataSnapshot>) {
        val device = event.data.getValue(DeviceInfoFirebase::class.java) as DeviceInfoFirebase
        when (event) {
            is ChildEventAdded<DataSnapshot> -> {
                if (device.uuid != App.CURRENT_DEVICE_UUID) {
                    deviceForConfirm = device
                    getView()?.showPairingConfirmationDialog(device)
                    // Сразу же запускаем листнера если другое устройство подтвердило сопряжение
                    //checkForConfirmationWhilePairing(device)
                }
            }
            is ChildEventRemoved<DataSnapshot> ->
                if (deviceForConfirm == device) {
                    getView()?.closePairingConfirmationDialog() //TODO tests
                    stopPairing()
                    val msg = "The device ${device.name} has stopped pairing" //TODO from stringRes
                    getView()?.showSnackbar(msg)
                    Timber.d(msg)
                }
        }
    }

//    /**
//     * Проверяет подтверждение сопряжения от другого устройства,
//     * используется только во время pairing режима TODO возможно стоит заменить на отслеживание в папке on_disconnect
//     */
//    private fun checkForConfirmationWhilePairing(device: DeviceInfoFirebase) {
//        pairingDisposables += firebasePairingWifi.listenForOtherConfirmedPairing(device)
//
//                .compose(RxUtils.applyMaybeIoSchedulers())
//                .subscribeBy(onSuccess = {
//                    if (deviceForConfirm?.uuid == it.whoConfirmed)
//                        pairingConfirmed = true
//                })
//    }


    fun confirmPairingAndWaitForOther(otherDevice: DeviceInfoFirebase) {
        getView()?.hidePairingStatus()
        deviceForConfirm = null
        pairedDisposable = firebasePairingWifi.saveOtherDeviceAsPaired(otherDevice)  // нет перехода к след Completable
                .andThen { firebasePairingWifi.listenForOtherConfirmedPairing(otherDevice) }
                .andThen { firebasePairingWifi.saveDeviceToRoom(otherDevice) }
                .andThen { firebasePairingWifi.removerThisDeviceFromPairing() }
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onComplete = {
                            getView()?.showSnackbar("You and device ${otherDevice.name} paired!") // TODO to stringRes
                            pairingDisposables.clear()
                        },
                        onError = { TODO("not implemented") }
                )
    }

    fun stopPairing() {
        getView()?.hidePairingStatus()
        deviceForConfirm = null
        pairingDisposables.clear()
        firebasePairingWifi.removerThisDeviceFromPairing()
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onError = { Timber.d(it.fillInStackTrace()) }
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


    private fun listenRoomEvents() {
        disposables += firebasePairingWifi.listenForDeviceToRoom()
                .flatMap { firebasePairingWifi.listenForPairedDevicesInRoom(it) }
                .compose(RxUtils.applyFlowableIoSchedulers())
                .subscribeBy(
                        onNext = {
                            parseListOfPairedDevices(it)
                        }
                )
    }

    private fun parseListOfPairedDevices(childEvent: ChildEvent<DataSnapshot>) { // TODO проверить
        val pairedDevice = childEvent.data.getValue(PairedDevice::class.java) as PairedDevice
        when (childEvent) {
            is ChildEventAdded<DataSnapshot> ->
                listOfPairedDevices += pairedDevice
            is ChildEventRemoved<DataSnapshot> ->
                listOfPairedDevices -= pairedDevice
            is ChildEventChanged<DataSnapshot> ->
                listOfPairedDevices.replaceAll { if (it.uuid == pairedDevice.uuid) pairedDevice else it }
        }
        listOfPairedDevices.forEachWithIndex { index, el -> Timber.d("element#$index =  ${el.name}") }

        //TODO("Add refreshing UI on list change")
    }


    fun removeDeviceFromList(device: PairedDevice) {
        Timber.d("Device to remove ${device.name}")
        listOfPairedDevices.removeIf { it.uuid == device.uuid }
    }

    fun onStop() {
        disposables.clear()
        if (appState == State.PAIRING) {
            stopPairing()
        }
        listOfPairedDevices.clear()
    }

    fun onStart() {
        listenRoomEvents()
    }
}