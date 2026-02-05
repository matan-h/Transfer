package com.matanh.transfer.util

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.matanh.transfer.R
import com.matanh.transfer.util.FileUtils.toReadableFileSize

class FileAdapter(
    private var files: List<FileItem>,
    private val onItemClick: (FileItem, Int) -> Unit,
    private val onItemLongClick: (FileItem, Int) -> Boolean
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    private val selectedItems = mutableSetOf<Int>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvFileName)
        val tvSize: TextView = view.findViewById(R.id.tvFileSize)
        val ivSelectionCheck: ImageView = view.findViewById(R.id.ivSelectionCheck)
        val linearBody: MaterialCardView = view.findViewById(R.id.linearBody)

        fun bind(file: FileItem, position: Int, isSelected: Boolean) {
            tvName.text = file.name
            tvSize.text = file.size.toReadableFileSize()
            ivSelectionCheck.isVisible = isSelected

            linearBody.shapeAppearanceModel = createCornerShape(position)

            itemView.setOnClickListener { onItemClick(file, position) }
            itemView.setOnLongClickListener { onItemLongClick(file, position) }
        }

        private fun createCornerShape(position: Int): ShapeAppearanceModel {
            val dp = itemView.resources.displayMetrics.density
            val corners = when {
                itemCount == 1 -> listOf(18f, 18f, 18f, 18f)
                position == 0 -> listOf(18f, 18f, 6f, 6f)
                position == itemCount - 1 -> listOf(6f, 6f, 18f, 18f)
                else -> listOf(6f, 6f, 6f, 6f)
            }.map { it * dp }

            return ShapeAppearanceModel.builder().setTopLeftCorner(CornerFamily.ROUNDED, corners[0])
                .setTopRightCorner(CornerFamily.ROUNDED, corners[1])
                .setBottomLeftCorner(CornerFamily.ROUNDED, corners[2])
                .setBottomRightCorner(CornerFamily.ROUNDED, corners[3]).build()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(files[position], position, position in selectedItems)
    }

    override fun getItemCount() = files.size

    fun updateFiles(newFiles: List<FileItem>) {
        files = newFiles
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun toggleSelection(position: Int) {
        if (position in selectedItems) selectedItems.remove(position)
        else selectedItems.add(position)
        notifyItemChanged(position)
    }

    fun getSelectedFileItems() = selectedItems.map { files[it] }

    fun getSelectedItemCount() = selectedItems.size

    fun clearSelections() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun getFileItem(position: Int) = files.getOrNull(position)

    fun selectAll() {
        if (selectedItems.size == files.size) selectedItems.clear()
        else selectedItems.addAll(files.indices)
        notifyDataSetChanged()
    }
}