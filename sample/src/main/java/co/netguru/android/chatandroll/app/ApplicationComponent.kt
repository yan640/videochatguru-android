package co.netguru.android.chatandroll.app

import co.netguru.android.chatandroll.data.firebase.FirebaseModule
import co.netguru.android.chatandroll.feature.main.video.VideoFragment
import co.netguru.android.chatandroll.feature.main.video.VideoFragmentComponent
import co.netguru.android.chatandroll.feature.main.video.VideoFragmentPresenter
import co.netguru.android.chatandroll.webrtc.service.WebRtcServiceComponent
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = arrayOf(ApplicationModule::class, FirebaseModule::class))  // зависимости из каких модулей мы объеденяем
interface ApplicationComponent {

    fun videoFragmentComponent(): VideoFragmentComponent  // инициализация дочерных компонентов (Subcomponent)
    fun inject(videoFragment: VideoFragment)  // Объявляет в какой класс мы хотим делать injection, название произвольное, главное - тип


    fun videoFragmentPresenter(): VideoFragmentPresenter

    fun webRtcServiceComponent(): WebRtcServiceComponent
}
