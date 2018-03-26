package co.netguru.android.chatandroll.feature.main.central

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.view.View
import co.netguru.android.chatandroll.R
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.feature.base.BaseMvpFragment
import kotlinx.android.synthetic.main.fragment_central.*
import timber.log.Timber

/**
 * Created by Gleb on 23.03.2018.
 */
class CentralFragment : BaseMvpFragment<CentralFragmentView, CentralFragmentPresenter>(), CentralFragmentView {

    @SuppressLint("Range")
    override fun showSnackbar(message: String) {
        showSnackbarMessage(message, Snackbar.LENGTH_LONG)
    }

    var isDestoyedBySystem:Boolean = false

    companion object {
        val TAG: String = CentralFragment::class.java.name
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate $this")

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        testPresenter.setOnClickListener { getPresenter().onTestClick() }

    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        isDestoyedBySystem = true

    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("onDestroy")
        if (!isDestoyedBySystem) {
            App.get(activity.application).destroyFragmentComponent()
        }
    }

    override fun onResume() {
        super.onResume()
        isDestoyedBySystem = false
    }

    override fun showCounter(counter: Int) {
        textView.text = counter.toString()
    }

    override fun retrievePresenter(): CentralFragmentPresenter =
            App.get(activity.application)
                    .getFragmentComponent()
                    .centralFragmentPresenter()

    override fun getLayoutId() = R.layout.fragment_central

}