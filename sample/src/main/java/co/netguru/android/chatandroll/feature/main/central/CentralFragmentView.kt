package co.netguru.android.chatandroll.feature.main.central

import co.netguru.android.chatandroll.feature.base.MvpView

interface CentralFragmentView:MvpView {
    fun showSnackbar (message:String)
    fun showCounter (counter:Int)

}
