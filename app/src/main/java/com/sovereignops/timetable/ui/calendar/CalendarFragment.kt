package com.sovereignops.timetable.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.sovereignops.timetable.R
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.schedule.MonthSummary
import com.sovereignops.timetable.databinding.FragmentCalendarBinding
import com.sovereignops.timetable.databinding.ItemCalendarDayBinding
import com.sovereignops.timetable.ui.AppClock
import com.sovereignops.timetable.ui.Formats
import com.sovereignops.timetable.ui.MainViewModel
import com.sovereignops.timetable.ui.agenda.AgendaAdapter
import com.sovereignops.timetable.ui.agenda.AgendaRow
import com.sovereignops.timetable.ui.agenda.AgendaSection
import com.sovereignops.timetable.ui.editor.BookingEditorFragment
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * A month overview built on the Calendar library: each day that has bookings is
 * marked with how many, and tapping a day lists them below. Days outside the
 * month are not selectable. Cancelled and no-show bookings do not mark a day.
 */
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private var byDay: Map<LocalDate, List<Booking>> = emptyMap()
    private var selected: LocalDate = AppClock.today()
    private lateinit var adapter: AgendaAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        savedInstanceState?.getString(KEY_SELECTED)?.let { selected = LocalDate.parse(it) }

        adapter = AgendaAdapter { row -> BookingEditorFragment.show(parentFragmentManager, row.booking.id) }
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        val firstDay = firstDayOfWeekFromLocale()
        daysOfWeek(firstDay).forEach { day ->
            binding.weekdayHeader.addView(
                TextView(requireContext()).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    gravity = android.view.Gravity.CENTER
                    text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    contentDescription = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    setTextColor(requireContext().getColor(R.color.on_surface_muted))
                    textSize = 12f
                },
            )
        }

        binding.calendar.dayBinder = object : MonthDayBinder<DayContainer> {
            override fun create(view: View) = DayContainer(view)
            override fun bind(container: DayContainer, data: CalendarDay) = bindDay(container, data)
        }
        // A smooth scroll can report its month after the view is gone: never touch a cleared binding.
        binding.calendar.monthScrollListener = { month ->
            _binding?.monthTitle?.text = month.yearMonth.format(MONTH_TITLE)
        }
        val current = YearMonth.from(AppClock.today())
        binding.calendar.setup(current.minusMonths(24), current.plusMonths(24), firstDay)
        binding.calendar.scrollToMonth(YearMonth.from(selected))

        binding.prevMonth.setOnClickListener { shiftMonth(-1) }
        binding.nextMonth.setOnClickListener { shiftMonth(1) }

        viewModel.bookings.observe(viewLifecycleOwner) { bookings ->
            byDay = MonthSummary.byDay(bookings)
            binding.calendar.notifyCalendarChanged()
            showDay()
        }
    }

    private fun shiftMonth(delta: Long) {
        val visible = binding.calendar.findFirstVisibleMonth()?.yearMonth ?: YearMonth.from(selected)
        binding.calendar.smoothScrollToMonth(visible.plusMonths(delta))
    }

    private fun bindDay(container: DayContainer, day: CalendarDay) {
        val inMonth = day.position == DayPosition.MonthDate
        container.day = day
        val count = if (inMonth) byDay[day.date]?.size ?: 0 else 0
        val b = container.binding
        b.dayNumber.text = day.date.dayOfMonth.toString()
        b.dayNumber.alpha = if (inMonth) 1f else 0.3f
        b.dayMarker.text = if (count > 0) "●".repeat(minOf(count, 3)) else ""
        container.view.isClickable = inMonth
        container.view.isSelected = inMonth && day.date == selected
        container.view.setBackgroundResource(if (container.view.isSelected) R.drawable.bg_calendar_selected else 0)
        container.view.contentDescription = if (inMonth) {
            val label = day.date.format(Formats.dayFull())
            if (count > 0) resources.getQuantityString(R.plurals.calendar_day_description, count, label, count) else label
        } else {
            null
        }
        container.view.importantForAccessibility =
            if (inMonth) View.IMPORTANT_FOR_ACCESSIBILITY_YES else View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun select(date: LocalDate) {
        val old = selected
        selected = date
        binding.calendar.notifyDateChanged(old)
        binding.calendar.notifyDateChanged(date)
        showDay()
    }

    private fun showDay() {
        val bookings = byDay[selected].orEmpty()
        binding.dayTitle.text = selected.format(Formats.dayFull())
        adapter.submitList(bookings.map { AgendaRow.Item(AgendaSection.UPCOMING, it) })
        binding.dayEmpty.visibility = if (bookings.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SELECTED, selected.toString())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class DayContainer(view: View) : ViewContainer(view) {
        val binding = ItemCalendarDayBinding.bind(view)
        lateinit var day: CalendarDay

        init {
            view.setOnClickListener { if (day.position == DayPosition.MonthDate) select(day.date) }
        }
    }

    companion object {
        private const val KEY_SELECTED = "selected"
        private val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy")
    }
}
