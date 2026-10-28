package com.sovereignops.timetable.ui.availability

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.databinding.ItemAvailabilityBinding
import com.sovereignops.timetable.ui.Formats
import java.time.format.TextStyle
import java.util.Locale

class AvailabilityAdapter(
    private val onEdit: (AvailabilityWindow) -> Unit,
    private val onDelete: (AvailabilityWindow) -> Unit,
) : ListAdapter<AvailabilityWindow, AvailabilityAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemAvailabilityBinding.inflate(LayoutInflater.from(parent.context), parent, false), onEdit, onDelete)

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(
        private val binding: ItemAvailabilityBinding,
        private val onEdit: (AvailabilityWindow) -> Unit,
        private val onDelete: (AvailabilityWindow) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(window: AvailabilityWindow) {
            binding.dayLabel.text = window.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val context = binding.root.context
            binding.rangeLabel.text = "${Formats.timeOf(context, window.start)}–" +
                Formats.minuteOfDay(context, window.endMinute)
            binding.root.setOnClickListener { onEdit(window) }
            binding.deleteButton.setOnClickListener { onDelete(window) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AvailabilityWindow>() {
            override fun areItemsTheSame(a: AvailabilityWindow, b: AvailabilityWindow) = a.id == b.id
            override fun areContentsTheSame(a: AvailabilityWindow, b: AvailabilityWindow) = a == b
        }
    }
}
