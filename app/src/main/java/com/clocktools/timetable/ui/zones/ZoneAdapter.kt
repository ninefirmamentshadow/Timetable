package com.clocktools.timetable.ui.zones

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.clocktools.timetable.R
import com.clocktools.timetable.data.Zone
import com.clocktools.timetable.databinding.ItemZoneBinding
import com.clocktools.timetable.ui.TimeFormat

class ZoneAdapter(
    private val onZoneClicked: (Zone) -> Unit,
    private val onDeleteClicked: (Zone) -> Unit
) : ListAdapter<Zone, ZoneAdapter.ZoneViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ZoneViewHolder {
        val binding = ItemZoneBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ZoneViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ZoneViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ZoneViewHolder(private val binding: ItemZoneBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(zone: Zone) {
            binding.zoneName.text = zone.name
            binding.zoneRoute.text = zone.routeLabel
            binding.zoneOutbound.text =
                binding.root.context.getString(R.string.zones_outbound_label, TimeFormat.display(zone.lastOutboundTime))
            binding.zoneReturn.text =
                binding.root.context.getString(R.string.zones_return_label, TimeFormat.display(zone.lastReturnTime))
            binding.zoneVerified.text =
                binding.root.context.getString(R.string.zones_verified_label, zone.lastVerified.toString())

            binding.root.setOnClickListener { onZoneClicked(zone) }
            binding.zoneDeleteButton.setOnClickListener { onDeleteClicked(zone) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Zone>() {
            override fun areItemsTheSame(oldItem: Zone, newItem: Zone) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Zone, newItem: Zone) = oldItem == newItem
        }
    }
}
