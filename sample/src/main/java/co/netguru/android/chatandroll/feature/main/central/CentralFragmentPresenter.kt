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
import co.netguru.android.chatandroll.data.model.Role
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


    enum class State {
        PAIRING,
        NORMAL
    }

    var appState = State.NORMAL


    //<editor-fold desc="BasePresenter methods">
    override fun attachView(mvpView: CentralFragmentView) {
        super.attachView(mvpView)
        onAttachView()

    }

    override fun detachView() {
        super.detachView()
    }

    //</editor-fold>


    //<editor-fold desc="Fragment (View) Lifecycle Events">

    private fun onAttachView() {
        getDataFromServer()
    }


    fun onDestroyView() {
        // TODO
    }


    //</editor-fold>


    //<editor-fold desc="Pairing">

    fun pairButtonClicked() {
        app.CURRENT_WIFI_BSSID?.let {
            startWifiPairing(it)
            getView()?.showPairingProgressDialog()
        } ?: getView()?.showSnackbarFromString("Connect phones to one Wifi!") // TODO to strRes
        // TODO добавить альтернативный вариант подключения при отсутствии общего wifi
    }


    /**
     * Add your device to FDB folder "wifi_pair_devices/YOUR_WIFI"
     * and listen for ready in that folder
     */
    private fun startWifiPairing(wifiBSSID: String) {
        actualPairedDataDisposables.clear()
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
                    getView()?.closePairingProgressDialog()
                    getView()?.showPairingConfirmationDialog(device)
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
        getView()?.closePairingProgressDialog()
        getView()?.closePairingConfirmationDialog()
        pairingDisposables.clear()
        removeThisDeviceFromPaired()
                .andThen(firebasePairingWifi.removeThisDeviceFromPairing())
                .compose(RxUtils.applyCompletableIoSchedulers())
                .subscribeBy(
                        onComplete = {
                            pairingCandidate = null
                            getView()?.setNoOnePairedState() // TODO отображать только если нет других paired devices
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
        actualPairedDataDisposables += firebasePairingWifi.connect()
                .andThen(getDeviceUUid())
                .doOnSuccess { App.THIS_DEVICE_UUID = it }
                .flatMapPublisher { firebasePairingWifi.listenRoomReference(it) }
                .doOnNext { Timber.d("get Room id = $it") }
                .flatMap { firebasePairingWifi.listenRoom(it) }
                .doOnNext { Timber.d("get paired device in room = $it") }
                .compose(RxUtils.applyFlowableIoSchedulers())
                .subscribeBy(
                        onNext = {
                            updateLocalListOfPairedDevices(it)
                        }
                )
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
        if (thisDeviceDataFromPaired != null
                && listOfOtherDevicePaired.isNotEmpty()) {
            setDeviceOnline()
            updateUI()
        } else {
            getView()?.setNoOnePairedState()
        }

        // TODO удалить комнату из базы если там одно устройство или нашу ссылку на комнату если нашего устройства нет в списке

        listOfPairedDevices.forEachWithIndex { index, el -> Timber.d("element#$index =  ${el.deviceName}") }
    }


    private fun updateUI() = getView()?.run {
        setHasPairedDeviceState()
        updateDevicesRecycler(listOfOtherDevicePaired) // TODO при большом кол-ве сопряженных устойст много раз переррсовывает recycler при их загрузке из базы, не удаляет эл-ты при полном удалении базы
        thisDeviceDataFromPaired?.let {
            when (it.role) {
                Role.CHILD -> {
                    setChildRoleState()
                }
                Role.PARENT -> {
                    setParentRoleState()
                }
                Role.UNDEFINED -> {
                    setHasPairedDeviceState()
                    showChooseRoleDialog()
                }
            }
        }
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

    fun parentRoleButtonClicked() = getView()?.run {
        setParentRoleState()
        hideChildName()
        thisDeviceDataFromPaired?.run {
            role = Role.PARENT
            pushThisDeviceDataToServer(this)
        }
    }


    fun childRoleButtonClicked() = getView()?.run {
        setChildRoleState()
        thisDeviceDataFromPaired?.run {
            role = Role.CHILD
            pushThisDeviceDataToServer(this)
            if (childName.isNotBlank())
                showChildName(childName)
            else
                showSetChildNameDialog()
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

    //</editor-fold>


}
