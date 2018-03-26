package co.netguru.android.chatandroll.feature.main.central

import co.netguru.android.chatandroll.common.di.FragmentScope
import dagger.Subcomponent

/**
 * Created by Gleb on 25.03.2018.
 */
@FragmentScope
@Subcomponent  (modules = [FragmentModule::class])//Если нужны зависимости только от родительского модуля, то ничего
interface FragmentComponent  {
    fun inject(centralFragment: CentralFragment)

    fun centralFragmentPresenter():CentralFragmentPresenter


}