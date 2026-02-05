package com.matanh.transfer.util

import android.content.Context
import android.widget.ArrayAdapter
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.matanh.transfer.R

/**
 * A dropdown adapter showing labeled IP entries in the list, but only the raw value when closed.
 */
class IpEntryAdapter(
    context: Context,
    entries: List<IpEntry> = emptyList()
) : ArrayAdapter<IpEntry>(
    context,
    android.R.layout.simple_dropdown_item_1line,
    entries.toMutableList()
) {

    private fun bind(view: TextView, position: Int) {
        val item = getItem(position) ?: return
        view.text = context.getString(
            R.string.ip_entry_format,
            item.label,
            item.value
        )
    }

    override fun getDropDownView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View =
        (super.getDropDownView(position, convertView, parent) as TextView).also {
            bind(it, position)
        }

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View =
        (super.getView(position, convertView, parent) as TextView).also {
            bind(it, position)
        }
}