package co.netguru.android.chatandroll.app

import co.netguru.android.chatandroll.data.firebase.FirebaseModule
import co.netguru.android.chatandroll.feature.main.central.FragmentComponent
import co.netguru.android.chatandroll.feature.main.central.FragmentModule
import co.netguru.android.chatandroll.feature.main.video.VideoFragmentComponent
import co.netguru.android.chatandroll.webrtc.service.WebRtcServiceComponent
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = arrayOf(ApplicationModule::class, FirebaseModule::class))  // зависимости из каких модулей мы объеденяем
interface ApplicationComponent {

    //<editor-fold desc="инициализация дочерных компонентов (Subcomponent)">
    fun videoFragmentComponent(): VideoFragmentComponent

    fun fragmentComponent(fragmentModule: FragmentModule): FragmentComponent

    fun webRtcServiceComponent(): WebRtcServiceComponent

    //</editor-fold>

}
