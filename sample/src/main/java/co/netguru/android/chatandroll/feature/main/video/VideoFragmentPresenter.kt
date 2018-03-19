package co.netguru.android.chatandroll.feature.main.video

import android.content.Context
import android.util.Log
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.ChildEvent
import co.netguru.android.chatandroll.common.extension.ChildEventAdded
import co.netguru.android.chatandroll.common.extension.ChildEventChanged
import co.netguru.android.chatandroll.common.extension.ChildEventRemoved
import co.netguru.android.chatandroll.common.util.RxUtils
import co.netguru.android.chatandroll.data.SharedPreferences.SharedPreferences
import co.netguru.android.chatandroll.data.firebase.*
import co.netguru.android.chatandroll.data.model.PairedDevice
import co.netguru.android.chatandroll.data.model.PairingDevice
import co.netguru.android.chatandroll.data.model.Role
import co.netguru.android.chatandroll.feature.base.BasePresenter
import com.google.firebase.database.DataSnapshot
import io.reactivex.Completable
import io.reactivex.Single
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

    private val listOfPairedDevices = mutableListOf<PairedDevice>()
    private val currentDevicePaired
        get() = listOfPairedDevices.find { it.uuid == App.CURRENT_DEVICE_UUID }

    private val listOfOtherDevicePaired
        get() = listOfPairedDevices.filter { it.uuid != App.CURRENT_DEVICE_UUID }

    var appState = State.NORMAL

    private var isOtherDeviceAddedInPaired: Boolean = false

    enum class State {
        PAIRING,
        NORMAL
    }


    private var deviceForConfirm: PairingDevice? = null


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
    fun startWifiPairing(wifiBSSID: String) {
        Log.d("TAG", "wifi pairing")
        disposables.clear()
        pairingDisposables += firebasePairingWifi.connect()
                .andThen(firebasePairingWifi.addDeviceToPairingFolder(wifiBSSID))
                .andThen(firebasePairingWifi.listenPairingFolder(wifiBSSID))
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
        val device = event.data.getValue(PairingDevice::class.java) as PairingDevice
        when (event) {
            is ChildEventAdded<DataSnapshot> -> {
                if (device.uuid != App.CURRENT_DEVICE_UUID) {
                    deviceForConfirm = device
                    getView()?.closePairingProgessDialog()
                    getView()?.showPairingConfirmationDialog(device)
                }
            }
            is ChildEventRemoved<DataSnapshot> ->
                if (deviceForConfirm == device) {
                    stopPairing()
                    getView()?.showMessageDeviceStoppedPairing(device.name)
                }
        }
    }


    fun confirmPairingAndWaitForOther(otherDevice: PairingDevice) {
        getView()?.closePairingProgessDialog()
        pairedDisposable = firebasePairingWifi.saveOtherDeviceAsPaired(otherDevice)
                .doOnComplete { isOtherDeviceAddedInPaired = true }
                .andThen(firebasePairingWifi.listenForOtherConfirmedPairing(otherDevice)) // критично, в andThen() круглые скобки
                .doOnComplete{pairingDisposables.clear()}
                .andThen(firebasePairingWifi.saveDeviceToRoom(otherDevice))
              .doOnComplete { Timber.d("saveDeviceToRoom(otherDevice) complete") }
                .andThen(firebasePairingWifi.removerThisDeviceFromPairing())
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onComplete = {
                            getView()?.showSnackbarFromString("You and device ${otherDevice.name} paired!") // TODO to stringRes
                            deviceForConfirm = null
                            pairedDisposable.dispose()
                            getActualDeviceData()
                        },
                        onError = { TODO("not implemented") }
                )
    }

    fun stopPairing() {
        getView()?.closePairingProgessDialog()
        getView()?.closePairingConfirmationDialog()
        pairingDisposables.clear()
        removeOtherDeviceFromPaired()
                .andThen(firebasePairingWifi.removerThisDeviceFromPairing())
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onComplete = {
                            getActualDeviceData()
                        },
                        onError = { Timber.d(it.fillInStackTrace()) }
                )
    }

    private fun removeOtherDeviceFromPaired(): Completable {
        deviceForConfirm?.let {
            if (isOtherDeviceAddedInPaired) {
                return firebasePairingWifi.removerOtherDeviceFromPaired(it)
            }
        }
        return Completable.complete()
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

    /**
     * Получает и отслеживает актуальные данные по данному усторйству из
     * SharedPreferences или FirebaseDB
     */
    private fun getActualDeviceData() {
        disposables += firebasePairingWifi.connect()
                .andThen(getDeviceUUid())
                .doOnSuccess {
                    Timber.d("get device UUID = $it")
                    App.CURRENT_DEVICE_UUID = it
                }
                .flatMapPublisher { firebasePairingWifi.listenForRoomReference(it) }
                .doOnNext {
                    Timber.d("get Room id = $it")
                }
                .flatMap { firebasePairingWifi.listenForPairedDevicesInRoom(it) }
                .doOnNext { Timber.d("get paired device in room = $it") }
                .compose(RxUtils.applyFlowableIoSchedulers())
                .subscribeBy(
                        onNext = {
                            parseListOfPairedDevices(it)
                        }
                )
    }

    private fun getDeviceUUid(): Single<String> {
        return if (SharedPreferences.hasToken(appContext)) {
            Single.just(SharedPreferences.getToken(appContext))
        } else {
            firebasePairedOnline.getMeNewKey()
        }
    }


    private fun parseListOfPairedDevices(childEvent: ChildEvent<DataSnapshot>) {
        val pairedDevice = childEvent.data.getValue(PairedDevice::class.java) as PairedDevice
        when (childEvent) {
            is ChildEventAdded<DataSnapshot> ->
                listOfPairedDevices += pairedDevice
            is ChildEventRemoved<DataSnapshot> ->
                listOfPairedDevices -= pairedDevice
            is ChildEventChanged<DataSnapshot> -> {
                listOfPairedDevices.removeAll { it.uuid == pairedDevice.uuid }
                listOfPairedDevices += pairedDevice
            }
        }
        // Проверяем что в списке есть наше устройство и хотябы одно другое
        if (currentDevicePaired != null
                && listOfOtherDevicePaired.isNotEmpty()) {
            setDeviceOnline()
            updateUI()
        } else {
            getView()?.hideParentChildButtons()
        }

        // TODO удалить комнату из базы если там одно устройство или нашу ссылку на комнату если нашего устройства нет в списке

        listOfPairedDevices.forEachWithIndex { index, el -> Timber.d("element#$index =  ${el.deviceName}") }
    }


    private fun updateUI() = getView()?.run {
        updateDevicesRecycler(listOfOtherDevicePaired)  // TODO при большом кол-ве сопряженных устойст много раз переррсовывает recycler при их загрузке из базы, не удаляет эл-ты при полном удалении базы
        showParentChildButtons()
        setPairButtonText("Unpair")
        currentDevicePaired?.let {
            when (it.role) {
                Role.CHILD -> {
                    setChildButtonChecked(true)
                    showChildName(it.childName)
                }
                Role.PARENT -> {
                    setParentButtonChecked(true)
                    hideChildName()
                }
                Role.UNDEFINED -> {
                    showChooseRoleDialog()
                    showParentChildButtons()
                    hideChildName()
                }
            }
        }
    }


    fun onDestroyView() {
        disposables.clear()
        if (appState == State.PAIRING)
            stopPairing()
        listOfPairedDevices.clear()
        // TODO online - offline
    }

    fun onViewCreated() {
        getActualDeviceData()
    }

    fun pairButtonClicked() {
        app.CURRENT_WIFI_BSSID?.let {
            startWifiPairing(it)
            getView()?.showPairingProgressDialog()
        } ?: getView()?.showSnackbarFromString("Connect phones to one Wifi!")
        // TODO добавить альтернативный вариант подключения при отсутствии общего wifi
    }


    fun parentRoleButtonClicked() = getView()?.run {
        setParentButtonChecked(true)
        setChildButtonChecked(false)
        hideChildName()
        currentDevicePaired?.run {
            role = Role.PARENT
            firebasePairingWifi.updateThisDeviceData(this)
        }
    }


    fun childRoleButtonClicked() = getView()?.run {
        setChildButtonChecked(true)
        setParentButtonChecked(false)
        currentDevicePaired?.run {
            role = Role.CHILD
            firebasePairingWifi.updateThisDeviceData(this)
            if (childName.isNotBlank())
                showChildName(childName)
            else
                showSetChildNameDialog()
        }
    }


    fun setChildName(childName: String) = currentDevicePaired?.let {
        it.childName = childName
        firebasePairingWifi.updateThisDeviceData(it)
    }


    fun setDeviceOnline() = currentDevicePaired?.let {
        it.online = true
        firebasePairingWifi.updateThisDeviceData(it)

    }

    fun childNameButtonClicked() {
        getView()?.showSetChildNameDialog(currentDevicePaired?.childName)
    }
}