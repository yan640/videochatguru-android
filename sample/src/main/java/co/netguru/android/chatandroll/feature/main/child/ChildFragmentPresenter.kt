package co.netguru.android.chatandroll.feature.main.video

import android.content.Context
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.ChildEvent
import co.netguru.android.chatandroll.common.extension.ChildEventAdded
import co.netguru.android.chatandroll.common.extension.ChildEventChanged
import co.netguru.android.chatandroll.common.extension.ChildEventRemoved
import co.netguru.android.chatandroll.common.util.RxUtils
import co.netguru.android.chatandroll.data.firebase.*
import co.netguru.android.chatandroll.data.model.Child
import co.netguru.android.chatandroll.data.model.PairingDevice
import co.netguru.android.chatandroll.feature.base.BasePresenter
import com.google.firebase.database.DataSnapshot
import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import org.jetbrains.anko.collections.forEachWithIndex
import org.webrtc.PeerConnection
import timber.log.Timber
import javax.inject.Inject


class ChildFragmentPresenter @Inject constructor(
        private val appContext: Context,
        private val firebaseSignalingOnline: FirebaseSignalingOnline,
        private val firebasePairedOnline: FirebasePairedOnline,
        private val firebaseSignalingDisconnect: FirebaseSignalingDisconnect,
        private val firebasePairingWifi: FirebasePairingWifi,
        private val firebaseChild: FirebaseChild
) : BasePresenter<ChildFragmentView>() {

    private val onDestroyDestroedDisposables = CompositeDisposable()
    private var pairedDisposable = Disposables.disposed()
    private var checkChildFolderDisposable = Disposables.disposed()

    private val pairingDisposables = CompositeDisposable()
    private var childDisposable = Disposables.disposed()


    private var disposableForRetrieveKey: Disposable = Disposables.disposed()
    private var disconnectOrdersSubscription: Disposable = Disposables.disposed()

    private val app: App by lazy { App.get(appContext) }

    private val listOfChildrens = mutableListOf<Child>()
    var childrenKey = ""
    var childrensSize = 0
    private val childPicked
        get() = listOfChildrens.find { it.key == childrenKey }
//
//    private val listOfOtherDevicePaired
//        get() = listOfChildrens.filter { it.uuid != App.THIS_DEVICE_UUID }


    private var pairingCandidate: PairingDevice? = null


    /**
     * Служит для обработки момента, когда otherDevice было уже добавленно в paired этим устройством,
     * но отменило сопряжение. При этом оно должно удалено из paired
     */
    private var isOtherDeviceAddedInPaired: Boolean = false

    enum class State {
        PAIRING,
        NORMAL
    }

    var appState = State.NORMAL


    //<editor-fold desc="Fragment (View) Lifecycle Events">

    fun onViewCreated() {
        checkChildFolderForEmpty()
    }

    fun onDestroyView() {
        firebaseChild.disconnect()
        onDestroyDestroedDisposables.clear()
        if (appState == State.PAIRING)
            stopPairing()
        listOfChildrens.clear()

    }


    override fun detachView() {
        super.detachView()
    }


    override fun attachView(mvpView: ChildFragmentView) {
        super.attachView(mvpView)
    }
    //</editor-fold>


    //<editor-fold desc="Pairing">



    /**
     * Add your device to FDB folder "wifi_pair_devices/YOUR_WIFI"
     * and listen for ready in that folder
     */
    private fun startWifiPairing(wifiBSSID: String) {
        onDestroyDestroedDisposables.clear()
        pairingDisposables += firebasePairingWifi.connect()
                .andThen(firebasePairingWifi.addDeviceToPairing(wifiBSSID))
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

    /**
     * Обрабатывает добавление и удаление устройств в pairing_devices folder во время сопряжения
     */
    private fun parsePairingEvents(event: ChildEvent<DataSnapshot>) {
        val device = event.data.getValue(PairingDevice::class.java) as PairingDevice
        when (event) {
            is ChildEventAdded<DataSnapshot> -> {
                if (device.uuid != App.THIS_DEVICE_UUID) {
                    pairingCandidate = device
                    getView()?.closePairingProgessDialog()
                    getView()?.showPairingConfirmationDialog(device)
                }
            }
            is ChildEventRemoved<DataSnapshot> ->
                if (pairingCandidate == device) {
                    stopPairing()
                    getView()?.showMessageDeviceStoppedPairing(device.name)
                    // TODO вернуть UI в исходное состояние
                }
        }
    }


    fun confirmPairingAndWaitForOther(pairingCandidate: PairingDevice) {
        pairedDisposable = firebasePairingWifi.saveThisDeviceInPaired(pairingCandidate)
                .andThen(firebasePairingWifi.listenForPairingCandidateConfirmed(pairingCandidate)) //  в andThen() круглые скобки!!!
                .doOnComplete { pairingDisposables.clear() }  // если не уничтожить pairing потоки, будет ошибочная остановка сопряжения когда Кандидат удалит себя из pairing_devices
                .andThen(firebasePairingWifi.saveRoomReference(pairingCandidate))
                .andThen(firebasePairingWifi.removeThisDeviceFromPairing())
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onComplete = {
                            getView()?.showSnackbarFromString("You and device ${pairingCandidate.name} paired!") // TODO to stringRes
                            this.pairingCandidate = null
                            pairedDisposable.dispose()
                            getDataFromServer()
                        },
                        onError = { TODO("not implemented") }
                )
    }

    fun stopPairing() {
        getView()?.closePairingProgessDialog()
        getView()?.closePairingConfirmationDialog()
        pairingDisposables.clear()
        removeThisDeviceFromPaired()
                .andThen(firebasePairingWifi.removeThisDeviceFromPairing())
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onComplete = {
                            // TODO вернуть UI вв исходное состояние)
                        },
                        onError = { Timber.d(it.fillInStackTrace()) }
                )
    }

    private fun removeThisDeviceFromPaired(): Completable {
        pairingCandidate?.let {
            return firebasePairingWifi.removeThisDeviceFromPaired(it)
        }
        return Completable.complete()
    }

    //</editor-fold>


    //<editor-fold desc="After pairing">
    private fun checkChildFolderForEmpty() {
        checkChildFolderDisposable = firebaseChild.listenChildFolder(App.CURRENT_ROOM_ID)
                .compose(RxUtils.applySingleIoSchedulers())
                .subscribeBy(
                        onSuccess = {
                            Timber.d(it.toString())
                            if (it.hasChildren()) {
                                getDataFromServer()
                                childrensSize = it.childrenCount.toInt()
                            } else getView()?.showSetChildNameDialog()
                            checkChildFolderDisposable.dispose()
                            //  updateLocalListOfChildes(it)
                        },
                        onError = {
                            Timber.d(it.fillInStackTrace())
                            checkChildFolderDisposable.dispose()
                        }
                )
    }

    /**
     * Получает и отслеживает актуальные данные доступные данному усторйству из
     * SharedPreferences или FirebaseDB
     */
    private fun getDataFromServer() {
        onDestroyDestroedDisposables += firebaseChild.listenRoom(App.CURRENT_ROOM_ID)
                .doOnNext { Timber.d("get paired device in room = $it") }
                .compose(RxUtils.applyFlowableIoSchedulers())
                .subscribeBy(
                        onNext = {
                            updateLocalListOfChildes(it)
                        },
                        onError = {
                            Timber.d(it.fillInStackTrace())
                        },
                        onComplete = {
                            //getView()?.showSetChildNameDialog()
                        }

                )
    }


    private fun updateLocalListOfChildes(childEvent: ChildEvent<DataSnapshot>) {
        val child = childEvent.data.getValue(Child::class.java) as Child
        when (childEvent) {
            is ChildEventAdded<DataSnapshot> -> {
                listOfChildrens += child

            }
            is ChildEventRemoved<DataSnapshot> -> {
                listOfChildrens -= child
                childrensSize--
            }
            is ChildEventChanged<DataSnapshot> -> {
                listOfChildrens.removeAll { it.key == child.key }
                listOfChildrens += child
            }
        }
        // Проверяем что в списке есть наше устройство и хотябы одно другое
        if (listOfChildrens != null) {
            if (listOfChildrens.size >= childrensSize) {
                if (listOfChildrens.size == 1) {//Если ребенок один, выбираем его автоматически
                    setChildOnline(listOfChildrens[0].key)

                } else updateUI() //Показываем рекуклер вью с детьми и ждем выбора
            }

        }

        listOfChildrens.forEachWithIndex { index, el -> Timber.d("element#$index =  ${el.childName}") }
    }


    private fun saveChildrenSetting(child: Child) {
        onDestroyDestroedDisposables += firebaseChild.saveChildrenSetting(child)

                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onError = {
                            Timber.d(it.fillInStackTrace())
                        },
                        onComplete = {
                        }
                )
    }


    fun setChildOnline(childrenK: String) {
        childrenKey = childrenK
        childPicked?.run {
            phoneUuid = App.THIS_DEVICE_UUID
            phoneModel = App.THIS_DEVICE_MODEL
            online = true
            onDestroyDestroedDisposables += firebaseChild.setChildOnline(this)

                    .compose(RxUtils.applyCompletableIoSchedulers())
                    .subscribeBy(
                            onError = {
                                Timber.d(it.fillInStackTrace())
                            },
                            onComplete = {
                                startChildVideo()
                            }
                    )
        }
    }


    private fun updateUI() = getView()?.run {
        updateChildRecycler(listOfChildrens)  // TODO при большом кол-ве сопряженных устойст много раз переррсовывает recycler при их загрузке из базы, не удаляет эл-ты при полном удалении базы

    }


    fun setChildName(childName: String) {
        childDisposable = firebaseChild.getKeyForNewChild()
                .doOnSuccess {
                    Timber.d("get children Key  = $it")
                    childrenKey = it
                }
                .flatMapCompletable { firebaseChild.saveThisChildInPaired(childName, it) }
                .doOnComplete { }
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onComplete = {
                            //getView()?.showSnackbarFromString("You and device ${pairingCandidate.name} paired!") // TODO to stringRes
                            this.pairingCandidate = null
                            childrensSize++
                            childDisposable.dispose()
                            getDataFromServer()


                        },
                        onError = { TODO("not implemented") }
                )


        // TODO Создать и сохранить Чайлда

    }


    fun startChildVideo() {
        onDestroyDestroedDisposables += firebasePairedOnline.connect()
                .andThen(firebasePairedOnline.getRoomId())

                .compose(RxUtils.applySingleIoSchedulers())
                .subscribeBy(
                        onSuccess = {
                            Timber.d("Next $it")
                            //getView()?.showCamViews()
                            App.get(appContext).FRONT_CAMERA_INITIALIZATION = false
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
        onDestroyDestroedDisposables += firebaseSignalingOnline.connect()
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
        onDestroyDestroedDisposables += firebaseSignalingOnline.disconnect()
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
//        hideConnectButtonWithAnimation()
    }


    fun disconnectByUser() {
        val remoteUuid = getView()?.remoteUuid
        if (remoteUuid != null) {
            onDestroyDestroedDisposables += firebaseSignalingDisconnect.sendDisconnectOrderToOtherParty(remoteUuid)
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
    //</editor-fold>


}





