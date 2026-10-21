package com.smock210.mypet.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smock210.mypet.notifications.NotificationHelper
import com.smock210.mypet.sqlClass.AppDatabase
import com.smock210.mypet.sqlClass.PetData
import com.smock210.mypet.sqlClass.PetPhoto
import com.smock210.mypet.sqlClass.Vaccination
import com.smock210.mypet.sqlClass.VetVisit
import com.smock210.mypet.sqlClass.WeightRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class PetViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).employeeDao()
    private val healthDao = AppDatabase.getDatabase(application).healthDao()
    private val photoDao = AppDatabase.getDatabase(application).petPhotoDao()
    private val weightDao = AppDatabase.getDatabase(application).weightDao()

    // ========= ЖИВОТНЫЕ (реактивно из Room) =========

    val pets: StateFlow<List<PetData>> = dao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ========= СКОПИРОВАННЫЙ ПИТОМЕЦ ДЛЯ ДЕТАЛЬНЫХ ЭКРАНОВ =========

    private val currentPetId = MutableStateFlow(0L)

    /** Питомец для режима редактирования (AddPetFragment). */
    private val _editedPet = MutableStateFlow<PetData?>(null)
    val editedPet: StateFlow<PetData?> = _editedPet

    fun loadPetForEdit(petId: Long) {
        viewModelScope.launch {
            _editedPet.value = dao.getById(petId)
        }
    }

    val vaccinations: StateFlow<List<Vaccination>> = currentPetId
        .map { petId ->
            if (petId == 0L) flowOf(emptyList())
            else healthDao.getVaccinationsForPetFlow(petId)
        }
        .flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visits: StateFlow<List<VetVisit>> = currentPetId
        .map { petId ->
            if (petId == 0L) flowOf(emptyList())
            else healthDao.getVisitsForPetFlow(petId)
        }
        .flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val photos: StateFlow<List<PetPhoto>> = currentPetId
        .map { petId ->
            if (petId == 0L) flowOf(emptyList())
            else photoDao.getPhotosForPetFlow(petId)
        }
        .flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Выбрать питомца для экранов здоровья/фото. */
    fun loadVaccinations(petId: Long) {
        currentPetId.value = petId
    }

    fun loadVisits(petId: Long) {
        currentPetId.value = petId
    }

    fun loadPhotos(petId: Long) {
        currentPetId.value = petId
    }

    // ========= ИСТОРИЯ ВЕСА (FIX_PLAN.md, 6.1) =========

    val weights: StateFlow<List<WeightRecord>> = currentPetId
        .map { petId ->
            if (petId == 0L) flowOf(emptyList())
            else weightDao.getWeightsForPetFlow(petId)
        }
        .flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadWeights(petId: Long) {
        currentPetId.value = petId
    }

    /** Добавляет взвешивание и обновляет текущий вес питомца. */
    fun addWeight(petId: Long, weightKg: Double) {
        viewModelScope.launch {
            weightDao.insert(
                WeightRecord(petId = petId, weightKg = weightKg, dateMillis = System.currentTimeMillis())
            )
            dao.getById(petId)?.let { dao.update(it.copy(weightKg = weightKg)) }
        }
    }

    // ========= ОПЕРАЦИИ НАД ЖИВОТНЫМИ =========

    fun addPet(
        name: String,
        species: String,
        breed: String,
        ageYears: Int,
        weightKg: Double,
        smallDetail: String?,
        photoUri: String?
    ) {
        viewModelScope.launch {
            val pet = PetData(
                name = name,
                species = species,
                breed = breed,
                ageYears = ageYears,
                weightKg = weightKg,
                smallDetail = smallDetail,
                photoUri = photoUri,
                createdAt = System.currentTimeMillis()
            )
            val petId = dao.insert(pet)
            if (!photoUri.isNullOrBlank()) {
                photoDao.insert(
                    PetPhoto(petId = petId, photoUri = photoUri, isPrimary = true)
                )
            }
        }
    }

    fun updatePet(pet: PetData) {
        viewModelScope.launch { dao.update(pet) }
    }

    fun deletePet(pet: PetData) {
        viewModelScope.launch {
            dao.delete(pet)
            photoDao.deleteAllForPet(pet.id)
        }
    }

    /** Полный снапшот данных питомца для отмены удаления (FIX_PLAN.md, 6.3). */
    data class PetSnapshot(
        val pet: PetData,
        val photos: List<PetPhoto>,
        val vaccinations: List<Vaccination>,
        val visits: List<VetVisit>,
        val weights: List<WeightRecord>
    )

    /**
     * Удаляет питомца со всеми связанными записями и возвращает снапшот
     * для возможной отмены (Undo).
     */
    suspend fun deletePetWithSnapshot(pet: PetData): PetSnapshot {
        val snapshot = PetSnapshot(
            pet = pet,
            photos = photoDao.getPhotosForPet(pet.id),
            vaccinations = healthDao.getVaccinationsForPet(pet.id),
            visits = healthDao.getVisitsForPet(pet.id),
            weights = weightDao.getWeightsForPet(pet.id)
        )
        dao.delete(pet)
        photoDao.deleteAllForPet(pet.id)
        weightDao.deleteAllForPet(pet.id)
        healthDao.getVaccinationsForPet(pet.id).forEach { healthDao.deleteVaccination(it) }
        healthDao.getVisitsForPet(pet.id).forEach { healthDao.deleteVisit(it) }
        return snapshot
    }

    /** Восстанавливает питомца и все записи после Undo. */
    fun restorePetSnapshot(snapshot: PetSnapshot) {
        viewModelScope.launch {
            dao.insert(snapshot.pet) // id сохранён → записи снова привяжутся к нему
            snapshot.photos.forEach { photoDao.insert(it) }
            snapshot.weights.forEach { weightDao.insert(it) }
            snapshot.vaccinations.forEach { healthDao.insertVaccination(it) }
            snapshot.visits.forEach { healthDao.insertVisit(it) }
        }
    }

    // ========= ФОТОГРАФИИ =========

    fun addPhoto(petId: Long, photoUri: String) {
        viewModelScope.launch {
            val isFirst = photoDao.getPhotosForPet(petId).isEmpty()
            photoDao.insert(PetPhoto(petId = petId, photoUri = photoUri, isPrimary = isFirst))
        }
    }

    fun deletePhoto(photo: PetPhoto) {
        viewModelScope.launch { photoDao.delete(photo) }
    }

    fun setPrimaryPhoto(photo: PetPhoto) {
        viewModelScope.launch {
            val others = photoDao.getPhotosForPet(photo.petId)
            for (p in others) {
                photoDao.update(p.copy(isPrimary = (p.id == photo.id)))
            }
        }
    }

    // ========= ПРИВИВКИ =========

    fun addVaccination(
        petId: Long,
        name: String,
        dateMillis: Long,
        reminderEnabled: Boolean,
        notes: String
    ) {
        viewModelScope.launch {
            val v = Vaccination(
                petId = petId,
                name = name,
                dateMillis = dateMillis,
                reminderEnabled = reminderEnabled,
                notes = notes
            )
            v.id = healthDao.insertVaccination(v)
            scheduleVaccinationReminder(petId, v)
        }
    }

    fun deleteVaccination(v: Vaccination) {
        viewModelScope.launch {
            notificationHelper.cancelVaccinationReminder(v)
            healthDao.deleteVaccination(v)
        }
    }

    fun updateVaccination(v: Vaccination) {
        viewModelScope.launch { healthDao.updateVaccination(v) }
    }

    // ========= ЗАПИСИ К ВРАЧУ =========

    fun addVisit(
        petId: Long,
        clinicName: String,
        doctorName: String,
        dateMillis: Long,
        notes: String,
        diagnosis: String
    ) {
        viewModelScope.launch {
            val v = VetVisit(
                petId = petId,
                clinicName = clinicName,
                doctorName = doctorName,
                dateMillis = dateMillis,
                notes = notes,
                diagnosis = diagnosis
            )
            v.id = healthDao.insertVisit(v)
            scheduleVisitReminder(petId, v)
        }
    }

    fun deleteVisit(v: VetVisit) {
        viewModelScope.launch {
            notificationHelper.cancelVisitReminder(v)
            withContext(Dispatchers.IO) { deleteCalendarEvent(v.calendarEventId) }
            healthDao.deleteVisit(v)
        }
    }

    /** Удаляет событие календаря, если оно было создано для визита. */
    private fun deleteCalendarEvent(eventId: Long) {
        if (eventId == 0L) return
        try {
            getApplication<Application>().contentResolver.delete(
                android.provider.CalendarContract.Events.CONTENT_URI,
                "${android.provider.CalendarContract.Events._ID} = ?",
                arrayOf(eventId.toString())
            )
        } catch (_: SecurityException) {
            // Нет разрешения WRITE_CALENDAR — событие остаётся, критично только для синхронизации
        }
    }

    fun updateVisit(v: VetVisit) {
        viewModelScope.launch { healthDao.updateVisit(v) }
    }

    // ========= КАЛЕНДАРЬ =========

    fun saveCalendarEventId(visit: VetVisit, eventId: Long) {
        viewModelScope.launch {
            healthDao.updateVisit(visit.copy(calendarEventId = eventId))
        }
    }

    // ========= ЭКСПОРТ =========

    /** Полный снапшот всех данных для экспорта (не зависит от currentPetId). */
    data class ExportSnapshot(
        val pets: List<PetData>,
        val vaccinations: List<Vaccination>,
        val visits: List<VetVisit>,
        val photos: List<PetPhoto>
    )

    suspend fun exportSnapshot(): ExportSnapshot = ExportSnapshot(
        pets = dao.getAll(),
        vaccinations = healthDao.getAllVaccinations(),
        visits = healthDao.getAllVisits(),
        photos = photoDao.getAllPhotos()
    )

    // ========= НАПОМИНАНИЯ =========

    private val notificationHelper by lazy { NotificationHelper(getApplication()) }

    /** Имя питомца для текста напоминания (пустая строка, если неизвестно). */
    private suspend fun petName(petId: Long): String = dao.getById(petId)?.name ?: ""

    private fun scheduleVaccinationReminder(petId: Long, v: Vaccination) {
        viewModelScope.launch {
            notificationHelper.scheduleVaccinationReminder(v, petName(petId))
        }
    }

    private fun scheduleVisitReminder(petId: Long, v: VetVisit) {
        viewModelScope.launch {
            notificationHelper.scheduleVisitReminder(v, petName(petId))
        }
    }
}
