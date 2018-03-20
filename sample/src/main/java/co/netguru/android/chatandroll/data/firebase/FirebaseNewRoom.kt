package co.netguru.android.chatandroll.data.firebase

import co.netguru.android.chatandroll.app.App
import com.google.firebase.database.FirebaseDatabase
import io.reactivex.Completable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by yan-c_000 on 11.02.2018.
 */
@Singleton
class FirebaseNewRoom @Inject constructor(private val firebaseDatabase: FirebaseDatabase) {

    companion object {
        private const val PHONE_ROOM = "Phone_room/"
        private const val ROOMS = "Rooms"
    }

    private fun deviceOnlinePath(deviceUuid: String) = PHONE_ROOM.plus(deviceUuid)
    private fun ROOMSPath(deviceUuid: String) = ROOMS

    fun setOnlineAndRetrieveRandomDevice(NewPhone: String) {

        var firebaseOnlineReference = firebaseDatabase.getReference(deviceOnlinePath(App.THIS_DEVICE_UUID))
        with(firebaseOnlineReference) {

            setValue(App.THIS_DEVICE_UUID)
        }
        firebaseOnlineReference = firebaseDatabase.getReference(deviceOnlinePath(NewPhone))
        with(firebaseOnlineReference) {

            setValue(App.THIS_DEVICE_UUID)
        }
        firebaseOnlineReference = firebaseDatabase.getReference(ROOMS)
        with(firebaseOnlineReference) {

            setValue(App.THIS_DEVICE_UUID)
        }

    }

    fun disconnect(): Completable = Completable.fromAction {
        firebaseDatabase.goOffline()
    }

    fun connect(): Completable = Completable.fromAction {
        firebaseDatabase.goOnline()
    }
}