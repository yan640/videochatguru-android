package co.netguru.android.chatandroll.feature.main.video

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import co.netguru.android.chatandroll.R
import co.netguru.android.chatandroll.data.model.PairedDevice

/**
 * Created by Gleb on 07.03.2018.
 */
class PairedDevicesAdapter(val devices: List<PairedDevice>,
                           val onButtonClick:(PairedDevice) -> Unit):
    RecyclerView.Adapter<DeviceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater
                .from(parent?.context)
                .inflate(R.layout.item_paired_device, parent, false)
        return DeviceViewHolder(view, onButtonClick)
    }

    override fun getItemCount() = devices.size

    override fun onBindViewHolder(holder: DeviceViewHolder?, position: Int) {
        holder?.bindDevice(devices[position])
    }
}