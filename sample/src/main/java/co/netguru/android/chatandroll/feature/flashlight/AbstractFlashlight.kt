package co.netguru.android.chatandroll.feature.flashlight.flashlight

import co.netguru.android.chatandroll.feature.flashlight.Flashlight
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject

/**
 * Abstract implementation of flashlight. It implements onInitialized property.
 */
abstract class AbstractFlashlight: Flashlight {

    protected val _onInitialized: Subject<Unit> by lazy { newOnInitialized() }

    override val onInitialized: Observable<Unit>
        get() = _onInitialized

    open protected fun newOnInitialized(): Subject<Unit> = PublishSubject.create<Unit>()
}