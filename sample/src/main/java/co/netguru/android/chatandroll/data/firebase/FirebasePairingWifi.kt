package co.netguru.android.chatandroll.data.firebase

import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.data.model.RouletteConnectionFirebase
import co.netguru.android.chatandroll.feature.main.video.VideoFragment
import com.google.firebase.database.*
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.internal.util.NotificationLite.getValue
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by yan-c_000 on 06.02.2018.
 */
@Singleton
class FirebasePairingWifi @Inject constructor(private val firebaseDatabase: FirebaseDatabase) {

    companion object {
        private const val PAIRE_DEVICES_PATH = "paire_devices/"
    }

    private fun deviceOnlinePath(deviceUuid: String) = PAIRE_DEVICES_PATH.plus(deviceUuid)

    fun setOnlineAndRetrieveRandomDevice( ): Maybe<Map<String,String> > = Completable.create {
        val firebaseOnlineReference = firebaseDatabase.getReference(deviceOnlinePath(VideoFragment.CURRENT_WIFI_BSSID+"/"+App.CURRENT_DEVICE_UUID))
        with(firebaseOnlineReference) {
            onDisconnect().removeValue()
            setValue(App.model)
        }
        it.onComplete()
    }.andThen(chooseRandomDevice())

    fun disconnect(): Completable = Completable.fromAction {
        firebaseDatabase.goOffline()
    }

    fun connect(): Completable = Completable.fromAction {
        firebaseDatabase.goOnline()
    }

    private fun chooseRandomDevice(): Maybe<Map<String,String> > = Maybe.create {
        var lastUuid: MutableData? = null
        var b :Map<String,String> = HashMap<String,String>()

        firebaseDatabase.getReference(deviceOnlinePath(VideoFragment.CURRENT_WIFI_BSSID)).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                lastUuid = null
                val genericTypeIndicator = object : GenericTypeIndicator<MutableMap<String, String>>() {}
                val availableDevices = mutableData.getValue(genericTypeIndicator) ?:



                return Transaction.success(mutableData)

               val removedSelfValue  = availableDevices.remove(App.CURRENT_DEVICE_UUID)

                if (removedSelfValue != null && !availableDevices.isEmpty()) {

                    if (  !availableDevices.isEmpty()) {
                        for (sdf in availableDevices) {
                            b+= sdf.key to sdf.value.toString()
                        }
                    }
                    mutableData.value = b

                }

                //mutableData.value = availableDevices
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
                } else if (completed && b != null) {
                    it.onSuccess(b as Map<String,String>)
                    //TODO отправить список всех телефонов (плюс название телфона)
                }
                it.onComplete()
            }
        })
    }
}