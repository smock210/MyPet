package com.smock210.mypet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smock210.mypet.classes.PetPhotoAdapter
import com.smock210.mypet.databinding.ActivityMainDetailBinding
import com.smock210.mypet.health.HealthActivity
import com.smock210.mypet.requests.VKWallPostCommand
import com.smock210.mypet.sqlClass.PetData
import com.smock210.mypet.utils.ImageStorage
import com.smock210.mypet.video.VideoRecorderHelper
import com.squareup.picasso.Picasso
import com.smock210.mypet.viewmodel.PetViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Карточка питомца: фото, основная информация, заметки, галерея и график веса.
 */
class MainDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PET_ID = "pet_id"
        const val EXTRA_PET_NAME = "pet_name"
        const val EXTRA_PET_DETAIL = "pet_detail"
        const val EXTRA_PET_PHOTO = "pet_photo"
    }

    private lateinit var binding: ActivityMainDetailBinding
    private val viewModel: PetViewModel by viewModels()
    private var petId: Long = 0L
    private var petName: String = ""

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            // Копируем фото в приватное хранилище: временный content:// URI
            // перестаёт открываться после перезапуска приложения
            val path = ImageStorage.persist(this, uri.toString())
            viewModel.addPhoto(petId, path)
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

    /**
     * Разрешение на камеру: приложение объявляет CAMERA в манифесте, поэтому
     * системная камера откажется запускать видеозапись без выданного
     * разрешения (SecurityException).
     */
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            recordVideoLauncher.launch(videoRecorder.createIntent())
        } else {
            Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        petId = intent.getLongExtra(EXTRA_PET_ID, 0L)
        if (petId == 0L) {
            // Защита от записи данных под petId = 0 (см. FIX_PLAN.md, 0.1)
            Toast.makeText(this, R.string.pet_open_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        petName = intent.getStringExtra(EXTRA_PET_NAME) ?: getString(R.string.pet_generic)

        // Данные из intent — мгновенно, затем следим за БД (актуально после редактирования)
        bindPet(
            PetData(
                id = petId,
                name = intent.getStringExtra(EXTRA_PET_NAME),
                smallDetail = intent.getStringExtra(EXTRA_PET_DETAIL),
                photoUri = intent.getStringExtra(EXTRA_PET_PHOTO)
            )
        )
        observePet()

        binding.btnHealth.setOnClickListener {
            startActivity(
                Intent(this, HealthActivity::class.java).apply {
                    putExtra("pet_id", petId)
                    putExtra("pet_name", petName)
                }
            )
        }

        binding.btnAddPhoto.setOnClickListener {
            pickPhotoLauncher.launch("image/*")
        }

        setupPhotoGallery()
        setupWeightSection()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.detail_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_edit_pet -> {
                startActivity(
                    Intent(this, AddPetActivity::class.java)
                        .putExtra(AddPetActivity.EXTRA_PET_ID, petId)
                )
                return true
            }
            R.id.menu_post_vk -> {
                postToVk()
                return true
            }
            R.id.menu_record_video -> {
                startVideoRecording()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Запуск записи видео: сначала проверяем runtime-разрешение на камеру
     * (иначе система выбросит SecurityException), затем открываем камеру.
     */
    private fun startVideoRecording() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            recordVideoLauncher.launch(videoRecorder.createIntent())
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
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
        val name = viewModel.editedPet.value?.name ?: ""
        val message = getString(R.string.vk_post_message, name)

        // Фото хранится путём к файлу в приватном хранилище — для загрузки
        // в VK нужен file:// Uri
        val uploadUri = if (photoUri.startsWith("/")) {
            Uri.fromFile(java.io.File(photoUri))
        } else {
            Uri.parse(photoUri)
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    com.vk.api.sdk.VK.executeSync(
                        VKWallPostCommand(
                            message = message,
                            photos = listOf(uploadUri)
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
        viewModel.loadWeights(petId)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.weights.collect { binding.weightChart.submit(it) }
            }
        }

        binding.btnAddWeight.setOnClickListener {
            val input = EditText(this).apply {
                hint = getString(R.string.weight_kg_hint)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setPadding(48, 32, 48, 32)
            }
            MaterialAlertDialogBuilder(this)
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
                viewModel.editedPet.value?.let { pet ->
                    viewModel.updatePet(pet.copy(photoUri = photo.photoUri))
                }
                Toast.makeText(this, R.string.photo_is_primary, Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvPhotos.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                this@MainDetailActivity,
                androidx.recyclerview.widget.RecyclerView.HORIZONTAL,
                false
            )
            adapter = photoAdapter
        }

        viewModel.loadPhotos(petId)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.photos.collect { photos ->
                    photoAdapter.submitList(photos)
                    binding.tvPhotosEmpty.visibility =
                        if (photos.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvPhotos.visibility =
                        if (photos.isEmpty()) View.GONE else View.VISIBLE
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
                    bindPet(pet)
                }
            }
        }
    }

    /** Выводит данные питомца в карточку. */
    private fun bindPet(pet: PetData) {
        val name = pet.name ?: getString(R.string.pet_generic)
        binding.tvPetName.text = name
        binding.toolbar.title = name
        petName = name

        // Подпись: вид и порода
        val subtitle = listOf(
            pet.species.takeIf { it.isNotBlank() },
            pet.breed.takeIf { it.isNotBlank() }
        ).filterNotNull().joinToString(" · ")
        binding.tvPetSubtitle.text = subtitle
        binding.tvPetSubtitle.visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE

        // Чипы: возраст и вес
        binding.chipAge.text = ageLabel(pet.ageYears)
        binding.chipAge.visibility = if (pet.ageYears > 0) View.VISIBLE else View.GONE
        binding.chipWeight.text = getString(
            R.string.pet_weight_chip,
            String.format(java.util.Locale.getDefault(), "%.1f", pet.weightKg)
        )
        binding.chipWeight.visibility = if (pet.weightKg > 0) View.VISIBLE else View.GONE

        binding.tvPetDetail.text = pet.smallDetail ?: getString(R.string.no_notes)

        val photoUri = pet.photoUri
        if (!photoUri.isNullOrBlank()) {
            Picasso.get()
                .load(photoUri)
                .placeholder(R.drawable.ic_pet_placeholder)
                .error(R.drawable.ic_pet_placeholder)
                .into(binding.ivPetAvatar)
            binding.ivPetPlaceholder.visibility = View.GONE
        } else {
            binding.ivPetAvatar.setImageDrawable(null)
            binding.ivPetPlaceholder.visibility = View.VISIBLE
        }
    }

    /** «1 год», «2 года», «5 лет» — правильные склонения. */
    private fun ageLabel(years: Int): String {
        val mod100 = years % 100
        val mod10 = years % 10
        val word = when {
            mod100 in 11..14 -> "лет"
            mod10 == 1 -> "год"
            mod10 in 2..4 -> "года"
            else -> "лет"
        }
        return "$years $word"
    }
}
