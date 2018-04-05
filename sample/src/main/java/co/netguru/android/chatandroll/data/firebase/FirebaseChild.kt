package co.netguru.android.chatandroll.data.firebase

import android.content.Context
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.ChildEvent
import co.netguru.android.chatandroll.common.extension.ChildEventAdded
import co.netguru.android.chatandroll.common.extension.rxChildEvents
import co.netguru.android.chatandroll.common.extension.rxSingleValue
import co.netguru.android.chatandroll.data.model.Child
import co.netguru.android.chatandroll.data.model.PairedDevice
import co.netguru.android.chatandroll.data.model.PairingDevice
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.rxkotlin.ofType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by yan-c_000 on 27.03.2018.
 */
@Singleton
class FirebaseChild @Inject constructor(private val firebaseDatabase: FirebaseDatabase,
                                              private val appContext: Context) {

    companion object {
        private const val PAIRING_PATH = "pairing/"
        private const val PAIRED_ROOMS_PATH = "paired_rooms/"
        private const val ROOM_REFERENCE_PATH = "room_reference/"
        private const val DEVICES = "devices/"
        private const val CHILD = "child/"
        const val ROOM_DELETED = "ROOM_DELETED"

    }


    private lateinit var pairingReferenceThisDevice: DatabaseReference
    private lateinit var childReference: DatabaseReference
    private val app: App by lazy { App.get(appContext) }


    /**
     * Add you device info to Firebase path [PAIRING_PATH]/[CURRENT_WIFI_BSSID]
     */
    fun addDeviceToPairing(wifiBSSID: String): Completable = Completable.create { emitter ->
        pairingReferenceThisDevice = firebaseDatabase
                .getReference(PAIRING_PATH)
                .child(wifiBSSID)
                .child(App.THIS_DEVICE_UUID)
        with(pairingReferenceThisDevice) {
            onDisconnect().removeValue()
            setValue(PairingDevice(App.THIS_DEVICE_UUID, App.THIS_DEVICE_MODEL))
                    .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
        }
        emitter.onComplete()
    }


    fun removeThisDeviceFromPairing(): Completable = Completable.create { emitter ->
        pairingReferenceThisDevice.removeValue()
                .addOnCompleteListener { emitter.onComplete() }
                .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
    }


    fun listenPairingFolder(wifiBSSID: String): Flowable<ChildEvent<DataSnapshot>> =
            firebaseDatabase.getReference(PAIRING_PATH)
                    .child(wifiBSSID)
                    .rxChildEvents()


    private fun choosePairedFolderName(yourUuid: String, otherUuid: String): String {
        return if (yourUuid > otherUuid) yourUuid else otherUuid
    }

    fun getKeyForNewChild( )      : Single< String > = Single.create { emitter ->
    val key = firebaseDatabase.getReference(PAIRED_ROOMS_PATH )
    .child(App.CURRENT_ROOM_ID)
    .child(CHILD)
    .push()
    .key
    emitter.onSuccess(key)
    }

    fun saveThisChildInPaired(childName: String,childKey: String  ):
            Completable = Completable.create { emitter ->

        firebaseDatabase.getReference(PAIRED_ROOMS_PATH )
                .child(App.CURRENT_ROOM_ID)
                .child(CHILD)
                .child(childKey)
                .setValue(Child(
                        key = childKey,
                        childName = childName ,
                        useFrontCamera  = false,
                        useFlashLight = false))
                .addOnCompleteListener { emitter.onComplete() }
                .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
    }



    fun saveChildSetting(child: Child ): Completable = Completable.create { emitter ->
        childReference = firebaseDatabase.getReference(PAIRED_ROOMS_PATH )
                .child(App.CURRENT_ROOM_ID)
                .child(CHILD)
                .child(child.key)
        childReference
                .setValue(child)
                .addOnCompleteListener { emitter.onComplete() }
                .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }

    }


