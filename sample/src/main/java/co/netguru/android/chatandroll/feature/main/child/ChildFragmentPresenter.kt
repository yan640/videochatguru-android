package co.netguru.android.chatandroll.feature.main.video

import android.content.Context
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.ChildEvent
import co.netguru.android.chatandroll.common.extension.ChildEventAdded
import co.netguru.android.chatandroll.common.extension.ChildEventChanged
import co.netguru.android.chatandroll.common.extension.ChildEventRemoved
import co.netguru.android.chatandroll.common.util.RxUtils
import co.netguru.android.chatandroll.data.firebase.FirebaseChild
import co.netguru.android.chatandroll.data.firebase.FirebasePairedOnline
import co.netguru.android.chatandroll.data.firebase.FirebaseSignalingDisconnect
import co.netguru.android.chatandroll.data.firebase.FirebaseSignalingOnline
import co.netguru.android.chatandroll.data.model.Child
import co.netguru.android.chatandroll.data.model.PairingDevice
import co.netguru.android.chatandroll.feature.base.BasePresenter
import com.google.firebase.database.DataSnapshot
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import org.jetbrains.anko.collections.forEachWithIndex
import org.webrtc.CameraVideoCapturer
import org.webrtc.PeerConnection
import timber.log.Timber
import javax.inject.Inject


class ChildFragmentPresenter @Inject constructor(
        private val appContext: Context,
        private val firebaseSignalingOnline: FirebaseSignalingOnline,
        private val firebasePairedOnline: FirebasePairedOnline,
        private val firebaseSignalingDisconnect: FirebaseSignalingDisconnect,
        private val firebaseChild: FirebaseChild
) : BasePresenter<ChildFragmentView>() {

    private val onDestroyDestroedDisposables = CompositeDisposable()

    private var checkChildFolderDisposable : Disposable = Disposables.disposed()

    private var childDisposable = Disposables.disposed()


    private var disconnectOrdersSubscription: Disposable = Disposables.disposed()

    private val app: App by lazy { App.get(appContext) }

    private val listOfChildren = mutableListOf<Child>()
    private var childrenKey = ""
    private var childrensSize = 0
    private val childPicked
        get() = listOfChildren.find { it.key == childrenKey }

    private var pairingCandidate: PairingDevice? = null


    /**
     * Служит для обработки момента, когда otherDevice было уже добавленно в paired этим устройством,
     * но отменило сопряжение. При этом оно должно удалено из paired
     */

    enum class State {
        PAIRING,
        NORMAL
    }

    private var appState = State.NORMAL


    val cameraSwitchHandler = object : CameraVideoCapturer.CameraSwitchHandler {

        override fun onCameraSwitchDone(isFront: Boolean) {

            changeCameraToOpposite(isFront)
            Timber.d("WebRtcServiceController", "camera switched to Front: $isFront")
        }

        override fun onCameraSwitchError(msg: String?) {
            Timber.d("WebRtcServiceController", "failed to switch camera " + msg)
        }
    }


    //<editor-fold desc="Fragment (View) Lifecycle Events">

    fun onViewCreated() {
        checkChildFolderForEmpty()
    }

    fun onDestroyView() {
        firebaseChild.disconnect()
        onDestroyDestroedDisposables.clear()

        checkChildFolderDisposable.dispose()
        childDisposable.dispose()
        listOfChildren.clear()

    }




    override fun detachView() {
        super.detachView()
        firebaseChild.disconnect()
        checkChildFolderDisposable.dispose()

    }


    override fun attachView(mvpView: ChildFragmentView) {
        super.attachView(mvpView)
    }
    //</editor-fold>


    //<editor-fold desc="Pairing">




    //</editor-fold>


    //<editor-fold desc="After pairing">
    private fun checkChildFolderForEmpty() {
        checkChildFolderDisposable =firebaseChild.connect()
                .andThen(firebaseChild.listenChildFolder(App.CURRENT_ROOM_ID))
                .compose(RxUtils.applySingleIoSchedulers())
                .subscribeBy(
                        onSuccess = {
                            Timber.d(it.toString())
                            if (it.hasChildren()) {
                                getDataFromServer()
                                childrensSize = it.childrenCount.toInt()
                            } else getView()?.showSetChildNameDialog()
                            if (checkChildFolderDisposable.isDisposed)
                            {checkChildFolderDisposable.dispose()}
                            else
                            {checkChildFolderDisposable.dispose()}
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
                listOfChildren += child

            }
            is ChildEventRemoved<DataSnapshot> -> {
                listOfChildren -= child
                childrensSize--
            }
            is ChildEventChanged<DataSnapshot> -> {
                listOfChildren.removeAll { it.key == child.key }
                listOfChildren += child
            }
        }
        // Проверяем что в списке есть наше устройство и хотябы одно другое
        if (listOfChildren != null) {
            if (listOfChildren.size >= childrensSize) {
                if (listOfChildren.size == 1) {//Если ребенок один, выбираем его автоматически
                    setChildOnline(listOfChildren[0].key)

                } else updateUI() //Показываем рекуклер вью с детьми и ждем выбора
            }

        }

        listOfChildren.forEachWithIndex { index, el -> Timber.d("element#$index =  ${el.childName}") }
    }

    fun changeCameraToOpposite(isFront: Boolean) {
        App.get(appContext).FRONT_CAMERA_INITIALIZATION=isFront
        childPicked?.run {
            useFrontCamera = App.get(appContext).FRONT_CAMERA_INITIALIZATION
            saveChildrenSetting(this)
        }
    }

    private fun saveChildrenSetting(child: Child) {
        onDestroyDestroedDisposables += firebaseChild.saveChildSetting(child)
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
            onDestroyDestroedDisposables += firebaseChild.saveChildSetting(this)

                    .compose(RxUtils.applyCompletableIoSchedulers())
                    .subscribeBy(
                            onError = {
                                Timber.d(it.fillInStackTrace())
                            },
                            onComplete = {

                                App.get(appContext).FRONT_CAMERA_INITIALIZATION = this.useFrontCamera
                                startChildVideo()
                            }
                    )
        }
    }


    private fun updateUI() = getView()?.run {
        updateChildRecycler(listOfChildren)  // TODO при большом кол-ве сопряженных устойст много раз переррсовывает recycler при их загрузке из базы, не удаляет эл-ты при полном удалении базы

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




    }


    fun startChildVideo() {
        onDestroyDestroedDisposables += firebasePairedOnline.connect()
                .andThen(firebasePairedOnline.getRoomId())

                .compose(RxUtils.applySingleIoSchedulers())
                .subscribeBy(
                        onSuccess = {
                            Timber.d("Next $it")
                            //getView()?.showCamViews()

                            App.CURRENT_ROOM_ID = it
                            connect()

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
        onDestroyDestroedDisposables += firebaseSignalingOnline.disconnectRightWay()
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

    fun disconnectChild() {
        childPicked?.run {
            phoneUuid = App.THIS_DEVICE_UUID
            phoneModel = App.THIS_DEVICE_MODEL
            online = true
            onDestroyDestroedDisposables += firebaseChild.setChildOffline(this)
                    .compose(RxUtils.applyCompletableIoSchedulers())
                    .subscribeBy(
                            onError = {
                                Timber.d(it)
                                //    onDestroyView()
                            },
                            onComplete = {
                                disconnectOrdersSubscription.dispose()
                                // onDestroyView()
//                            getView()?.disconnect()
//
//
//                            getView()?.showStartRouletteView()
                            }
                    )
        }
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





