package co.netguru.android.chatandroll.feature.main.child

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import co.netguru.android.chatandroll.R
import co.netguru.android.chatandroll.data.model.Child
import kotlinx.android.synthetic.main.item_paired_device.view.*

class ChildAdapter(val childrens: List<Child>,
                   val clickListener: (Child) -> Unit) :
        RecyclerView.Adapter<ChildAdapter.ChildViewHolder>() {
    private var mObjects: List<Child> = childrens

    init {
        mObjects = childrens
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ChildViewHolder {
        val view = LayoutInflater
                .from(parent?.context)
                .inflate(R.layout.item_child, parent, false)
        return ChildViewHolder(view, clickListener)
    }

    override fun getItemCount() = childrens.size

    override fun onBindViewHolder(holder: ChildViewHolder?, position: Int) {
        holder?.bindDevice(childrens[position])
        var item: Child = childrens[position]

        // Calling the clickListener sent by the constructor
        holder?.view?.setOnClickListener {
            clickListener(item)
        }
    }

    class ChildViewHolder(val view: View,
                          private val clickListener: (Child) -> Unit)
        : RecyclerView.ViewHolder(view) {

        fun bindDevice(child: Child) = with(child) {
            view.childNameTextView.text = childName

            view.statusTextView.text = if (online) {"Online on "+child.phoneModel} else "Offline"  // TODO добавить красн или зел цвет
            //view.connectButton.isEnabled = online

            // view.connectButton.setOnClickListener { clickListener(this) }
        }


    }
}