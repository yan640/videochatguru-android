package co.netguru.android.chatandroll.data.firebase

import android.content.Context
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.ChildEvent
import co.netguru.android.chatandroll.common.extension.ChildEventAdded
import co.netguru.android.chatandroll.common.extension.rxChildEvents
import co.netguru.android.chatandroll.common.extension.rxValueEvents
import co.netguru.android.chatandroll.common.util.RxUtils
import co.netguru.android.chatandroll.data.model.PairedDevice
import co.netguru.android.chatandroll.data.model.PairingDevice
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by yan-c_000 on 06.02.2018.
 * Rewritten by Gleb 21.02.2018
 */
@Singleton
class FirebasePairingWifi @Inject constructor(private val firebaseDatabase: FirebaseDatabase,
                                              private val appContext: Context) {

    companion object {
        private const val WIFI_PAIRING_PATH = "wifi_pairing_devices/"
        private const val PAIRED_ROOMS_PATH = "paired_rooms_devices/"
        private const val ROOM_REFERENCE_PATH = "room_reference/"
        const val ROOM_DELETED = "ROOM_DELETED"

    }

    private val myDevice = PairingDevice(App.CURRENT_DEVICE_UUID, App.model)

    private lateinit var pairingReferenceThisDevice: DatabaseReference
    private val app: App by lazy { App.get(appContext) }


    /**
     * Add you device info to FDB folder [WIFI_PAIRING_PATH]/[CURRENT_WIFI_BSSID]
     */
    fun addDeviceToPairingFolder(wifiBSSID: String): Completable = Completable.create { emitter ->
        pairingReferenceThisDevice = firebaseDatabase
                .getReference(WIFI_PAIRING_PATH)
                .child(wifiBSSID)
                .child(App.CURRENT_DEVICE_UUID)
        with(pairingReferenceThisDevice) {
            onDisconnect().removeValue()
            setValue(myDevice).addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
        }
        emitter.onComplete()
    }


    fun removerThisDeviceFromPairing(): Completable = Completable.create { emitter ->
        pairingReferenceThisDevice.removeValue()
                .addOnCompleteListener { emitter.onComplete() }
                .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
    }


    fun listenPairingFolder(wifiBSSID: String): Flowable<ChildEvent<DataSnapshot>> =
            firebaseDatabase.getReference(WIFI_PAIRING_PATH)
                    .child(wifiBSSID)
                    .rxChildEvents()

    fun disconnect(): Completable = Completable.fromAction {
        firebaseDatabase.goOffline()
    }

    fun connect(): Completable = Completable.fromAction {
        firebaseDatabase.goOnline()
    }


    fun listenForOtherConfirmedPairing(otherDevice: PairingDevice): Completable =
            firebaseDatabase.getReference(PAIRED_ROOMS_PATH)
                    .child(choosePairedFolderName(App.CURRENT_DEVICE_UUID, otherDevice.uuid))
                    .rxChildEvents()
                    .ofType<ChildEventAdded<DataSnapshot>>()
                    .map { it.data }
                    .doOnNext { Timber.d("listenForOtherConfirmedPairing = $it") }
                    .filter { it.value != 0 }
                    .map { it.getValue(PairedDevice::class.java) as PairedDevice }
                    .filter { it.uuid == App.CURRENT_DEVICE_UUID }
                    .firstElement()
                    .ignoreElement()
                    .doOnComplete { Timber.d("listenForOtherConfirmedPairing onComplete") }


    private fun choosePairedFolderName(yourUuid: String, otherUuid: String): String {
        return if (yourUuid > otherUuid) yourUuid else otherUuid
    }


    fun saveOtherDeviceAsPaired(otherDevice: PairingDevice): Completable = Completable.create { emitter ->
        val roomName = choosePairedFolderName(App.CURRENT_DEVICE_UUID, otherDevice.uuid)
        firebaseDatabase.getReference(PAIRED_ROOMS_PATH)
                .child(roomName)
                .child(otherDevice.uuid)
                .setValue(PairedDevice(
                        uuid = otherDevice.uuid,
                        deviceName = otherDevice.name,
                        roomUUID = roomName,
                        whoConfirmed = App.CURRENT_DEVICE_UUID))
                .addOnCompleteListener { emitter.onComplete() }
                .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
    }

    fun removerOtherDeviceFromPaired(otherDevice: PairingDevice): Completable = Completable.create { emitter ->
        val roomName = choosePairedFolderName(App.CURRENT_DEVICE_UUID, otherDevice.uuid)
        firebaseDatabase.getReference(PAIRED_ROOMS_PATH)
                .child(roomName)
                .child(otherDevice.uuid)
                .removeValue()
                .addOnCompleteListener { emitter.onComplete() }
                .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
    }


    fun saveDeviceToRoom(otherDevice: PairingDevice): Completable = Completable.create { emitter ->
        firebaseDatabase.getReference(ROOM_REFERENCE_PATH)
                .child(App.CURRENT_DEVICE_UUID)
                .setValue(choosePairedFolderName(App.CURRENT_DEVICE_UUID, otherDevice.uuid))
                .addOnCompleteListener { emitter.onComplete() }
                .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
    }


    fun listenForRoomReference(deviceUUID: String = App.CURRENT_DEVICE_UUID): Flowable<String> =
            firebaseDatabase.getReference(ROOM_REFERENCE_PATH)
                    .child(deviceUUID)
                    .rxValueEvents()
                    .filter { it.value != null }
                    .map {
                        Timber.d("room = ${it.value}")
                        it.getValue(String::class.java) as String
                    }


    fun listenForPairedDevicesInRoom(roomUuid: String): Flowable<ChildEvent<DataSnapshot>> =
            firebaseDatabase.getReference(PAIRED_ROOMS_PATH)
                    .child(roomUuid)
                    .rxChildEvents()


    fun updateThisDeviceData(pairedDevice: PairedDevice) {
        Timber.d("updateThisDeviceData = $pairedDevice")
        Completable.create { emitter ->
            val reference = firebaseDatabase.getReference(PAIRED_ROOMS_PATH)
                    .child(pairedDevice.roomUUID)
                    .child(App.CURRENT_DEVICE_UUID)
            reference.setValue(pairedDevice).addOnCompleteListener { emitter.onComplete() }
            reference.child(PairedDevice::online.name).onDisconnect().removeValue() // TODO TEST
            Timber.d("PairedDevice::online.name = ${PairedDevice::online.name}")
        }
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


}