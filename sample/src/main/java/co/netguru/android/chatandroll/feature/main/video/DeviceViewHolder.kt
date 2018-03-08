package co.netguru.android.chatandroll.feature.main.video

import android.support.v7.widget.RecyclerView
import android.view.View
import co.netguru.android.chatandroll.data.model.PairedDevice
import kotlinx.android.synthetic.main.item_paired_device.view.*


/**
 * Created by Gleb on 07.03.2018.
 */
class DeviceViewHolder(val view: View, val onButtonClick: (PairedDevice) -> Unit) : RecyclerView.ViewHolder(view) {

    fun bindDevice(device: PairedDevice) = with(device) {
        view.childNameTextView.text = childName
        view.deviceNameTextView.text = deviceName
        view.statusTextView.text = status
        view.connectButton.setOnClickListener { onButtonClick }
    }


}