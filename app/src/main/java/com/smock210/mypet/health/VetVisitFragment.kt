package com.smock210.mypet.health

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.smock210.mypet.R
import com.smock210.mypet.classes.VetVisitAdapter
import com.smock210.mypet.sqlClass.VetVisit
import com.smock210.mypet.viewmodel.PetViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class VetVisitFragment : Fragment() {

    private val viewModel: PetViewModel by viewModels()
    private var petId: Long = 0L
    private var editing: VetVisit? = null

    private val displayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* см. FIX_PLAN.md, 3.1 */ }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_vet_visit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        petId = arguments?.getLong("pet_id") ?: 0L

        val dateInput = view.findViewById<TextInputLayout>(R.id.til_visit_date).editText
        dateInput?.setOnClickListener {
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(requireContext(), { _, y, m, d ->
                dateInput.setText("$d/${m + 1}/$y")
            }, cal[Calendar.YEAR], cal[Calendar.MONTH], cal[Calendar.DAY_OF_MONTH]).show()
        }

        val adapter = VetVisitAdapter(
            onItemClick = { item -> startEdit(view, item) },
            onDelete = { item -> confirmDelete(item) }
        )
        view.findViewById<RecyclerView>(R.id.rv_visits).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.visits.collect { list ->
                    adapter.submitList(list)
                    view.findViewById<View>(R.id.tv_visit_empty).visibility =
                        if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        val saveBtn = view.findViewById<MaterialButton>(R.id.btn_add_to_calendar)
        saveBtn.setOnClickListener {
            val clinic = view.findViewById<TextInputEditText>(R.id.et_visit_clinic).text?.toString() ?: ""
            val doctor = view.findViewById<TextInputEditText>(R.id.et_visit_doctor).text?.toString() ?: ""
            val notes = view.findViewById<TextInputEditText>(R.id.et_visit_notes).text?.toString() ?: ""
            val dateStr = dateInput?.text?.toString() ?: ""
            val diagnosis = view.findViewById<TextInputEditText>(R.id.et_visit_diagnosis).text?.toString() ?: ""

            if (clinic.isBlank() || dateStr.isBlank()) {
                Toast.makeText(context, R.string.fill_clinic_date, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dateMillis = parseDate(dateStr)
            val current = editing
            if (current != null) {
                viewModel.updateVisit(
                    current.copy(
                        clinicName = clinic, doctorName = doctor,
                        dateMillis = dateMillis, notes = notes, diagnosis = diagnosis
                    )
                )
                Toast.makeText(context, R.string.record_updated, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.addVisit(petId, clinic, doctor, dateMillis, notes, diagnosis)
                addToCalendar(clinic, dateMillis, notes)
                Toast.makeText(context, R.string.record_added, Toast.LENGTH_SHORT).show()
            }
            resetForm(view, saveBtn)
        }

        viewModel.loadVisits(petId)
        ensureNotificationPermission()
    }

    /** Android 13+: напоминания требуют явного разрешения на уведомления. */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startEdit(view: View, item: VetVisit) {
        editing = item
        view.findViewById<TextInputEditText>(R.id.et_visit_clinic).setText(item.clinicName)
        view.findViewById<TextInputEditText>(R.id.et_visit_doctor).setText(item.doctorName)
        view.findViewById<TextInputEditText>(R.id.et_visit_notes).setText(item.notes)
        view.findViewById<TextInputEditText>(R.id.et_visit_diagnosis).setText(item.diagnosis)
        val cal = Calendar.getInstance().apply { timeInMillis = item.dateMillis }
        view.findViewById<TextInputLayout>(R.id.til_visit_date).editText
            ?.setText(displayFormat.format(cal.time))
        view.findViewById<MaterialButton>(R.id.btn_add_to_calendar).text = getString(R.string.update)
    }

    private fun resetForm(view: View, saveBtn: MaterialButton) {
        editing = null
        view.findViewById<TextInputEditText>(R.id.et_visit_clinic).text?.clear()
        view.findViewById<TextInputEditText>(R.id.et_visit_doctor).text?.clear()
        view.findViewById<TextInputEditText>(R.id.et_visit_notes).text?.clear()
        view.findViewById<TextInputEditText>(R.id.et_visit_diagnosis).text?.clear()
        view.findViewById<TextInputLayout>(R.id.til_visit_date).editText?.text?.clear()
        saveBtn.text = getString(R.string.add_visit_title)
    }

    private fun confirmDelete(item: VetVisit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_record_title)
            .setMessage(item.clinicName)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteVisit(item) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun parseDate(dateStr: String): Long {
        val parts = dateStr.split("/")
        return if (parts.size == 3) {
            val cal = Calendar.getInstance()
            cal.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
            cal.timeInMillis
        } else {
            System.currentTimeMillis()
        }
    }

    private fun addToCalendar(clinic: String, dateMillis: Long, notes: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dateMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, dateMillis + 3600 * 1000) // 1 час
            putExtra(
                CalendarContract.Events.TITLE,
                getString(R.string.calendar_visit_title, clinic)
            )
            putExtra(CalendarContract.Events.DESCRIPTION, notes)
        }
        startActivity(intent)
    }
}
