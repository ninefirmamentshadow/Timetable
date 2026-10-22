package com.sovereignops.timetable.ui.agenda

import com.sovereignops.timetable.ui.showSecure
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sovereignops.timetable.R
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.core.schedule.Agenda
import com.sovereignops.timetable.databinding.FragmentAgendaBinding
import com.sovereignops.timetable.ui.AppClock
import com.sovereignops.timetable.ui.Formats
import com.sovereignops.timetable.ui.MainActivity
import com.sovereignops.timetable.ui.MainViewModel
import com.sovereignops.timetable.ui.editor.BookingEditorFragment

class AgendaFragment : Fragment() {

    private var _binding: FragmentAgendaBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: AgendaAdapter
    private var latest: List<Booking> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAgendaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = AgendaAdapter { row ->
            if (row.section == AgendaSection.NEEDS_REVIEW) showResolveDialog(row.booking) else openEditor(row.booking.id)
        }
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        binding.addButton.setOnClickListener { openEditor(0L) }
        binding.monthButton.setOnClickListener { (requireActivity() as MainActivity).openCalendar() }
        binding.settingsButton.setOnClickListener { (requireActivity() as MainActivity).openSettings() }

        viewModel.bookings.observe(viewLifecycleOwner) { bookings ->
            latest = bookings
            render()
        }
        viewModel.unreadableBookings.observe(viewLifecycleOwner) { count ->
            binding.unreadableBanner.visibility = if (count > 0) View.VISIBLE else View.GONE
            if (count > 0) {
                binding.unreadableBanner.text = resources.getQuantityString(R.plurals.unreadable_records, count, count)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Time passes while the app sits open: a booking can end, so re-evaluate.
        if (_binding != null) render()
    }

    private fun render() {
        val now = AppClock.now()
        val sections = Agenda.sections(latest, now)
        val rows = AgendaRows.build(sections) { section ->
            getString(
                when (section) {
                    AgendaSection.NEEDS_REVIEW -> R.string.section_needs_review
                    AgendaSection.UPCOMING -> R.string.section_upcoming
                    AgendaSection.PAST -> R.string.section_past
                },
            )
        }
        adapter.submitList(rows)
        binding.empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE

        val review = sections.needsReview.size
        binding.reviewSummary.visibility = if (review > 0) View.VISIBLE else View.GONE
        if (review > 0) {
            binding.reviewSummary.text = resources.getQuantityString(R.plurals.needs_review_summary, review, review)
        }

        val next = Agenda.next(latest, now)
        binding.nextSummary.text = if (next == null) {
            getString(R.string.no_upcoming)
        } else {
            "Next: ${next.clientAlias} · ${Formats.nextBooking(requireContext(), next.start)}"
        }
    }

    private fun openEditor(id: Long) {
        BookingEditorFragment.newInstance(id).show(parentFragmentManager, "editor")
    }

    /** An open booking whose time has passed: record how it ended. */
    private fun showResolveDialog(booking: Booking) {
        val outcomes = BookingStatus.RESOLUTIONS
        val labels = arrayOf(
            getString(R.string.resolve_completed),
            getString(R.string.resolve_no_show),
            getString(R.string.resolve_cancelled),
            getString(R.string.resolve_edit),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.resolve_title))
            .setMessage(booking.clientAlias)
            .setItems(labels) { _, which ->
                if (which < outcomes.size) viewModel.setBookingStatus(booking.id, outcomes[which]) else openEditor(booking.id)
            }
            .setNegativeButton(R.string.cancel, null)
            .showSecure()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
