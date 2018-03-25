package co.netguru.android.chatandroll.feature.main

import android.os.Bundle
import co.netguru.android.chatandroll.R
import co.netguru.android.chatandroll.feature.base.BaseActivity
import co.netguru.android.chatandroll.feature.main.video.ChildFragment
import co.netguru.android.chatandroll.feature.main.video.VideoFragment

/**
 * Created by yan-c_000 on 22.03.2018.
 */
class ChildActivity : BaseActivity() {

    private val childFragment = ChildFragment.newInstance()

    override fun getLayoutId() = R.layout.activity_child

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            getReplaceFragmentTransaction(R.id.fragmentContainer, childFragment, ChildFragment.TAG).commit()
        }

    }
}