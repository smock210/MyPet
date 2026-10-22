package com.smock210.mypet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.smock210.mypet.ui.auth.AuthProfile
import com.smock210.mypet.ui.auth.AuthProviders
import com.smock210.mypet.ui.auth.AuthProvider
import com.smock210.mypet.ui.auth.AuthResult
import com.smock210.mypet.ui.auth.HuaweiNativeAuthProvider
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

    /** Провайдер, для которого сейчас выполняется вход. */
    private var activeProvider: AuthProvider? = null

    private val vkAuthLauncher = VK.login(this) { result ->
        when (result) {
            is VKAuthenticationResult.Success -> loadVkProfile()
            is VKAuthenticationResult.Failed -> {
                // Показываем реальную причину от VK (например, «application is blocked»),
                // иначе пользователь не поймёт, что не так с приложением в кабинете VK.
                val reason = result.exception.authError
                    ?: result.exception.message
                    ?: getString(R.string.vk_login_failed)
                Log.w("VK", "Authorization failed: ${result.exception.authError} / ${result.exception.message}")
                Toast.makeText(
                    this,
                    getString(R.string.vk_login_failed_reason, reason),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** Результат входа через Google / Huawei ID. */
    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val provider = activeProvider ?: return@registerForActivityResult
        provider.handleActivityResult(result) { authResult -> onAuthResult(authResult) }
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

    /** Запуск входа через выбранного провайдера (Google / Huawei ID). */
    private fun signInWith(provider: AuthProvider) {
        activeProvider = provider
        try {
            provider.signIn(this, authLauncher) { result -> onAuthResult(result) }
        } catch (e: Throwable) {
            // Например, NoClassDefFoundError, если в сборке не хватает части HMS SDK:
            // не роняем приложение, а предлагаем веб-авторизацию Huawei ID.
            Log.w("Auth", "Не удалось запустить вход через ${provider.id}: ${e.message}")
            if (provider.id == HuaweiNativeAuthProvider.PROVIDER_ID) {
                Toast.makeText(this, R.string.huawei_native_unavailable, Toast.LENGTH_LONG).show()
                signInWith(AuthProviders.huawei())
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.auth_login_failed, e.message ?: provider.id),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** Обработка результата входа через Google / Huawei ID. */
    private fun onAuthResult(result: AuthResult) {
        when (result) {
            is AuthResult.Success -> {
                bindProfile(result.profile)
                lifecycleScope.launch(Dispatchers.IO) {
                    securePrefs.saveProfileProvider(result.profile.provider)
                    securePrefs.saveProfileName(result.profile.name)
                    securePrefs.saveProfilePhoto(result.profile.photoUrl.orEmpty())
                    securePrefs.saveProfileEmail(result.profile.email.orEmpty())
                }
            }
            is AuthResult.Error -> {
                Toast.makeText(this, getString(R.string.auth_login_failed, result.message), Toast.LENGTH_LONG).show()
                // Нативный вход по HMS не удался — пробуем веб-авторизацию Huawei ID
                if (activeProvider?.id == HuaweiNativeAuthProvider.PROVIDER_ID) {
                    activeProvider = null
                    signInWith(AuthProviders.huawei())
                }
            }
            AuthResult.Cancelled -> Unit
        }
    }

    /** Показывает профиль пользователя в шапке главного экрана. */
    private fun bindProfile(profile: AuthProfile) {
        binding.nameTV.text = profile.name.ifBlank { getString(R.string.defolt_тame) }
        val photo = profile.photo()
        if (photo != null) {
            Picasso.get().load(photo).placeholder(R.drawable.user_placeholder).into(binding.avatarIV)
        } else {
            binding.avatarIV.setImageResource(R.drawable.user_placeholder)
        }
    }

    /**
     * Подсказывает рекомендуемый способ входа в зависимости от устройства:
     * на Huawei/Honor без Google Play Services — Huawei ID, иначе — Google.
     */
    private fun suggestRecommendedProvider() {
        if (!securePrefs.getProfileName().isNullOrBlank()) return

        val provider = AuthProviders.recommended(this)
        if (!provider.isConfigured(this)) return

        Snackbar.make(
            binding.root,
            getString(R.string.auth_recommended_provider, provider.title),
            Snackbar.LENGTH_LONG
        ).setAction(R.string.menu_vhod_title) {
            signInWith(provider)
        }.show()
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
            R.id.menu_sign_in_google -> {
                val provider = AuthProviders.google()
                if (!provider.isAvailable(this)) {
                    Toast.makeText(this, R.string.google_not_available, Toast.LENGTH_LONG).show()
                } else {
                    signInWith(provider)
                }
            }
            R.id.menu_sign_in_huawei -> {
                // На устройствах с HMS Core — нативный вход, иначе — веб-авторизация
                val native = AuthProviders.huaweiNative()
                if (native.isAvailable(this)) {
                    signInWith(native)
                } else {
                    signInWith(AuthProviders.huawei())
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
            R.id.menu_vk_diagnostics -> {
                startActivity(
                    Intent(this, ToolsActivity::class.java)
                        .putExtra(ToolsActivity.EXTRA_TOOL, ToolsActivity.TOOL_VK_DIAGNOSTICS)
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
                Picasso.get().load(it).placeholder(R.drawable.user_placeholder).into(binding.avatarIV)
            } ?: binding.avatarIV.setImageResource(R.drawable.user_placeholder)
        } else {
            binding.nameTV.text = getString(R.string.defolt_тame)
            binding.avatarIV.setImageResource(R.drawable.user_placeholder)
        }
        if (VK.isLoggedIn()) {
            loadVkProfile()
        }
        suggestRecommendedProvider()
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
        bindProfile(
            AuthProfile(
                provider = "vk",
                id = user.id.toString(),
                name = fullName,
                photoUrl = user.photo
            )
        )
        // Кэш в EncryptedSharedPreferences (FIX_PLAN.md, 4.2)
        withContext(Dispatchers.IO) {
            securePrefs.saveProfileProvider("vk")
            securePrefs.saveProfileName(fullName)
            securePrefs.saveProfilePhoto(user.photo)
            securePrefs.saveUserId(user.id)
        }
    }
}
