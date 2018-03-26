package co.netguru.android.chatandroll.feature.main.video

import co.netguru.android.chatandroll.common.di.FragmentScope
import dagger.Subcomponent

@FragmentScope
@Subcomponent
interface VideoFragmentComponent {
    fun inject(videoFragment: VideoFragment)  // Объявляет в какой класс мы хотим делать injection, название произвольное, главное - тип


    fun videoFragmentPresenter(): VideoFragmentPresenter
}