package com.smock210.mypet

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.smock210.mypet.classes.NewPetAdapt
import com.smock210.mypet.classes.VKUser
import com.smock210.mypet.databinding.ActivityMainStartBinding
import com.smock210.mypet.requests.VKUsersCommand
import com.smock210.mypet.security.BiometricAuthHelper
import com.smock210.mypet.security.DataStoreProvider
import com.smock210.mypet.security.SecurePreferencesManager
import com.smock210.mypet.sqlClass.PetData
import com.smock210.mypet.viewmodel.PetViewModel
import com.squareup.picasso.Picasso
import com.vk.api.sdk.VK
import com.vk.api.sdk.auth.VKAuthenticationResult
import com.vk.api.sdk.auth.VKScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(),
    NewPetAdapt.OnItemClickListener,
    NewPetAdapt.OnLongClickListener {

    private lateinit var binding: ActivityMainStartBinding
    private lateinit var petAdapter: NewPetAdapt
    private lateinit var petViewModel: PetViewModel
    private lateinit var dataStore: DataStoreProvider
    private lateinit var securePrefs: SecurePreferencesManager
    private var petList: List<PetData> = emptyList()
    /** Список, который сейчас отображается (с учётом поиска) — по нему работает свайп. */
    private var visiblePetList: List<PetData> = emptyList()
    private var searchQuery: String = ""
    private var unlocked = false

    private val biometricHelper = BiometricAuthHelper()

    private val vkAuthLauncher = VK.login(this) { result ->
        when (result) {
            is VKAuthenticationResult.Success -> loadVkProfile()
            is VKAuthenticationResult.Failed ->
                Toast.makeText(this, R.string.vk_login_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataStore = DataStoreProvider(applicationContext)
        securePrefs = SecurePreferencesManager(this)

        // Тема применяется до наполнения UI (FIX_PLAN.md, 4.2 / 6.4)
        applyTheme(dataStore.getThemeModeSync())

        binding = ActivityMainStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        petViewModel = PetViewModel(application)

        petAdapter = NewPetAdapt(this, this)
        binding.selectPetStart.apply {
            adapter = petAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            setHasFixedSize(true)
        }
        attachSwipeToDelete(binding.selectPetStart)

        lifecycleScope.launch {
            petViewModel.pets.collect { pets ->
                petList = pets
                applySearch()
            }
        }
        requestUsers()
        enforceBiometricLock()
    }

    /** Фильтрует список по [searchQuery] (кличка/порода/заметка) — FIX_PLAN.md, 6.2. */
    private fun applySearch() {
        val filtered = if (searchQuery.isBlank()) {
            petList
        } else {
            val q = searchQuery.trim()
            petList.filter { pet ->
                pet.name.orEmpty().contains(q, ignoreCase = true) ||
                    pet.breed.contains(q, ignoreCase = true) ||
                    pet.species.contains(q, ignoreCase = true) ||
                    pet.smallDetail.orEmpty().contains(q, ignoreCase = true)
            }
        }
        petAdapter.submitList(filtered)
        visiblePetList = filtered
        val empty = petList.isEmpty()
        val noResults = !empty && filtered.isEmpty()
        binding.tvEmptyState.visibility =
            if (empty || noResults) View.VISIBLE else View.GONE
        binding.tvEmptyState.setText(if (empty) R.string.no_pets else R.string.no_search_results)
        binding.selectPetStart.visibility =
            if (empty) View.GONE else View.VISIBLE
    }

    /** Если включена биометрическая блокировка — требуем подтверждение при каждом входе. */
    private fun enforceBiometricLock() {
        if (unlocked || !dataStore.getBiometricLockSync()) return
        if (!biometricHelper.isAvailable(this)) {
            Toast.makeText(this, R.string.biometric_unavailable, Toast.LENGTH_LONG).show()
            unlocked = true
            return
        }
        biometricHelper.authenticate(
            this,
            onSuccess = { unlocked = true },
            onError = {
                Toast.makeText(this, R.string.biometric_failed, Toast.LENGTH_SHORT).show()
                finish()
            }
        )
    }

    private fun attachSwipeToDelete(recyclerView: RecyclerView) {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position in visiblePetList.indices) {
                    val pet = visiblePetList[position]
                    // Удаление с возможностью отмены (FIX_PLAN.md, 6.3)
                    lifecycleScope.launch {
                        val snapshot = petViewModel.deletePetWithSnapshot(pet)
                        Snackbar.make(
                            binding.root,
                            getString(R.string.pet_deleted, pet.name ?: ""),
                            Snackbar.LENGTH_LONG
                        ).setAction(R.string.undo) {
                            petViewModel.restorePetSnapshot(snapshot)
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.pet_restored, pet.name ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }.show()
                    }
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.title_menu, menu)

        // Поиск по списку питомцев (FIX_PLAN.md, 6.2)
        val searchItem = menu.findItem(R.id.menu_search)
        val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query.orEmpty()
                applySearch()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText.orEmpty()
                applySearch()
                return true
            }
        })
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                searchQuery = ""
                applySearch()
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_vhod -> {
                if (VK.isLoggedIn()) {
                    Toast.makeText(this, R.string.vk_already_logged_in, Toast.LENGTH_SHORT).show()
                } else {
                    vkAuthLauncher.launch(arrayListOf(VKScope.PHOTOS, VKScope.EMAIL, VKScope.PHONE))
                }
            }
            R.id.menu_registr -> {
                // Регистрация выполняется на стороне VK — пунктом меню запускаем вход
                if (VK.isLoggedIn()) {
                    Toast.makeText(this, R.string.vk_already_logged_in, Toast.LENGTH_SHORT).show()
                } else {
                    vkAuthLauncher.launch(arrayListOf(VKScope.PHOTOS, VKScope.EMAIL, VKScope.PHONE))
                }
            }
            R.id.menu_add_pet -> {
                val intent = Intent(this, AddPetActivity::class.java)
                startActivity(intent)
            }
            R.id.menu_feeding_calc -> {
                startActivity(
                    Intent(this, ToolsActivity::class.java)
                        .putExtra(ToolsActivity.EXTRA_TOOL, ToolsActivity.TOOL_FEEDING)
                )
            }
            R.id.menu_export_data -> {
                startActivity(
                    Intent(this, ToolsActivity::class.java)
                        .putExtra(ToolsActivity.EXTRA_TOOL, ToolsActivity.TOOL_EXPORT)
                )
            }
            R.id.menu_health_scale -> {
                startActivity(
                    Intent(this, ToolsActivity::class.java)
                        .putExtra(ToolsActivity.EXTRA_TOOL, ToolsActivity.TOOL_HEALTH_SCALE)
                )
            }
            R.id.menu_maps -> {
                startActivity(
                    Intent(this, ToolsActivity::class.java)
                        .putExtra(ToolsActivity.EXTRA_TOOL, ToolsActivity.TOOL_MAPS)
                )
            }
            R.id.menu_toggle_theme -> cycleTheme()
            R.id.menu_biometric_lock -> toggleBiometricLock()
        }
        return super.onOptionsItemSelected(item)
    }

    /** Циклически меняет тему: системная → светлая → тёмная. */
    private fun cycleTheme() {
        val next = when (dataStore.getThemeModeSync()) {
            "system" -> "light"
            "light" -> "dark"
            else -> "system"
        }
        lifecycleScope.launch {
            dataStore.setThemeMode(next)
            applyTheme(next)
            val label = when (next) {
                "light" -> getString(R.string.theme_light)
                "dark" -> getString(R.string.theme_dark)
                else -> getString(R.string.theme_system)
            }
            Toast.makeText(this@MainActivity, getString(R.string.theme_changed, label), Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyTheme(mode: String) {
        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun toggleBiometricLock() {
        val enabled = !dataStore.getBiometricLockSync()
        if (enabled && !biometricHelper.isAvailable(this)) {
            Toast.makeText(this, R.string.biometric_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            dataStore.setBiometricLock(enabled)
            Toast.makeText(
                this@MainActivity,
                if (enabled) R.string.biometric_enabled else R.string.biometric_disabled,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onItemClick(position: Int) {
        if (position in petList.indices) {
            val pet = petList[position]
            val intent = Intent(this, MainDetailActivity::class.java).apply {
                putExtra(MainDetailActivity.EXTRA_PET_ID, pet.id)
                putExtra(MainDetailActivity.EXTRA_PET_NAME, pet.name)
                putExtra(MainDetailActivity.EXTRA_PET_DETAIL, pet.smallDetail)
                putExtra(MainDetailActivity.EXTRA_PET_PHOTO, pet.photoUri)
            }
            startActivity(intent)
        }
    }

    override fun OnLongClickListener(position: Int) {
        Toast.makeText(this, "Item $position long clicked", Toast.LENGTH_SHORT).show()
    }

    private fun requestUsers() {
        // Сначала показываем кэш из зашифрованного хранилища, затем обновляем из VK
        val cachedName = securePrefs.getProfileName()
        if (!cachedName.isNullOrBlank()) {
            binding.nameTV.text = cachedName
            securePrefs.getProfilePhoto()?.takeIf { it.isNotBlank() }?.let {
                Picasso.get().load(it).into(binding.avatarIV)
            } ?: binding.avatarIV.setImageResource(R.drawable.user_placeholder)
        } else {
            binding.nameTV.text = getString(R.string.defolt_тame)
            binding.avatarIV.setImageResource(R.drawable.user_placeholder)
        }
        if (VK.isLoggedIn()) {
            loadVkProfile()
        }
    }

    /** Загружает профиль пользователя VK и показывает имя/аватар в шапке. */
    private fun loadVkProfile() {
        lifecycleScope.launch {
            val user = withContext(Dispatchers.IO) {
                runCatching { VK.executeSync(VKUsersCommand()) }.getOrNull()?.firstOrNull()
            }
            bindVkUser(user)
        }
    }

    private suspend fun bindVkUser(user: VKUser?) {
        if (user == null) {
            // Если есть кэш — не пугаем ошибкой
            if (securePrefs.getProfileName().isNullOrBlank()) {
                Toast.makeText(this, R.string.vk_profile_error, Toast.LENGTH_SHORT).show()
            }
            return
        }
        val fullName = "${user.firstName} ${user.lastName}".trim()
        binding.nameTV.text = fullName
        if (user.photo.isNotBlank()) {
            Picasso.get().load(user.photo).into(binding.avatarIV)
        } else {
            binding.avatarIV.setImageResource(R.drawable.user_placeholder)
        }
        // Кэш в EncryptedSharedPreferences (FIX_PLAN.md, 4.2)
        withContext(Dispatchers.IO) {
            securePrefs.saveProfileName(fullName)
            securePrefs.saveProfilePhoto(user.photo)
            securePrefs.saveUserId(user.id)
        }
    }
}
