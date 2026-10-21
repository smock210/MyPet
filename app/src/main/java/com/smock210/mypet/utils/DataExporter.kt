package com.smock210.mypet.utils

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.smock210.mypet.sqlClass.*
import java.io.File
import java.io.FileWriter

class DataExporter(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportAllData(
        pets: List<PetData>,
        vaccinations: List<Vaccination>,
        visits: List<VetVisit>,
        photos: List<PetPhoto>
    ): String {
        val exportData = ExportData(
            pets = pets,
            vaccinations = vaccinations,
            visits = visits,
            photos = photos,
            exportDate = System.currentTimeMillis()
        )
        return gson.toJson(exportData)
    }

    fun saveToFile(jsonString: String, filename: String = "mypet_backup.json"): Boolean {
        return try {
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(directory, filename)

            file.parentFile?.mkdirs()
            val writer = FileWriter(file)
            writer.write(jsonString)
            writer.close()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    data class ExportData(
        val pets: List<PetData>,
        val vaccinations: List<Vaccination>,
        val visits: List<VetVisit>,
        val photos: List<PetPhoto>,
        val exportDate: Long
    )
}