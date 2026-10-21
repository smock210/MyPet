package com.smock210.mypet.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.smock210.mypet.R
import com.smock210.mypet.sqlClass.PetData
import com.smock210.mypet.utils.ImageStorage
import com.smock210.mypet.viewmodel.PetViewModel
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch

class AddPetFragment : Fragment() {

    private val viewModel: PetViewModel by viewModels()
    private var selectedPhotoUri: Uri? = null
    private var editingPetId: Long = 0L

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
            view?.findViewById<CircleImageView>(R.id.iv_pet_photo_preview)
                ?.let { Picasso.get().load(uri).into(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_pet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editingPetId = arguments?.getLong(ARG_PET_ID) ?: 0L
        val isEditMode = editingPetId != 0L

        view.findViewById<MaterialButton>(R.id.btn_select_photo).setOnClickListener {
            pickPhotoLauncher.launch("image/*")
        }

        val speciesSpinner = view.findViewById<Spinner>(R.id.sp_species)
        val speciesAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.pet_species,
            android.R.layout.simple_spinner_item
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        speciesSpinner.adapter = speciesAdapter

        if (isEditMode) {
            view.findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.title_add_pet)
                .text = getString(R.string.edit_pet)
            view.findViewById<MaterialButton>(R.id.btn_save_pet)
                .text = getString(R.string.save)
            viewModel.loadPetForEdit(editingPetId)
            observePet(view)
        }

        view.findViewById<MaterialButton>(R.id.btn_save_pet).setOnClickListener {
            save(view)
        }
    }

    private fun observePet(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.editedPet.collect { pet ->
                    pet?.let { fillForm(view, it) }
                }
            }
        }
    }

    private fun fillForm(view: View, pet: PetData) {
        view.findViewById<TextInputEditText>(R.id.et_pet_name).setText(pet.name ?: "")
        view.findViewById<TextInputEditText>(R.id.et_pet_breed).setText(pet.breed)
        view.findViewById<TextInputEditText>(R.id.et_pet_age)
            .setText(if (pet.ageYears > 0) pet.ageYears.toString() else "")
        view.findViewById<TextInputEditText>(R.id.et_pet_weight)
            .setText(if (pet.weightKg > 0) pet.weightKg.toString() else "")
        view.findViewById<TextInputEditText>(R.id.et_pet_detail).setText(pet.smallDetail ?: "")

        val spinner = view.findViewById<Spinner>(R.id.sp_species)
        val adapter = spinner.adapter
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i).toString() == pet.species) {
                spinner.setSelection(i)
                break
            }
        }

        selectedPhotoUri = pet.photoUri?.let(Uri::parse)
        pet.photoUri?.takeIf { it.isNotBlank() }?.let {
            Picasso.get().load(it).into(view.findViewById<CircleImageView>(R.id.iv_pet_photo_preview))
        }
    }

    private fun save(view: View) {
        val name = view.findViewById<TextInputEditText>(R.id.et_pet_name).text.toString().trim()
        if (name.isBlank()) {
            Snackbar.make(view, R.string.name_required, Snackbar.LENGTH_SHORT).show()
            return
        }
        val species = view.findViewById<Spinner>(R.id.sp_species).selectedItem.toString()
        val breed = view.findViewById<TextInputEditText>(R.id.et_pet_breed).text.toString().trim()
        val ageStr = view.findViewById<TextInputEditText>(R.id.et_pet_age).text.toString().trim()
        val ageYears = ageStr.toIntOrNull() ?: 0
        val weightStr = view.findViewById<TextInputEditText>(R.id.et_pet_weight).text.toString().trim()
        val weightKg = weightStr.toDoubleOrNull() ?: 0.0
        val detail = view.findViewById<TextInputEditText>(R.id.et_pet_detail).text.toString().trim()

        // Фото сохраняем копией в приватном хранилище: временный content:// URI
        // перестаёт открываться после перезапуска приложения
        val photoPath = selectedPhotoUri?.let {
            ImageStorage.persist(requireContext(), it.toString())
        }

        if (editingPetId != 0L) {
            val current = viewModel.editedPet.value ?: return
            viewModel.updatePet(
                current.copy(
                    name = name,
                    species = species,
                    breed = breed,
                    ageYears = ageYears,
                    weightKg = weightKg,
                    smallDetail = detail.ifBlank { null },
                    photoUri = photoPath ?: current.photoUri
                )
            )
            Snackbar.make(view, R.string.pet_updated, Snackbar.LENGTH_SHORT).show()
        } else {
            viewModel.addPet(
                name, species, breed, ageYears, weightKg,
                detail.ifBlank { null }, photoPath
            )
            Snackbar.make(view, R.string.pet_saved, Snackbar.LENGTH_SHORT).show()
        }
        parentFragmentManager.popBackStack()
        requireActivity().finish()
    }

    companion object {
        private const val ARG_PET_ID = "pet_id"

        fun newInstance(petId: Long): AddPetFragment = AddPetFragment().apply {
            arguments = Bundle().apply { putLong(ARG_PET_ID, petId) }
        }
    }
}
