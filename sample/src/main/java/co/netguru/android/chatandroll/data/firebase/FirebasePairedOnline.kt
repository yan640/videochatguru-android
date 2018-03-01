package co.netguru.android.chatandroll.data.firebase

import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.ChildEventAdded
import co.netguru.android.chatandroll.common.extension.rxChildEvents
import co.netguru.android.chatandroll.common.extension.rxSingleValue
import co.netguru.android.chatandroll.common.extension.rxValueEvents
import co.netguru.android.chatandroll.data.model.DeviceInfoFirebase
import co.netguru.android.chatandroll.data.model.IceServerFirebase
import co.netguru.android.chatandroll.data.model.RouletteConnectionFirebase
import co.netguru.android.chatandroll.data.model.SessionDescriptionFirebase
import com.google.firebase.database.*
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.toMaybe
import org.webrtc.SessionDescription
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebasePairedOnline @Inject constructor(private val firebaseDatabase: FirebaseDatabase) {

    companion object {
        private const val ONLINE_DEVICES_PATH = "online_devices/"
        private const val PHONE_ROOM = "device_to_room/"
        private const val ROOMS = "Rooms"
    }

    private fun deviceOnlinePath(deviceUuid: String) = ONLINE_DEVICES_PATH.plus(deviceUuid)
    private fun Phone_roomPath(deviceUuid: String) = PHONE_ROOM.plus("125e4548-8c43-48e4-b4d7-dbc9bf55b382")
    private fun ROOMSPath(deviceUuid: String) = ROOMS
    private val firebaseNewPhoneReference by lazy { firebaseDatabase.getReference(PHONE_ROOM) }

    fun setOnlineAndRetrieveRandomDevice(): Maybe<String> = Completable.create {
        val firebaseOnlineReference = firebaseDatabase.getReference(deviceOnlinePath(App.CURRENT_DEVICE_UUID))
        with(firebaseOnlineReference) {
            onDisconnect().removeValue()
            setValue(RouletteConnectionFirebase())
        }
        it.onComplete()
    }.andThen(chooseRandomDevice())

    fun getMeNewKey(): Single<String> =   Single.create {it.onSuccess(firebaseNewPhoneReference.push().key)  }
  //  fun getMeMyRoomKey(): Single<String> =   Single.create {it.onSuccess(firebaseDatabase.getReference(Phone_roomPath(App.CURRENT_DEVICE_UUID))  }
//    fun GetRoomId(): Flowable<ChildEventAdded<String>> {
//        return firebaseDatabase.getReference(Phone_roomPath(App.CURRENT_DEVICE_UUID))
//                .rxChildEvents()
//                .ofType<ChildEventAdded<String>>()
//    }
//fun listenForNewAnswers(): Flowable<SessionDescription> {
//    return Single.just { firebaseDatabase.getReference(deviceAnswersPath(App.CURRENT_DEVICE_UUID)) }
//            .flatMapPublisher { it().rxValueEvents(SessionDescriptionFirebase::class.java) }
//            .flatMapMaybe { it.data.toMaybe() }
//            .map { it.toSessionDescription() }
//}
//    fun GetRoomId(): Flowable< String >
//            =
//            firebaseDatabase.getReference(Phone_roomPath(App.CURRENT_DEVICE_UUID))
//                    .rxChildEvents()
//                    .ofType<ChildEventAdded<DataSnapshot>>()
//                    .map { it.data.getValue( ).toString()  }

    fun GetRoomId(): Flowable< String >
            =
            firebaseDatabase.getReference(Phone_roomPath("125e4548-8c43-48e4-b4d7-dbc9bf55b382"))
                    .rxChildEvents()
                    .ofType<ChildEventAdded<DataSnapshot>>()
                    .map { it.data.getValue( ).toString()  }


    fun disconnect(): Completable = Completable.fromAction {
        firebaseDatabase.goOffline()
    }

    fun connect(): Completable = Completable.fromAction {
        firebaseDatabase.goOnline()
    }

    private fun chooseRandomDevice(): Maybe<String> = Maybe.create {
        var lastUuid: String? = null

        firebaseDatabase.getReference(ONLINE_DEVICES_PATH).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                lastUuid = null
                val genericTypeIndicator = object : GenericTypeIndicator<MutableMap<String, RouletteConnectionFirebase>>() {}
                val availableDevices = mutableData.getValue(genericTypeIndicator) ?:
                return Transaction.success(mutableData)

                val removedSelfValue = availableDevices.remove(App.CURRENT_DEVICE_UUID)

                if (removedSelfValue != null && !availableDevices.isEmpty()) {
                    lastUuid = deleteRandomDevice(availableDevices)
                    mutableData.value = availableDevices
                }

                return Transaction.success(mutableData)
            }

            private fun deleteRandomDevice(availableDevices: MutableMap<String, RouletteConnectionFirebase>): String {
                val devicesCount = availableDevices.count()
                val randomDevicePosition = SecureRandom().nextInt(devicesCount)
                val randomDeviceToRemoveUuid = availableDevices.keys.toList()[randomDevicePosition]
                Timber.d("Device number $randomDevicePosition from $devicesCount devices was chosen.")
                availableDevices.remove(randomDeviceToRemoveUuid)
                return randomDeviceToRemoveUuid
            }

            override fun onComplete(databaseError: DatabaseError?, completed: Boolean, p2: DataSnapshot?) {
                if (databaseError != null) {
                    it.onError(databaseError.toException())
                } else if (completed && lastUuid != null) {
                    it.onSuccess(lastUuid as String)
                }
                it.onComplete()
            }
        })
    }
}