package com.smock210.mypet

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smock210.mypet.sqlClass.AppDatabase
import com.smock210.mypet.sqlClass.PetData
import com.smock210.mypet.sqlClass.PetPhoto
import com.smock210.mypet.sqlClass.Vaccination
import com.smock210.mypet.sqlClass.VetVisit
import com.smock210.mypet.sqlClass.WeightRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: com.smock210.mypet.sqlClass.EmployeeDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.employeeDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testInsertAndRead() = runBlocking {
        val pet = PetData(name = "Бобик", smallDetail = "Любит гулять")
        val id = dao.insert(pet)
        val read = dao.getById(id)
        assert(read?.name == "Бобик")
        assert(read?.smallDetail == "Любит гулять")
    }

    @Test
    fun testGetAllEmpty() = runBlocking {
        val all = dao.getAll()
        assert(all.isEmpty())
    }

    @Test
    fun testUpdate() = runBlocking {
        val pet = PetData(name = "Мурка")
        val id = dao.insert(pet)
        val updated = pet.copy(id = id, smallDetail = "Учит Java")
        dao.update(updated)
        val read = dao.getById(id)
        assert(read?.smallDetail == "Учит Java")
    }

    @Test
    fun testDelete() = runBlocking {
        val id = dao.insert(PetData(name = "Рекс"))
        val read = dao.getById(id)
        assert(read != null)
        dao.delete(read!!)
        assert(dao.getById(id) == null)
    }

    // ---------- FIX_PLAN.md, 5.1: прививки/визиты/фото ----------

    @Test
    fun testVaccinationsAreScopedByPet() = runBlocking {
        val healthDao = db.healthDao()
        val pet1 = dao.insert(PetData(name = "Бобик"))
        val pet2 = dao.insert(PetData(name = "Мурка"))

        healthDao.insertVaccination(Vaccination(petId = pet1, name = "Бешенство", dateMillis = 1000))
        healthDao.insertVaccination(Vaccination(petId = pet2, name = "Кальцивироз", dateMillis = 2000))

        val forPet1 = healthDao.getVaccinationsForPet(pet1)
        val forPet2 = healthDao.getVaccinationsForPet(pet2)

        // Регресс pet_id (FIX_PLAN.md, 0.1): записи не должны смешиваться
        assertEquals(1, forPet1.size)
        assertEquals("Бешенство", forPet1[0].name)
        assertEquals(1, forPet2.size)
        assertEquals("Кальцивироз", forPet2[0].name)
    }

    @Test
    fun testInsertVaccinationReturnsIdAndDoneDefaults() = runBlocking {
        val healthDao = db.healthDao()
        val petId = dao.insert(PetData(name = "Бобик"))
        val id = healthDao.insertVaccination(Vaccination(petId = petId, name = "Бешенство"))
        assert(id > 0)
        val read = healthDao.getVaccinationsForPet(petId).single()
        assertEquals(id, read.id)
        assertEquals(false, read.done) // миграция 4→5: по умолчанию не сделана
    }

    @Test
    fun testVisitsAreScopedByPet() = runBlocking {
        val healthDao = db.healthDao()
        val pet1 = dao.insert(PetData(name = "Бобик"))
        val pet2 = dao.insert(PetData(name = "Мурка"))

        healthDao.insertVisit(VetVisit(petId = pet1, clinicName = "Клиника-1", dateMillis = 1000))
        healthDao.insertVisit(VetVisit(petId = pet2, clinicName = "Клиника-2", dateMillis = 2000))

        assertEquals("Клиника-1", healthDao.getVisitsForPet(pet1).single().clinicName)
        assertEquals("Клиника-2", healthDao.getVisitsForPet(pet2).single().clinicName)
    }

    @Test
    fun testPhotosForPetAndDeleteAll() = runBlocking {
        val photoDao = db.petPhotoDao()
        val petId = dao.insert(PetData(name = "Бобик"))

        photoDao.insert(PetPhoto(petId = petId, photoUri = "content://one", isPrimary = true))
        photoDao.insert(PetPhoto(petId = petId, photoUri = "content://two", isPrimary = false))

        assertEquals(2, photoDao.getPhotosForPet(petId).size)
        // Основное фото идёт первым
        assertEquals("content://one", photoDao.getPhotosForPet(petId)[0].photoUri)

        photoDao.deleteAllForPet(petId)
        assertEquals(0, photoDao.getPhotosForPet(petId).size)
    }

    @Test
    fun testUpdateVaccinationDone() = runBlocking {
        val healthDao = db.healthDao()
        val petId = dao.insert(PetData(name = "Бобик"))
        healthDao.insertVaccination(Vaccination(petId = petId, name = "Бешенство"))

        val v = healthDao.getVaccinationsForPet(petId).single()
        healthDao.updateVaccination(v.copy(done = true))

        assertEquals(true, healthDao.getVaccinationsForPet(petId).single().done)
    }

    // ---------- FIX_PLAN.md, 6.1: история веса ----------

    @Test
    fun testWeightsAreScopedByPetAndOrdered() = runBlocking {
        val weightDao = db.weightDao()
        val pet1 = dao.insert(PetData(name = "Бобик"))
        val pet2 = dao.insert(PetData(name = "Мурка"))

        weightDao.insert(WeightRecord(petId = pet1, weightKg = 10.0, dateMillis = 1000))
        weightDao.insert(WeightRecord(petId = pet1, weightKg = 11.5, dateMillis = 2000))
        weightDao.insert(WeightRecord(petId = pet2, weightKg = 4.0, dateMillis = 1500))

        val forPet1 = weightDao.getWeightsForPet(pet1)
        assertEquals(2, forPet1.size)
        // Сортировка по дате
        assertEquals(10.0, forPet1[0].weightKg, 0.001)
        assertEquals(11.5, forPet1[1].weightKg, 0.001)
        assertEquals(1, weightDao.getWeightsForPet(pet2).size)
    }

    @Test
    fun testDeleteAllWeightsForPet() = runBlocking {
        val weightDao = db.weightDao()
        val petId = dao.insert(PetData(name = "Бобик"))
        weightDao.insert(WeightRecord(petId = petId, weightKg = 10.0))

        weightDao.deleteAllForPet(petId)

        assertEquals(0, weightDao.getWeightsForPet(petId).size)
    }
}