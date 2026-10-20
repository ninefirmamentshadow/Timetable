package com.sovereignops.timetable.ui.availability

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.databinding.ItemAvailabilityBinding
import com.sovereignops.timetable.ui.Formats
import java.time.format.TextStyle
import java.util.Locale

class AvailabilityAdapter(
    private val onDelete: (AvailabilityWindow) -> Unit,
) : RecyclerView.Adapter<AvailabilityAdapter.Holder>() {

    private var items: List<AvailabilityWindow> = emptyList()

    fun submit(items: List<AvailabilityWindow>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemAvailabilityBinding.inflate(LayoutInflater.from(parent.context), parent, false), onDelete)

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    class Holder(
        private val binding: ItemAvailabilityBinding,
        private val onDelete: (AvailabilityWindow) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(window: AvailabilityWindow) {
            binding.dayLabel.text = window.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val context = binding.root.context
            binding.rangeLabel.text = "${Formats.timeOf(context, window.start)}–" +
                Formats.minuteOfDay(context, window.endMinute)
            binding.deleteButton.setOnClickListener { onDelete(window) }
        }

    }
}
