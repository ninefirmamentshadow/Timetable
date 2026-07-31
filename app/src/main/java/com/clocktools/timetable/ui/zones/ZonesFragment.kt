package com.clocktools.timetable.ui.zones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.clocktools.timetable.R
import com.clocktools.timetable.TimetableApplication
import com.clocktools.timetable.data.Zone
import com.clocktools.timetable.databinding.FragmentZonesBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ZonesFragment : Fragment() {

    private var _binding: FragmentZonesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ZonesViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = requireActivity().application as TimetableApplication
                ZonesViewModel(app.zoneRepository)
            }
        }
    }

    private lateinit var adapter: ZoneAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentZonesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ZoneAdapter(
            onZoneClicked = { zone -> openZoneEdit(zone.id) },
            onDeleteClicked = { zone -> confirmDelete(zone) }
        )
        binding.zonesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.zonesRecyclerView.adapter = adapter

        binding.addZoneFab.setOnClickListener { openZoneEdit(zoneId = -1L) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.zones.collect { zones ->
                    adapter.submitList(zones)
                    binding.zonesEmptyText.visibility = if (zones.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun openZoneEdit(zoneId: Long) {
        findNavController().navigate(
            R.id.action_zonesFragment_to_zoneEditFragment,
            bundleOf("zoneId" to zoneId)
        )
    }

    private fun confirmDelete(zone: Zone) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zones_delete_confirm_title)
            .setMessage(getString(R.string.zones_delete_confirm_message, zone.name))
            .setNegativeButton(R.string.zones_cancel, null)
            .setPositiveButton(R.string.zones_delete) { _, _ -> viewModel.delete(zone) }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
