package co.netguru.android.chatandroll.feature.main.central

import co.netguru.android.chatandroll.feature.base.BaseMvpFragment

/**
 * Created by Gleb on 23.03.2018.
 */
class CentralFragment:BaseMvpFragment<CentralFragmentView,CentralFragmentPresenter>(), CentralFragmentView {

    companion object {
        val TAG: String = CentralFragment::class.java.name
    }
    override fun retrievePresenter(): CentralFragmentPresenter {
       // App.getApplicationComponent(context).videoFragmentPresenter()
            TODO()
    }

    override fun getLayoutId(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}