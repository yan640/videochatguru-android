package co.netguru.android.chatandroll.feature.main.central

import android.content.Context
import co.netguru.android.chatandroll.common.di.FragmentScope
import co.netguru.android.chatandroll.data.firebase.FirebasePairingWifi
import dagger.Module
import dagger.Provides

/**
 * Created by Gleb on 24.03.2018.
 */
@Module
class FragmentModule {

    @Provides
    @FragmentScope
    fun provideCentralFragmentPresenter(
            context: Context,
            firebasePairingWifi: FirebasePairingWifi) =
            CentralFragmentPresenter(context, firebasePairingWifi)
}