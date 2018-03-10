package co.netguru.android.chatandroll.feature.main.video

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import co.netguru.android.chatandroll.R
import co.netguru.android.chatandroll.data.model.PairedDevice
import kotlinx.android.synthetic.main.item_paired_device.view.*

/**
 * Created by Gleb on 07.03.2018.
 */
class PairedDevicesAdapter(val devices: List<PairedDevice>,
                           val onButtonClick: (PairedDevice) -> Unit) :
        RecyclerView.Adapter<PairedDevicesAdapter.DeviceViewHolder>() {

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

    class DeviceViewHolder(val view: View,
                           private val onButtonClick: (PairedDevice) -> Unit)
        : RecyclerView.ViewHolder(view) {

        fun bindDevice(device: PairedDevice) = with(device) {
            view.childNameTextView.text = childName
            view.deviceNameTextView.text = deviceName
            view.statusTextView.text = if (online) "Online" else "Offline"  // TODO добавить красн или зел цвет
            view.connectButton.isEnabled = online
            view.connectButton.setOnClickListener { onButtonClick(this) }
        }


    }
}