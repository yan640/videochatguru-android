package co.netguru.android.chatandroll.data.model

/**
 * Created by yan-c_000 on 27.03.2018.
 */
data class Child(var childName: String = "",
                 val key: String = "",
                 val phoneUuid: String = "",
                 val phoneModel: String = "",
                 var online: Boolean = false,
                 var useFrontCamera: Boolean = false,
                 var useFlashLight: Boolean = false)

