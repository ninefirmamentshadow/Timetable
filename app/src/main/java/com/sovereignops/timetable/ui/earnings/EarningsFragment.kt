package com.sovereignops.timetable.ui.earnings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.sovereignops.timetable.R
import com.sovereignops.timetable.core.earnings.Earnings
import com.sovereignops.timetable.databinding.FragmentEarningsBinding
import com.sovereignops.timetable.ui.AppClock
import com.sovereignops.timetable.ui.MainViewModel

class EarningsFragment : Fragment() {

    private var _binding: FragmentEarningsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEarningsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.bookings.observe(viewLifecycleOwner) { bookings ->
            val summary = Earnings.summarize(bookings, AppClock.now())

            binding.earnedValue.text = summary.earned.format()
            binding.earnedCount.text = resources.getQuantityString(
                R.plurals.completed_count, summary.completedCount, summary.completedCount,
            )
            binding.projectedValue.text = summary.projected.format()
            binding.projectedCount.text = resources.getQuantityString(
                R.plurals.projected_count, summary.projectedCount, summary.projectedCount,
            )

            binding.cashValue.text = summary.cashRecorded.format()
            binding.cashCount.text = resources.getQuantityString(
                R.plurals.cash_count, summary.cashRecordedCount, summary.cashRecordedCount,
            )
            val unpaid = summary.completedWithoutPaymentCount
            binding.unpaidNote.visibility = if (unpaid > 0) View.VISIBLE else View.GONE
            if (unpaid > 0) binding.unpaidNote.text = resources.getQuantityString(R.plurals.unpaid_note, unpaid, unpaid)

            binding.reviewNote.visibility = if (summary.needsReviewCount > 0) View.VISIBLE else View.GONE
            binding.reviewNote.text = getString(R.string.needs_review_note, summary.needsReviewCount)

            val byWeek = Earnings.earnedByWeek(bookings)
            binding.weekBreakdown.text = if (byWeek.isEmpty()) {
                getString(R.string.no_earnings)
            } else {
                byWeek.entries.reversed().joinToString("\n") { (week, money) ->
                    "$week   ${money.format()}"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
