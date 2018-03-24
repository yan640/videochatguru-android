package co.netguru.android.chatandroll.feature.main.central

import android.os.Bundle
import co.netguru.android.chatandroll.R
import co.netguru.android.chatandroll.feature.base.BaseActivity

/**
 * Created by Gleb on 24.03.2018.
 */
class CentralActivity : BaseActivity() {
    override fun getLayoutId() = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null)
            getReplaceFragmentTransaction(R.id.container, CentralFragment(), CentralFragment.TAG)
    }
}