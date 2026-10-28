package com.sovereignops.timetable.ui.agenda

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sovereignops.timetable.R
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.databinding.ItemAgendaDayBinding
import com.sovereignops.timetable.databinding.ItemAgendaSectionBinding
import com.sovereignops.timetable.databinding.ItemBookingBinding
import com.sovereignops.timetable.ui.Formats
import java.time.LocalDate

/** Which part of the agenda a row belongs to. */
enum class AgendaSection { NEEDS_REVIEW, UPCOMING, PAST }

/** A flat row stream: section headers, day headers and the bookings under them. */
sealed interface AgendaRow {
    data class SectionHeader(val section: AgendaSection, val title: String) : AgendaRow
    data class DayHeader(val section: AgendaSection, val date: LocalDate) : AgendaRow
    data class Item(val section: AgendaSection, val booking: Booking) : AgendaRow
}

class AgendaAdapter(
    private val onClick: (AgendaRow.Item) -> Unit,
) : ListAdapter<AgendaRow, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is AgendaRow.SectionHeader -> TYPE_SECTION
            is AgendaRow.DayHeader -> TYPE_DAY
            is AgendaRow.Item -> TYPE_ITEM
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SECTION -> SectionHolder(ItemAgendaSectionBinding.inflate(inflater, parent, false))
            TYPE_DAY -> DayHolder(ItemAgendaDayBinding.inflate(inflater, parent, false))
            else -> ItemHolder(ItemBookingBinding.inflate(inflater, parent, false), onClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is AgendaRow.SectionHeader -> (holder as SectionHolder).bind(row)
            is AgendaRow.DayHeader -> (holder as DayHolder).bind(row)
            is AgendaRow.Item -> (holder as ItemHolder).bind(row)
        }
    }

    private class SectionHolder(private val binding: ItemAgendaSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: AgendaRow.SectionHeader) {
            binding.sectionLabel.text = row.title
        }
    }

    private class DayHolder(private val binding: ItemAgendaDayBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: AgendaRow.DayHeader) {
            binding.dayLabel.text = row.date.format(Formats.dayFull())
        }
    }

    private class ItemHolder(
        private val binding: ItemBookingBinding,
        private val onClick: (AgendaRow.Item) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: AgendaRow.Item) {
            val booking = row.booking
            val time = Formats.time(binding.root.context)
            binding.timeLabel.text = "${booking.start.toLocalTime().format(time)}–" +
                booking.end.toLocalTime().format(time)
            binding.aliasLabel.text = booking.clientAlias
            binding.rateLabel.text = booking.rate.format()
            // Earned money is green, an open commitment amber, a closed non-earner muted:
            // the colour never suggests that an unearned rate is money in hand.
            binding.rateLabel.setTextColor(
                binding.root.context.getColor(
                    when {
                        booking.status.isEarned -> R.color.earned
                        booking.status.isProjected -> R.color.projected
                        else -> R.color.on_surface_muted
                    },
                ),
            )
            binding.statusLabel.text = booking.status.label
            val flags = buildList {
                if (booking.screened) add("screened")
                if (booking.depositReceived) add("advance noted")
                if (booking.locationLabel.isNotBlank()) add(booking.locationLabel)
            }
            binding.metaLabel.text = flags.joinToString(" · ")
            binding.root.setOnClickListener { onClick(row) }
        }
    }

    companion object {
        private const val TYPE_SECTION = 0
        private const val TYPE_DAY = 1
        private const val TYPE_ITEM = 2

        val DIFF = object : DiffUtil.ItemCallback<AgendaRow>() {
            override fun areItemsTheSame(a: AgendaRow, b: AgendaRow): Boolean = when {
                a is AgendaRow.SectionHeader && b is AgendaRow.SectionHeader -> a.section == b.section
                a is AgendaRow.DayHeader && b is AgendaRow.DayHeader -> a.section == b.section && a.date == b.date
                a is AgendaRow.Item && b is AgendaRow.Item -> a.booking.id == b.booking.id
                else -> false
            }

            override fun areContentsTheSame(a: AgendaRow, b: AgendaRow): Boolean = a == b
        }
    }
}