//        childReference.child(Child::online.name).onDisconnect().removeValue() // при отключении удаляет статус online, а default value = offline
//        childReference.child(Child::phoneModel.name).onDisconnect().removeValue() // при отключении удаляет phoneModel, а default value = ""
//        childReference.child(Child::phoneUuid.name).onDisconnect().removeValue() // при отключении удаляет phoneUuid, а default value = ""



    fun setChildOffline(child: Child ): Completable = Completable.create { emitter ->
        childReference = firebaseDatabase.getReference(PAIRED_ROOMS_PATH )
                .child(App.CURRENT_ROOM_ID)
                .child(CHILD)
                .child(child.key)

        childReference.child(Child::online.name).removeValue() // при отключении удаляет статус online, а default value = offline
        childReference.child(Child::phoneModel.name).removeValue() // при отключении удаляет phoneModel, а default value = ""
        childReference.child(Child::phoneUuid.name).removeValue().addOnCompleteListener { emitter.onComplete() }
                .addOnFailureListener { emitter.onError(it.fillInStackTrace()) } // при отключении удаляет phoneUuid, а default value = ""

    }





    /**
     * Порождает Completable.complete если в [PAIRED_ROOMS_PATH] /room
     * 1)добавляется устойство
     * 2)с uuid = pairingCandidate.uuid
     * 3)c нашем устройством в listOfConfirmedDevises
     **/
    fun listenForPairingCandidateConfirmed(pairingCandidate: PairingDevice): Completable =
            firebaseDatabase.getReference(PAIRED_ROOMS_PATH+DEVICES)
                    .child(choosePairedFolderName(App.THIS_DEVICE_UUID, pairingCandidate.uuid))
                    .rxChildEvents()
                    .ofType<ChildEventAdded<DataSnapshot>>()                                //(1)
                    .map { it.data }
                    .filter { it.value != 0 }
                    .map { it.getValue(PairedDevice::class.java) as PairedDevice }
                    .filter { it.uuid == pairingCandidate.uuid }                           // (2)
                    .filter { it.listOfConfirmedDevices.contains(App.THIS_DEVICE_UUID) }   // (3)
                    .firstElement()
                    .ignoreElement()


    fun removeThisDeviceFromPaired(pairingCandidate: PairingDevice): Completable =
            Completable.create { emitter ->
                val roomName = choosePairedFolderName(App.THIS_DEVICE_UUID, pairingCandidate.uuid)
                firebaseDatabase.getReference(PAIRED_ROOMS_PATH+DEVICES)
                        .child(roomName)
                        .child(App.THIS_DEVICE_UUID)
                        .removeValue()
                        .addOnCompleteListener { emitter.onComplete() }
                        .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
            }


    fun saveRoomReference(otherDevice: PairingDevice): Completable = Completable.create { emitter ->
        firebaseDatabase.getReference(ROOM_REFERENCE_PATH )
                .child(App.THIS_DEVICE_UUID)
                .setValue(choosePairedFolderName(App.THIS_DEVICE_UUID, otherDevice.uuid))
                .addOnCompleteListener { emitter.onComplete() }
                .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
    }
//    fun listenRoomReference(roomUuid: String  ): Flowable< Child > =
//            firebaseDatabase.getReference(ROOM_REFERENCE_PATH)
//                    .child(roomUuid)
//                    .child(CHILD)
//                    .rxValueEvents()
//                    .filter { it.value != null }
//                    .map { it.getValue(Child::class.java) as Child }

//    fun listenChildFolder(roomUuid: String  ): Flowable< DataSnapshot > =
//            firebaseDatabase.getReference(ROOM_REFERENCE_PATH)
//                    .child(roomUuid)
//                    .child(CHILD).rxValueEvents()
//
//                   // .filter { it.value != null }
//                    .map { it   }


    fun listenChildFolder(roomUuid: String  ): Single< DataSnapshot > =
            firebaseDatabase.getReference(PAIRED_ROOMS_PATH)
                    .child(roomUuid)
                    .child(CHILD)
                    .rxSingleValue()



//    fun listenChildFolder(roomUuid: String  ): Flowable< DataSnapshot > =
//            firebaseDatabase.getReference(ROOM_REFERENCE_PATH)
//                    .child(roomUuid)
//                    .child(CHILD)
//                    .rxValueEvents()
//                    // .filter { it.value != null }
//                    .map { it   }
//                    .filter { it.value != null }
//                    .map { it.getValue(String::class.java) as String }


    fun listenRoom(roomUuid: String): Flowable<ChildEvent<DataSnapshot>> =
            firebaseDatabase.getReference(PAIRED_ROOMS_PATH )
                    .child(roomUuid)
                    .child(CHILD)
                    .rxChildEvents()



    fun updateThisDeviceData(pairedDevice: PairedDevice): Completable =
            Completable.create { emitter ->
                val reference = firebaseDatabase.getReference(PAIRED_ROOMS_PATH+DEVICES)
                        .child(pairedDevice.roomUUID)
                        .child(App.THIS_DEVICE_UUID)
                reference.setValue(pairedDevice)
                        .addOnCompleteListener { emitter.onComplete() }
                        .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }
                reference.child(PairedDevice::online.name).onDisconnect().removeValue() // при отключении удаляет статус online, а default value = offline
            }


    fun disconnect(): Completable = Completable.fromAction {
        firebaseDatabase.goOffline()
    }

    fun connect(): Completable = Completable.fromAction {
        firebaseDatabase.goOnline()
    }


}