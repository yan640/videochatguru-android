package co.netguru.android.chatandroll.feature.main.video

import co.netguru.android.chatandroll.common.di.FragmentScope
import dagger.Subcomponent

@FragmentScope
@Subcomponent
interface ChildFragmentComponent {
    fun inject(childFragment: ChildFragment)

    fun childFragmentPresenter(): ChildFragmentPresenter
}