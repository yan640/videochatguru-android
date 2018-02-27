package co.netguru.android.chatandroll.data.model


/**
 * Created by Gleb on 23.02.2018.
 */
data class PairedDevice(val uuid:String="",
                        val name:String="",
                        val role:String="",
                        val roomName:String="",
                        val isConfirmed :Boolean=false) // TODO сделать ENUM для role
