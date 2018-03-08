package co.netguru.android.chatandroll.data.firebase

import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.ChildEvent
import co.netguru.android.chatandroll.common.extension.ChildEventAdded
import co.netguru.android.chatandroll.common.extension.rxChildEvents
import co.netguru.android.chatandroll.common.extension.rxValueEvents
import co.netguru.android.chatandroll.data.model.DeviceInfoFirebase
import co.netguru.android.chatandroll.data.model.PairedDevice
import co.netguru.android.chatandroll.feature.main.video.VideoFragment
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.rxkotlin.ofType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by yan-c_000 on 06.02.2018.
 * Rewritten by Gleb 21.02.2018
 */
@Singleton
class FirebasePairingWifi @Inject constructor(private val firebaseDatabase: FirebaseDatabase) {

    companion object {
        private const val WIFI_PAIRING_PATH = "wifi_pairing_devices/"
        private const val PAIRED_PATH = "paired_devices/"
        private const val DEVICE_TO_ROOM_PATH = "device_to_room/"
        const val ROOM_DELETED = "ROOM_DELETED"


    }

    private val pairingDevicesPath: String
        get() = WIFI_PAIRING_PATH + VideoFragment.CURRENT_WIFI_BSSID
    //"TEST_WIFI"//


    private val myDevice = DeviceInfoFirebase(App.CURRENT_DEVICE_UUID, App.model)

    private lateinit var pairingReferenceThisDevice: DatabaseReference


    /**
     * Add you device info to FDB folder [WIFI_PAIRING_PATH]/[CURRENT_WIFI_BSSID]
     */
    fun addDeviceToPairingFolder(): Completable = Completable.create { emitter ->
        pairingReferenceThisDevice = firebaseDatabase
                .getReference(pairingDevicesPath)
                .child(App.CURRENT_DEVICE_UUID)
        with(pairingReferenceThisDevice) {
            onDisconnect().removeValue()
            setValue(myDevice).addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
            // TODO удалить listener по завершению
        }
        emitter.onComplete()
    }

    fun removerThisDeviceFromPairing(): Completable = Completable.create { emitter ->
        pairingReferenceThisDevice.removeValue()
                .addOnCompleteListener { emitter.onComplete() }
                .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
    }

    /**
     * Get Flowable with all ready for pairing devices in your wifi
     */
    fun listenPairiFolder(): Flowable<DeviceInfoFirebase> =
            firebaseDatabase.getReference(pairingDevicesPath)
                    .rxChildEvents()
                    .ofType<ChildEventAdded<DataSnapshot>>()
                    .map { it.data.getValue(DeviceInfoFirebase::class.java)!! }
                    .filter { it != myDevice }


    fun listenForDeviceEscaping(device: DeviceInfoFirebase): Completable =
            firebaseDatabase.getReference(pairingDevicesPath)
                    .child(device.uuid)
                    .rxValueEvents()
                    .filter { it.value == null }  // если null значит значение удалили
                    .firstElement()
                    .flatMapCompletable { Completable.complete() }


    fun disconnect(): Completable = Completable.fromAction {
        firebaseDatabase.goOffline()    // TODO нужно где-то использовать
    }

    fun connect(): Completable = Completable.fromAction {
        firebaseDatabase.goOnline()
    }


    fun listenForOtherConfirmedPairing(otherDevice: DeviceInfoFirebase): Completable =
            firebaseDatabase.getReference(PAIRED_PATH)
                    .child(choosePairedFolderName(App.CURRENT_DEVICE_UUID, otherDevice.uuid))
                    .rxChildEvents()
                    .ofType<ChildEventAdded<DataSnapshot>>()
                    .map { it.data }
                    .filter { it.value != 0 }
                    .map { it.getValue(PairedDevice::class.java) as PairedDevice }
                    .filter { it.uuid == App.CURRENT_DEVICE_UUID }
                    .firstElement()
                    .ignoreElement()


    private fun choosePairedFolderName(yourUuid: String, otherUuid: String): String {
        return if (yourUuid > otherUuid) yourUuid else otherUuid
    }


    fun saveOtherDeviceAsPaired(otherDevice: DeviceInfoFirebase): Completable = Completable.create { emitter ->
        val roomName = choosePairedFolderName(App.CURRENT_DEVICE_UUID, otherDevice.uuid)
        firebaseDatabase.getReference(PAIRED_PATH)
                .child(roomName)
                .child(otherDevice.uuid)
                .setValue(PairedDevice(
                        uuid = otherDevice.uuid,
                        deviceName = otherDevice.name,
                        roomName =  roomName,
                        whoConfirmed = App.CURRENT_DEVICE_UUID))
                .addOnCompleteListener { emitter.onComplete() }
                .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
    }

    fun saveDeviceToRoom(otherDevice: DeviceInfoFirebase): Completable = Completable.create { emitter ->
        firebaseDatabase.getReference(DEVICE_TO_ROOM_PATH)
                .child(App.CURRENT_DEVICE_UUID)
                .setValue(choosePairedFolderName(App.CURRENT_DEVICE_UUID, otherDevice.uuid))
                .addOnCompleteListener { emitter.onComplete() }
                .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
    }

    fun listenForDeviceToRoom(): Flowable<String> =
            firebaseDatabase.getReference(DEVICE_TO_ROOM_PATH)
                    .child(App.CURRENT_DEVICE_UUID)
                    .rxValueEvents()
                    .map {
                        if (it.value != null) {
                            it.getValue(String::class.java) as String
                        } else
                            ROOM_DELETED
                    }


    fun listenForPairedDevicesInRoom(roomUuid: String): Flowable<ChildEvent<DataSnapshot>> =
        firebaseDatabase.getReference(PAIRED_PATH)
                .child(roomUuid)
                .rxChildEvents()


    fun listenPairingFolder(): Flowable<ChildEvent<DataSnapshot>> =
            firebaseDatabase.getReference(WIFI_PAIRING_PATH)
                    .child(VideoFragment.CURRENT_WIFI_BSSID)
                    .rxChildEvents()

}