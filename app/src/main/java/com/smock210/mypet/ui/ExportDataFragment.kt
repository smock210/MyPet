package com.smock210.mypet.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import com.smock210.mypet.R
import com.smock210.mypet.utils.DataExporter
import com.smock210.mypet.viewmodel.PetViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExportDataFragment : Fragment() {

    private val viewModel: PetViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            view?.let { performExport(it) }
        } else {
            Snackbar.make(requireView(), R.string.export_permission_denied, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_export_data, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.btn_export).setOnClickListener {
            if (needsLegacyPermission()) {
                val granted = ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) performExport(view)
                else permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                performExport(view)
            }
        }
    }

    /** На Android 10+ запись в Downloads через MediaStore не требует разрешения. */
    private fun needsLegacyPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private fun performExport(view: View) {
        val statusText = view.findViewById<MaterialTextView>(R.id.tv_export_status)
        viewLifecycleOwner.lifecycleScope.launch {
            val snapshot = viewModel.exportSnapshot()
            val json = withContext(Dispatchers.IO) {
                DataExporter(requireContext()).exportAllData(
                    snapshot.pets, snapshot.vaccinations, snapshot.visits, snapshot.photos
                )
            }
            val result = withContext(Dispatchers.IO) { save(json) }
            result.fold(onSuccess = { location ->
                statusText.text = getString(R.string.export_success, location)
                Snackbar.make(view, R.string.export_done, Snackbar.LENGTH_LONG).show()
            }, onFailure = {
                statusText.text = getString(R.string.export_error)
                Snackbar.make(view, R.string.export_error, Snackbar.LENGTH_LONG).show()
            })
        }
    }

    private fun save(json: String): Result<String> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = requireContext().contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: error("Не удалось создать файл")
            requireContext().contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            "Downloads/$FILE_NAME"
        } else {
            @Suppress("DEPRECATION")
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                FILE_NAME
            )
            file.parentFile?.mkdirs()
            file.writeText(json)
            file.absolutePath
        }
    }

    companion object {
        private const val FILE_NAME = "mypet_backup.json"
    }
}
