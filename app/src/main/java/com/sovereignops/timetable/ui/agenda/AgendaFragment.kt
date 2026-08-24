package com.sovereignops.timetable.ui.agenda

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.sovereignops.timetable.core.schedule.Agenda
import com.sovereignops.timetable.databinding.FragmentAgendaBinding
import com.sovereignops.timetable.ui.MainViewModel
import com.sovereignops.timetable.ui.editor.BookingEditorFragment
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AgendaFragment : Fragment() {

    private var _binding: FragmentAgendaBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: AgendaAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAgendaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = AgendaAdapter { booking ->
            BookingEditorFragment.newInstance(booking.id).show(parentFragmentManager, "editor")
        }
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        binding.addButton.setOnClickListener {
            BookingEditorFragment.newInstance(0L).show(parentFragmentManager, "editor")
        }

        viewModel.bookings.observe(viewLifecycleOwner) { bookings ->
            val now = LocalDateTime.now()
            val days = Agenda.upcomingByDay(bookings, now)
            val rows = buildList {
                days.forEach { day ->
                    add(AgendaRow.DayHeader(day.date))
                    day.bookings.forEach { add(AgendaRow.Item(it)) }
                }
            }
            adapter.submit(rows)
            binding.empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE

            val next = Agenda.next(bookings, now)
            binding.nextSummary.text = if (next == null) {
                getString(com.sovereignops.timetable.R.string.no_upcoming)
            } else {
                "Next: ${next.clientAlias} · ${next.start.format(NEXT_FORMAT)}"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val NEXT_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")
    }
}
