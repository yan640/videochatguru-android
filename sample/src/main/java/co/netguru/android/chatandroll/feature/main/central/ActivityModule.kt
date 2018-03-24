package co.netguru.android.chatandroll.feature.main.central

import co.netguru.android.chatandroll.common.di.ActivityScope
import co.netguru.android.chatandroll.data.firebase.FirebasePairingWifi
import dagger.Module
import dagger.Provides

/**
 * Created by Gleb on 24.03.2018.
 */
@Module
class ActivityModule {

    @Provides @ActivityScope
    fun provideCentralFragmentPresenter(firebasePairingWifi: FirebasePairingWifi)
            = CentralFragmentPresenter(firebasePairingWifi)
}