package co.netguru.android.chatandroll.feature.main

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Toast
import co.netguru.android.chatandroll.R
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.feature.base.BaseActivity
import co.netguru.android.chatandroll.feature.main.video.VideoFragment
import org.webrtc.ContextUtils
import timber.log.Timber


class MainActivity : BaseActivity() {

    private val videoFragment = VideoFragment.newInstance()

    override fun getLayoutId() = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            getReplaceFragmentTransaction(R.id.fragmentContainer, videoFragment, VideoFragment.TAG).commit()
        }

    }
}