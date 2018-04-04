package co.netguru.android.chatandroll.data.firebase

import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.data.model.RouletteConnectionFirebase
import com.google.firebase.database.*
import io.reactivex.Completable
import io.reactivex.Maybe
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSignalingOnline @Inject constructor(private val firebaseDatabase: FirebaseDatabase) {

    companion object {
        private const val ONLINE_DEVICES_PATH = "online_devices/"
    }

    private fun deviceOnlinePath(deviceUuid: String) ="paired_devices/"+App.CURRENT_ROOM_ID +"/"+ ONLINE_DEVICES_PATH+deviceUuid


    // Obsolete, т.к. не используем Disconnect
    fun setOnlineAndRetrieveRandomDevice1(): Maybe<String> = Completable.create {
        val firebaseOnlineReference = firebaseDatabase.getReference(deviceOnlinePath(App.THIS_DEVICE_UUID))
        with(firebaseOnlineReference) {
            onDisconnect().removeValue()
            setValue(RouletteConnectionFirebase())
        }
        it.onComplete()
    }.andThen(chooseRandomDevice())

    fun setOnlineAndRetrieveRandomDevice(): Maybe<String> = Completable.create {emitter ->
        val firebaseOnlineReference = firebaseDatabase.getReference(deviceOnlinePath(App.THIS_DEVICE_UUID))
        firebaseOnlineReference.setValue(RouletteConnectionFirebase())
                .addOnCompleteListener { emitter.onComplete() }
    }.andThen(chooseRandomDevice())

    fun disconnectRightWay(): Completable = Completable.create {emitter ->
        val firebaseOnlineReference = firebaseDatabase.getReference(deviceOnlinePath(App.THIS_DEVICE_UUID))
        firebaseOnlineReference.removeValue()
                .addOnCompleteListener { emitter.onComplete() }
    }



    fun disconnect(): Completable = Completable.fromAction {
        firebaseDatabase.goOffline()
    }

    fun connect(): Completable = Completable.fromAction {
        firebaseDatabase.goOnline()
    }

    private fun chooseRandomDevice(): Maybe<String> = Maybe.create { //TODO change chooseRandomDevice()
        var lastUuid: String? = null

        firebaseDatabase.getReference(deviceOnlinePath("")).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                lastUuid = null
                val genericTypeIndicator = object : GenericTypeIndicator<MutableMap<String, RouletteConnectionFirebase>>() {}
                val availableDevices = mutableData.getValue(genericTypeIndicator) ?:
                        return Transaction.success(mutableData)

                val removedSelfValue = availableDevices.remove(App.THIS_DEVICE_UUID)

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