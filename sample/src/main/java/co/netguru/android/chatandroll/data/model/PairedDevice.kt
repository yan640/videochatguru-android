package co.netguru.android.chatandroll.data.model

/**
 * Created by Gleb on 23.02.2018.
 */
data class PairedDevice(val uuid: String = "",
                        val deviceName: String = "",
                        var role: Role = Role.UNDEFINED,
                        var online: Boolean = false,
                        var childName: String = "",
                        val roomUUID: String = "",
                        val whoConfirmed: String = "")