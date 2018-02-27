package co.netguru.android.chatandroll.data.firebase

import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.ChildEventAdded
import co.netguru.android.chatandroll.common.extension.rxChildEvents
import co.netguru.android.chatandroll.data.model.DeviceInfoFirebase
import co.netguru.android.chatandroll.data.model.PairedDevice
import co.netguru.android.chatandroll.feature.main.video.VideoFragment
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
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

    }

    private fun deviceOnlinePath(deviceUuid: String) = WIFI_PAIRING_PATH + deviceUuid

    private val pairingDevicesPath: String
        get() = WIFI_PAIRING_PATH + VideoFragment.CURRENT_WIFI_BSSID


    private val myDevice = DeviceInfoFirebase(App.CURRENT_DEVICE_UUID, App.model)

    private lateinit var paingReferenceThisDevice: DatabaseReference


    /**
     * Add you device info to FDB folder [WIFI_PAIRING_PATH]/[CURRENT_WIFI_BSSID]
     */
    fun addDeviceToPairingFolder(): Completable = Completable.create { emitter ->
        val pairingReferenceAll = firebaseDatabase.getReference(pairingDevicesPath)
        val key = pairingReferenceAll
                .push()
                .key
        paingReferenceThisDevice = pairingReferenceAll.child(key)
        with(paingReferenceThisDevice) {
            onDisconnect().removeValue()
            setValue(myDevice).addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
            // TODO удалить listener по завершению
        }
        emitter.onComplete()
    }

    fun removerThisDeviceFromFolder(): Completable = Completable.create { emitter ->
        paingReferenceThisDevice.removeValue()
                .addOnCompleteListener { emitter.onComplete() }
                .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
    }

    /**
     * Get Flowable with all ready for pairing devices in your wifi
     */
    fun checkForWaitingDevices(): Flowable<DeviceInfoFirebase> =
            firebaseDatabase.getReference(pairingDevicesPath)
                    .rxChildEvents()
                    .ofType<ChildEventAdded<DataSnapshot>>()  // TODO возможно возвращает  DeviceInfoFirebase
                    .map { it.data.getValue(DeviceInfoFirebase::class.java)!! }
                    .filter { it != myDevice }

    fun disconnect(): Completable = Completable.fromAction {
        firebaseDatabase.goOffline()    // TODO нужно где-то использовать
    }

    fun connect(): Completable = Completable.fromAction {
        firebaseDatabase.goOnline()
    }

    fun listenForOtherConfirmedPairing(otherDevice: DeviceInfoFirebase): Maybe<PairedDevice> =
            //  val pairingReferenceOther =
            firebaseDatabase
                    .getReference(PAIRED_PATH)
                    .child(choosePairedFolderName(App.CURRENT_DEVICE_UUID, otherDevice.uuid))
                    .rxChildEvents()
                    .ofType<ChildEventAdded<DataSnapshot>>()
                    .map { it.data.getValue(PairedDevice::class.java)!! } //TODO подумать как обойтись без!!
                    .filter { it.uuid == App.CURRENT_DEVICE_UUID }
                    .firstElement()



    private fun choosePairedFolderName(yourUuid: String, otherUuid: String): String {
        return if (yourUuid > otherUuid) yourUuid else otherUuid      // TODO возможноо идет сравение по длине, проверить
    }

    fun addOtherDeviceAsConfirmed(otherDevice: DeviceInfoFirebase): Completable = Completable.create { emitter ->
        val roomName = choosePairedFolderName(App.CURRENT_DEVICE_UUID, otherDevice.uuid)
        firebaseDatabase.getReference(PAIRED_PATH)
                .child(roomName)
                .push()
                .setValue(PairedDevice(otherDevice.uuid, otherDevice.name, "", roomName, true))
                .addOnCompleteListener { emitter.onComplete() }
    }

    fun saveDeviceToRoom(roomName: String): Completable = Completable.create { emitter ->
        firebaseDatabase.getReference(DEVICE_TO_ROOM_PATH)
                .child(App.CURRENT_DEVICE_UUID)
                .setValue(roomName)
                .addOnCompleteListener { emitter.onComplete() }
                .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }

    }


}