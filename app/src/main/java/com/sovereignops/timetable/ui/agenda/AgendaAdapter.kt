package com.sovereignops.timetable.ui.agenda

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.databinding.ItemAgendaDayBinding
import com.sovereignops.timetable.databinding.ItemBookingBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A flat row stream: day headers interleaved with the bookings under them. */
sealed interface AgendaRow {
    data class DayHeader(val date: LocalDate) : AgendaRow
    data class Item(val booking: Booking) : AgendaRow
}

class AgendaAdapter(
    private val onClick: (Booking) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<AgendaRow> = emptyList()

    fun submit(rows: List<AgendaRow>) {
        this.rows = rows
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int =
        when (rows[position]) {
            is AgendaRow.DayHeader -> TYPE_HEADER
            is AgendaRow.Item -> TYPE_ITEM
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemAgendaDayBinding.inflate(inflater, parent, false))
        } else {
            ItemHolder(ItemBookingBinding.inflate(inflater, parent, false), onClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is AgendaRow.DayHeader -> (holder as HeaderHolder).bind(row.date)
            is AgendaRow.Item -> (holder as ItemHolder).bind(row.booking)
        }
    }

    private class HeaderHolder(private val binding: ItemAgendaDayBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(date: LocalDate) {
            binding.dayLabel.text = date.format(DAY_FORMAT)
        }
    }

    private class ItemHolder(
        private val binding: ItemBookingBinding,
        private val onClick: (Booking) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(booking: Booking) {
            binding.timeLabel.text = "${booking.start.toLocalTime().format(TIME_FORMAT)}–" +
                booking.end.toLocalTime().format(TIME_FORMAT)
            binding.aliasLabel.text = booking.clientAlias
            binding.rateLabel.text = booking.rate.format()
            binding.statusLabel.text = booking.status.label
            val flags = buildList {
                if (booking.screened) add("screened")
                if (booking.depositReceived) add("deposit")
                if (booking.locationLabel.isNotBlank()) add(booking.locationLabel)
            }
            binding.metaLabel.text = flags.joinToString(" · ")
            binding.root.setOnClickListener { onClick(booking) }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
        private val DAY_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")
    }
}
