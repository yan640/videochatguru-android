package co.netguru.android.chatandroll.feature.main.central

import android.content.Context
import co.netguru.android.chatandroll.data.firebase.FirebasePairingWifi
import co.netguru.android.chatandroll.feature.base.BasePresenter
import timber.log.Timber

class CentralFragmentPresenter(val context: Context,
                               val firebasePairingWifi: FirebasePairingWifi) :
        BasePresenter<CentralFragmentView>() {

    var counter = 0

    init {
        Timber.d("constructor $this")
    }


    fun onTestClick() {
        getView()?.showSnackbar("Test from presenter")
    }

    override fun attachView(mvpView: CentralFragmentView) {
        super.attachView(mvpView)
        counter++
        getView()?.showCounter(counter)
    }
}
