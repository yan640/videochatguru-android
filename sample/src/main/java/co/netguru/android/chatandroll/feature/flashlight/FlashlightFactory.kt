package co.netguru.android.chatandroll.feature.flashlight

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import co.netguru.android.chatandroll.feature.extensions.isLollipop
import co.netguru.android.chatandroll.feature.extensions.isMarshmallowAndUpper
import co.netguru.android.chatandroll.feature.flashlight.flashlight.LollipopFlashlight
import co.netguru.android.chatandroll.feature.flashlight.flashlight.MarshmallowFlashlight
import co.netguru.android.chatandroll.feature.flashlight.flashlight.PreLollipopFlashlight

/**
 * Factory that creates flashlight depending on Android version.
 */
object FlashlightFactory {

    /**
     * Create a new instance of flashlight.
     */
    @SuppressLint("NewApi")
    fun newInstance(context: Context, sdkVersion: Int = Build.VERSION.SDK_INT): Flashlight {
        return when {
            sdkVersion.isLollipop() -> LollipopFlashlight(context)
            sdkVersion.isMarshmallowAndUpper() -> MarshmallowFlashlight(context)
            else -> PreLollipopFlashlight()
        }
    }
}