package co.netguru.android.chatandroll.feature.main.child

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import co.netguru.android.chatandroll.R
import co.netguru.android.chatandroll.data.model.Child
import kotlinx.android.synthetic.main.item_paired_device.view.*

class ChildAdapter(val childrens: List<Child>,
                           val onButtonClick: (Child) -> Unit) :
        RecyclerView.Adapter<ChildAdapter.ChildViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ChildViewHolder {
        val view = LayoutInflater
                .from(parent?.context)
                .inflate(R.layout.item_child, parent, false)
        return ChildViewHolder(view, onButtonClick)
    }

    override fun getItemCount() = childrens.size

    override fun onBindViewHolder(holder: ChildViewHolder?, position: Int) {
        holder?.bindDevice(childrens[position])
    }

    class ChildViewHolder(val view: View,
                           private val onButtonClick: (Child) -> Unit)
        : RecyclerView.ViewHolder(view) {

        fun bindDevice(child: Child) = with(child) {
            view.childNameTextView.text = childName

            view.statusTextView.text = if (online) "Online" else "Offline"  // TODO добавить красн или зел цвет
            view.connectButton.isEnabled = online
            view.connectButton.setOnClickListener { onButtonClick(this) }
        }


    }
}