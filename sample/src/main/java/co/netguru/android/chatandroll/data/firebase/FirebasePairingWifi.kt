package co.netguru.android.chatandroll.data.firebase

import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.ChildEventAdded
import co.netguru.android.chatandroll.common.extension.rxChildEvents
import co.netguru.android.chatandroll.data.model.DeviceInfoFirebase
import co.netguru.android.chatandroll.feature.main.video.VideoFragment
import com.google.firebase.database.DataSnapshot
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
        private const val WIFI_PAIR_DEVICES_PATH = "wifi_pair_devices/"
    }

    private fun deviceOnlinePath(deviceUuid: String) = WIFI_PAIR_DEVICES_PATH + deviceUuid

    private val pairingDevicesPath: String
        get() = WIFI_PAIR_DEVICES_PATH + VideoFragment.CURRENT_WIFI_BSSID


    /**
     * Add you device info to FDB folder [WIFI_PAIR_DEVICES_PATH]/[CURRENT_WIFI_BSSID]
     */
    fun addToFolder(): Completable = Completable.create { emitter ->
        val firebaseOnlineReference = firebaseDatabase.getReference(pairingDevicesPath)
        with(firebaseOnlineReference) {
            onDisconnect().removeValue()
            push().setValue(DeviceInfoFirebase(App.CURRENT_DEVICE_UUID, App.model))
                    .addOnFailureListener { emitter.onError(it.fillInStackTrace()) }  // TODO удалить listener по завершению
        }
        emitter.onComplete()
    }

    /**
     * Get Flowable with all ready for pairing devices in your wifi
     */
    fun checkForWaitingDevices(): Flowable<DeviceInfoFirebase> =
            firebaseDatabase.getReference(pairingDevicesPath)
                    .rxChildEvents()
                    .ofType<ChildEventAdded<DataSnapshot>>()  // TODO возможно возвращает  DeviceInfoFirebase
                    .map { it.data.getValue(DeviceInfoFirebase::class.java)!! }

    fun disconnect(): Completable = Completable.fromAction {
        firebaseDatabase.goOffline()    // TODO нужно где-то использовать
    }

    fun connect(): Completable = Completable.fromAction {
        firebaseDatabase.goOnline()
    }

}