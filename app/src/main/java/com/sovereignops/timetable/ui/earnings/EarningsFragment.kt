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
            val summary = Earnings.summarize(bookings)

            binding.earnedValue.text = summary.earned.format()
            binding.earnedCount.text = resources.getQuantityString(
                R.plurals.completed_count, summary.completedCount, summary.completedCount,
            )
            binding.projectedValue.text = summary.projected.format()
            binding.projectedCount.text = resources.getQuantityString(
                R.plurals.projected_count, summary.projectedCount, summary.projectedCount,
            )

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
