package co.netguru.android.chatandroll.feature.main.central

import android.content.Context
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.ChildEvent
import co.netguru.android.chatandroll.common.extension.ChildEventAdded
import co.netguru.android.chatandroll.common.extension.ChildEventChanged
import co.netguru.android.chatandroll.common.extension.ChildEventRemoved
import co.netguru.android.chatandroll.common.util.RxUtils
import co.netguru.android.chatandroll.data.SharedPreferences.SharedPreferences
import co.netguru.android.chatandroll.data.firebase.FirebasePairingWifi
import co.netguru.android.chatandroll.data.model.PairedDevice
import co.netguru.android.chatandroll.data.model.PairingDevice
import co.netguru.android.chatandroll.data.model.Role.*
import co.netguru.android.chatandroll.data.model.State.*
import co.netguru.android.chatandroll.feature.base.BasePresenter
import com.google.firebase.database.DataSnapshot
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposables
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import org.jetbrains.anko.collections.forEachWithIndex
import timber.log.Timber

class CentralFragmentPresenter(private val appContext: Context,
                               private val firebasePairingWifi: FirebasePairingWifi) :
        BasePresenter<CentralFragmentView>() {


    private val actualPairedDataDisposables = CompositeDisposable()
    private val pairingDisposables = CompositeDisposable()
    private var pairedDisposable = Disposables.disposed()
    private val app = App.get(appContext)


    private val listOfPairedDevices = mutableListOf<PairedDevice>() // TODO mutable change
    private val thisDeviceDataFromPaired
        get() = listOfPairedDevices.find { it.uuid == App.THIS_DEVICE_UUID }

    private val listOfOtherDevicePaired
        get() = listOfPairedDevices.filter { it.uuid != App.THIS_DEVICE_UUID }


    private var pairingCandidate: PairingDevice? = null


    private var viewState = UNDEFINED_STATE
    private var role = ROLE_NOT_SET

    private var childName: String = ""


    //<editor-fold desc="BasePresenter methods">

    override fun attachView(mvpView: CentralFragmentView) {
        super.attachView(mvpView)
        updateUI()
    }

    override fun detachView() {
        super.detachView()
    }


    //</editor-fold>


    //<editor-fold desc="Fragment (View) Lifecycle Events">


    fun onDestroyView() {
        // TODO
    }

    //</editor-fold>


    //<editor-fold desc="Pairing">

    fun pairButtonClicked() {
        app.CURRENT_WIFI_BSSID?.let {  // todo перенести все что связано с контехт во mvpView
            viewState = PAIRING
            updateUI()
            startWifiPairing(it)
        } ?: getView()?.showSnackbarFromString("Connect phones to one Wifi!") // TODO to strRes
        // TODO добавить альтернативный вариант подключения при отсутствии общего wifi
    }

    fun pairMoreButtonClicked() {
        TODO("not implemented")
    }

    fun onPairingConfirmationCancel() {
        stopPairing()
    }

    /**
     * Add your device to FDB folder "wifi_pair_devices/YOUR_WIFI"
     * and listen for ready in that folder
     */
    private fun startWifiPairing(wifiBSSID: String) {
        actualPairedDataDisposables.clear() // TODO будет заново загружать данные если сделать add
        listOfPairedDevices.clear()
        pairingDisposables += firebasePairingWifi.connect()
                .doOnSubscribe { /*Show pairing dialog */ }
                .andThen(firebasePairingWifi.addDeviceToPairing(wifiBSSID))
                .andThen(firebasePairingWifi.listenPairingFolder(wifiBSSID))
                .doOnTerminate { /*Close pairing dialog */  }
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
                    viewState = CONFIRMATION
                    updateUI()
                }
            }
            is ChildEventRemoved<DataSnapshot> ->
                if (pairingCandidate == device) {
                    stopPairing()
                    getView()?.showMessageDeviceStoppedPairing(device.name)
                }
        }
    }


    fun confirmPairingAndWaitForOther(pairingCandidate: PairingDevice) {
        pairedDisposable = firebasePairingWifi.saveThisDeviceInPaired(pairingCandidate)
                .andThen(firebasePairingWifi.listenForPairingCandidateConfirmed(pairingCandidate)) //  в andThen() круглые скобки!!!
                .doOnComplete {
                    pairingDisposables.clear()
                    Timber.d("pairingDisposables.clear()")
                }  // если не уничтожить pairing потоки, будет ошибочная остановка сопряжения когда Кандидат удалит себя из pairing_devices
                .andThen(firebasePairingWifi.saveRoomReference(pairingCandidate))
                .andThen(firebasePairingWifi.removeThisDeviceFromPairing())
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onComplete = {
                            getView()?.showSnackbarFromString("You and device ${pairingCandidate.name} paired!") // TODO to stringRes
                            this.pairingCandidate = null
                            pairedDisposable.dispose()
                            viewState = PAIRED
                            getDataFromServer()
                        },
                        onError = { TODO("not implemented") }
                )
    }

    fun stopPairing() {
        //  viewState = NOT_PAIRED // TODO запрос на аскуальные данные
        //  updateUI()
        Timber.d("stopPairing()")
        pairingDisposables.clear()
        removeThisDeviceFromPaired()
                .andThen(firebasePairingWifi.removeThisDeviceFromPairing())
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onComplete = {
                            pairingCandidate = null
                            getDataFromServer()

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

    /**
     * Получает и отслеживает актуальные данные доступные данному усторйству из
     * SharedPreferences или FirebaseDB
     */
    private fun getDataFromServer() {
        Timber.d("getDataFromServer()")
        actualPairedDataDisposables += firebasePairingWifi.connect()
                .andThen(getDeviceUUid())
                .doOnSuccess { App.THIS_DEVICE_UUID = it }
                .flatMapPublisher { firebasePairingWifi.listenRoomReference(it) }
                .doOnNext { App.CURRENT_ROOM_ID = it }
                .flatMap { firebasePairingWifi.listenRoom(it) }
                .doOnNext { Timber.d("get paired device in room = $it") }
                .compose(RxUtils.applyFlowableIoSchedulers())
                .subscribeBy(
                        onNext = {
                            updateLocalListOfPairedDevices(it)
                        }
                ) // TODO если нет данных отобразить кнопку PAir
    }

    private fun getDeviceUUid(): Single<String> {
        return if (SharedPreferences.hasToken(appContext)) {
            Single.just(SharedPreferences.getToken(appContext))
        } else {
            firebasePairingWifi.getNewKeyForThisDevice()
        }
    }


    private fun updateLocalListOfPairedDevices(childEvent: ChildEvent<DataSnapshot>) {
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
        if (thisDeviceDataFromPaired != null) {
            Timber.d("this device = $thisDeviceDataFromPaired")
            setDeviceOnline()
            viewState = PAIRED
            thisDeviceDataFromPaired?.let {
                role = it.role
                childName = it.childName
            }

        } else {
            Timber.d("not paired")
            viewState = NOT_PAIRED
        }
        listOfPairedDevices.forEachWithIndex { index, device ->
            Timber.d("$index = ${device.deviceName}")
        }
        updateUI()

        // TODO удалить комнату из базы если там одно устройство или нашу ссылку на комнату если нашего устройства нет в списке
    }


    private fun updateUI() = getView()?.run {
        when (viewState) {
            UNDEFINED_STATE -> this@CentralFragmentPresenter.getDataFromServer()
            NOT_PAIRED -> setNotPairedState()
            PAIRING -> setPairingState()
            CONFIRMATION -> pairingCandidate?.let { setConfirmationState(it) }
            PAIRED -> setPairedState(role, listOfOtherDevicePaired, childName)
        }
        // updateDevicesRecycler(listOfOtherDevicePaired) // TODO при большом кол-ве сопряженных устойст много раз переррсовывает recycler при их загрузке из базы, не удаляет эл-ты при полном удалении базы

    }


    private fun pushThisDeviceDataToServer(thisDevice: PairedDevice) {
        firebasePairingWifi.updateThisDeviceData(thisDevice)
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onComplete = {
                            Timber.d("Device data in room updated!")
                        },
                        onError = {
                            Timber.d("error while updating device data ${it.fillInStackTrace()}")
                        }
                )

    }

    fun parentRoleButtonClicked() {
        role = PARENT
        viewState = PAIRED
        thisDeviceDataFromPaired?.run {
            role = PARENT
            updateUI()
            pushThisDeviceDataToServer(this)
        }
    }


    fun childRoleButtonClicked() {
        role = CHILD
        viewState = PAIRED
        thisDeviceDataFromPaired?.run {
            role = CHILD
            updateUI()
            if (childName.isBlank()) {
                getView()?.showSetChildNameDialog()
            }
            pushThisDeviceDataToServer(this)
        }
    }


    fun onChildNameChanged(childName: String) = thisDeviceDataFromPaired?.let {
        it.childName = childName
        pushThisDeviceDataToServer(it)
    }


    private fun setDeviceOnline() = thisDeviceDataFromPaired?.let {
        it.online = true
        pushThisDeviceDataToServer(it)

    }

    fun childNameButtonClicked() {
        getView()?.showSetChildNameDialog(thisDeviceDataFromPaired?.childName)
    }

    fun leaveRoomButtonClicked() {
        actualPairedDataDisposables.clear()
        firebasePairingWifi.removeThisDeviceFromPaired(App.CURRENT_ROOM_ID)
                .doOnComplete { Timber.d("removeThisDeviceFromPaired") }
                .andThen(firebasePairingWifi.removeThisDeviceFromRoomReference())
                .doOnComplete { Timber.d("removeThisDeviceFromRoomReference()") }
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onComplete = {
                            listOfPairedDevices.clear()
                            viewState = NOT_PAIRED
                            updateUI()
                        }
                )
    }

//</editor-fold>


}
