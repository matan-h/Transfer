package com.matanh.transfer.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.matanh.transfer.databinding.ListItemTeamMemberBinding
import com.matanh.transfer.ui.items.TeamMember
import com.matanh.transfer.util.HapticUtils

class TeamAdapter(
    private val onClick: (TeamMember) -> Unit
) : ListAdapter<TeamMember, TeamAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ListItemTeamMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onClick
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position == itemCount - 1)
    }

    class ViewHolder(
        private val binding: ListItemTeamMemberBinding, private val onClick: (TeamMember) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(member: TeamMember, isLast: Boolean) = with(binding) {
            textViewTeamMemberName.text = member.name

            Glide.with(imageViewTeamMember).load(member.imageUrl).circleCrop()
                .into(imageViewTeamMember)

            divider.isVisible = !isLast

            root.setOnClickListener {
                HapticUtils.weakVibrate(it)
                onClick(member)
            }
        }
    }

    private companion object DiffCallback : DiffUtil.ItemCallback<TeamMember>() {
        override fun areItemsTheSame(old: TeamMember, new: TeamMember) = old.social == new.social

        override fun areContentsTheSame(old: TeamMember, new: TeamMember) = old == new
    }
}