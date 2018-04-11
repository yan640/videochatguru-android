package co.netguru.android.chatandroll.data.firebase

import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.ChildEvent
import co.netguru.android.chatandroll.common.extension.rxChildEvents
import co.netguru.android.chatandroll.common.extension.rxSingleValue
import co.netguru.android.chatandroll.data.model.Child
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by yan-c_000 on 27.03.2018.
 */
@Singleton
class FirebaseChild @Inject constructor(private val firebaseDatabase: FirebaseDatabase ) {

    companion object {
        private const val PAIRING_PATH = "pairing/"
        private const val PAIRED_ROOMS_PATH = "paired_rooms/"
        private const val CHILD = "child/"

    }


    private lateinit var childReference: DatabaseReference


    /**
     * Add you device info to Firebase path [PAIRING_PATH]/[CURRENT_WIFI_BSSID]
     */



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


    fun listenChildFolder(roomUuid: String  ): Single< DataSnapshot > =
            firebaseDatabase.getReference(PAIRED_ROOMS_PATH)
                    .child(roomUuid)
                    .child(CHILD)
                    .rxSingleValue()



    fun listenRoom(roomUuid: String): Flowable<ChildEvent<DataSnapshot>> =
            firebaseDatabase.getReference(PAIRED_ROOMS_PATH )
                    .child(roomUuid)
                    .child(CHILD)
                    .rxChildEvents()


    fun disconnect(): Completable = Completable.fromAction {
        firebaseDatabase.goOffline()
    }

    fun connect(): Completable = Completable.fromAction {
        firebaseDatabase.goOnline()
    }


}