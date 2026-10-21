package com.smock210.mypet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smock210.mypet.classes.PetPhotoAdapter
import com.smock210.mypet.health.HealthActivity
import com.smock210.mypet.requests.VKWallPostCommand
import com.smock210.mypet.video.VideoRecorderHelper
import com.squareup.picasso.Picasso
import com.smock210.mypet.viewmodel.PetViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PET_ID = "pet_id"
        const val EXTRA_PET_NAME = "pet_name"
        const val EXTRA_PET_DETAIL = "pet_detail"
        const val EXTRA_PET_PHOTO = "pet_photo"
    }

    private val viewModel: PetViewModel by viewModels()
    private var petId: Long = 0L

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.addPhoto(petId, uri.toString())
        }
    }

    private val videoRecorder = VideoRecorderHelper()

    private val recordVideoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (videoRecorder.extractVideoUri(result) != null) {
            Toast.makeText(this, R.string.video_saved, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, R.string.video_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_detail)

        petId = intent.getLongExtra(EXTRA_PET_ID, 0L)
        if (petId == 0L) {
            // Защита от записи данных под petId = 0 (см. FIX_PLAN.md, 0.1)
            Toast.makeText(this, R.string.pet_open_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val name = intent.getStringExtra(EXTRA_PET_NAME) ?: getString(R.string.pet_generic)

        // Данные из intent — мгновенно, затем следим за БД (актуально после редактирования)
        bindDetail(intent.getStringExtra(EXTRA_PET_NAME), intent.getStringExtra(EXTRA_PET_DETAIL), intent.getStringExtra(EXTRA_PET_PHOTO))
        observePet()

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab).setOnClickListener {
            finish()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_health).setOnClickListener {
            val intent = Intent(this, HealthActivity::class.java).apply {
                putExtra("pet_id", petId)
                putExtra("pet_name", name)
            }
            startActivity(intent)
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_edit_pet).setOnClickListener {
            startActivity(
                Intent(this, AddPetActivity::class.java)
                    .putExtra(AddPetActivity.EXTRA_PET_ID, petId)
            )
        }

        setupPhotoGallery()

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_record_video)
            .setOnClickListener {
                recordVideoLauncher.launch(videoRecorder.createIntent())
            }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_post_vk)
            .setOnClickListener { postToVk() }

        setupWeightSection()
    }

    /**
     * Публикация фото питомца на стене VK (FIX_PLAN.md, 6.7).
     * Требует авторизации и наличия основного фото.
     */
    private fun postToVk() {
        if (!com.vk.api.sdk.VK.isLoggedIn()) {
            Toast.makeText(this, R.string.vk_not_logged_in, Toast.LENGTH_LONG).show()
            return
        }
        val photoUri = viewModel.editedPet.value?.photoUri
        if (photoUri.isNullOrBlank()) {
            Toast.makeText(this, R.string.vk_no_photo, Toast.LENGTH_LONG).show()
            return
        }
        val petName = viewModel.editedPet.value?.name ?: ""
        val message = getString(R.string.vk_post_message, petName)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    com.vk.api.sdk.VK.executeSync(
                        VKWallPostCommand(
                            message = message,
                            photos = listOf(Uri.parse(photoUri))
                        )
                    )
                }
            }
            Toast.makeText(
                this@MainDetailActivity,
                if (result.isSuccess) R.string.vk_post_success else R.string.vk_post_failed,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** История взвешиваний и график (FIX_PLAN.md, 6.1). */
    private fun setupWeightSection() {
        val chart = findViewById<com.smock210.mypet.ui.WeightChartView>(R.id.weight_chart)
        viewModel.loadWeights(petId)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.weights.collect { chart.submit(it) }
            }
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_weight)
            .setOnClickListener {
                val input = android.widget.EditText(this).apply {
                    hint = getString(R.string.weight_kg_hint)
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setPadding(48, 32, 48, 32)
                }
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.add_weight)
                    .setView(input)
                    .setPositiveButton(R.string.save) { _, _ ->
                        val kg = input.text.toString().toDoubleOrNull()
                        if (kg == null || kg <= 0 || kg > 500) {
                            Toast.makeText(this, R.string.weight_invalid, Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addWeight(petId, kg)
                            Toast.makeText(this, R.string.weight_added, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
    }

    private fun setupPhotoGallery() {
        val photoAdapter = PetPhotoAdapter(
            onDelete = { photo ->
                viewModel.deletePhoto(photo)
                Toast.makeText(this, R.string.photo_deleted, Toast.LENGTH_SHORT).show()
            },
            onSetPrimary = { photo ->
                viewModel.setPrimaryPhoto(photo)
                viewModel.updatePet(
                    (viewModel.editedPet.value ?: return@PetPhotoAdapter).copy(photoUri = photo.photoUri)
                )
                Toast.makeText(this, R.string.photo_is_primary, Toast.LENGTH_SHORT).show()
            }
        )
        findViewById<RecyclerView>(R.id.rv_photos).apply {
            layoutManager = LinearLayoutManager(this@MainDetailActivity, RecyclerView.HORIZONTAL, false)
            adapter = photoAdapter
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add_photo)
            .setOnClickListener {
                pickPhotoLauncher.launch("image/*")
            }

        viewModel.loadPhotos(petId)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.photos.collect { photos ->
                    photoAdapter.submitList(photos)
                }
            }
        }
    }

    private fun observePet() {
        viewModel.loadPetForEdit(petId)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.editedPet.collect { pet ->
                    pet ?: return@collect
                    bindDetail(pet.name, pet.smallDetail, pet.photoUri)
                }
            }
        }
    }

    private fun bindDetail(name: String?, detail: String?, photoUri: String?) {
        findViewById<TextView>(R.id.tv_pet_name).text = name ?: getString(R.string.pet_generic)
        findViewById<TextView>(R.id.tv_pet_detail).text = detail ?: getString(R.string.no_notes)

        val avatar = findViewById<ImageView>(R.id.iv_pet_avatar)
        if (!photoUri.isNullOrBlank()) {
            Picasso.get().load(photoUri).into(avatar)
        } else {
            avatar.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }
}
