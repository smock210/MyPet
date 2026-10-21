package com.smock210.mypet.health

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
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
import com.smock210.mypet.classes.VaccinationAdapter
import com.smock210.mypet.sqlClass.Vaccination
import com.smock210.mypet.viewmodel.PetViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class VaccinationFragment : Fragment() {

    private val viewModel: PetViewModel by viewModels()
    private var petId: Long = 0L
    private var editing: Vaccination? = null

    private val displayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* напоминания будут тихо неэтими, если отказано — критично */ }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_vaccination, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        petId = arguments?.getLong("pet_id") ?: 0L

        val dateInput = view.findViewById<TextInputLayout>(R.id.til_vaccine_date).editText
        val timeInput = view.findViewById<TextInputLayout>(R.id.til_vaccine_time).editText

        dateInput?.setOnClickListener { showDatePicker { dateInput.setText(it) } }
        timeInput?.setOnClickListener { showTimePicker { timeInput.setText(it) } }

        val adapter = VaccinationAdapter(
            onItemClick = { item -> startEdit(view, item) },
            onDoneChange = { item, done -> viewModel.updateVaccination(item.copy(done = done)) },
            onDelete = { item -> confirmDelete(item) }
        )
        view.findViewById<RecyclerView>(R.id.rv_vaccinations).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.vaccinations.collect { list ->
                    adapter.submitList(list)
                    view.findViewById<View>(R.id.tv_vaccination_empty).visibility =
                        if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        val saveBtn = view.findViewById<MaterialButton>(R.id.btn_add_vaccination)
        saveBtn.setOnClickListener {
            val name = view.findViewById<TextInputEditText>(R.id.et_vaccine_name).text?.toString() ?: ""
            if (name.isBlank()) {
                Toast.makeText(context, R.string.name_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dateStr = dateInput?.text?.toString() ?: ""
            val timeStr = timeInput?.text?.toString() ?: ""
            val notes = view.findViewById<TextInputEditText>(R.id.et_vaccine_notes).text?.toString() ?: ""
            val dateMillis = parseDate(dateStr, timeStr)

            val current = editing
            if (current != null) {
                viewModel.updateVaccination(
                    current.copy(name = name, dateMillis = dateMillis, notes = notes)
                )
                Toast.makeText(context, R.string.record_updated, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.addVaccination(petId, name, dateMillis, true, notes)
                Toast.makeText(context, R.string.record_added, Toast.LENGTH_SHORT).show()
            }
            resetForm(view, saveBtn)
        }

        viewModel.loadVaccinations(petId)
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

    private fun startEdit(view: View, item: Vaccination) {
        editing = item
        view.findViewById<TextInputEditText>(R.id.et_vaccine_name).setText(item.name)
        view.findViewById<TextInputEditText>(R.id.et_vaccine_notes).setText(item.notes)
        val cal = Calendar.getInstance().apply { timeInMillis = item.dateMillis }
        view.findViewById<TextInputLayout>(R.id.til_vaccine_date).editText
            ?.setText(displayFormat.format(cal.time))
        view.findViewById<TextInputLayout>(R.id.til_vaccine_time).editText
            ?.setText(String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
        view.findViewById<MaterialButton>(R.id.btn_add_vaccination).text = getString(R.string.update)
    }

    private fun resetForm(view: View, saveBtn: MaterialButton) {
        editing = null
        view.findViewById<TextInputEditText>(R.id.et_vaccine_name).text?.clear()
        view.findViewById<TextInputEditText>(R.id.et_vaccine_notes).text?.clear()
        view.findViewById<TextInputLayout>(R.id.til_vaccine_date).editText?.text?.clear()
        view.findViewById<TextInputLayout>(R.id.til_vaccine_time).editText?.text?.clear()
        saveBtn.text = getString(R.string.add_vaccination_title)
    }

    private fun confirmDelete(item: Vaccination) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_record_title)
            .setMessage(item.name)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteVaccination(item) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDatePicker(onResult: (String) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            onResult("$d/${m + 1}/$y")
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker(onResult: (String) -> Unit) {
        val cal = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, h, m ->
            onResult("$h:${if (m < 10) "0$m" else m}")
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun parseDate(dateStr: String, timeStr: String): Long {
        val cal = Calendar.getInstance()
        val parts = dateStr.split("/")
        if (parts.size == 3) {
            cal.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
        }
        val timeParts = timeStr.split(":")
        if (timeParts.size == 2) {
            cal.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
            cal.set(Calendar.MINUTE, timeParts[1].toInt())
        }
        return cal.timeInMillis
    }
}
