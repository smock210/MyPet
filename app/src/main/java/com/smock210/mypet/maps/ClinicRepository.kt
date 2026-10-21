package com.smock210.mypet.maps

import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Источник данных о ветклиниках.
 *
 * Сейчас используется демонстрационный набор клиник (реальный поиск
 * по Google Places API требует ключа), но координаты известны,
 * поэтому клиники сортируются по расстоянию до пользователя
 * и фильтруются по радиусу — FIX_PLAN.md, 5.2.
 */
class ClinicRepository(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    private val mockClinics = listOf(
        Clinic(
            id = 1,
            name = "ВетКлиника «Счастливый Пёс»",
            address = "г. Москва, ул. Ленина, 123",
            phone = "+7 (495) 123-45-67",
            latLng = LatLng(55.751244, 37.618423),
            rating = 4.8f,
            isOpen = true
        ),
        Clinic(
            id = 2,
            name = "ВетМед «ЗооЗдоровье»",
            address = "г. Москва, ул. Пушкина, 45",
            phone = "+7 (495) 987-65-43",
            latLng = LatLng(55.755709, 37.621702),
            rating = 4.5f,
            isOpen = true
        ),
        Clinic(
            id = 3,
            name = "Клиника «Лапы»",
            address = "г. Москва, просп. Мира, 89",
            phone = "+7 (495) 111-22-33",
            latLng = LatLng(55.779947, 37.617314),
            rating = 4.9f,
            isOpen = false
        )
    )

    /**
     * Клиники, отсортированные по расстоянию до точки [lat]/[lng].
     * Если ни одна клиника не попала в радиус — возвращаются все (по расстоянию).
     */
    suspend fun searchNearbyClinics(lat: Double, lng: Double, radiusKm: Int = 10): List<Clinic> {
        val sorted = mockClinics.sortedBy { it.latLng?.let { p -> distanceMeters(lat, lng, p.latitude, p.longitude) } ?: Double.MAX_VALUE }
        val nearby = sorted.filter { clinic ->
            clinic.latLng?.let {
                distanceMeters(lat, lng, it.latitude, it.longitude) <= radiusKm * 1000
            } ?: false
        }
        return nearby.ifEmpty { sorted }
    }

    /** Расстояние между двумя точками по формуле гаверсинуса, метры. */
    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    /** Текущая геопозиция (null, если геолокация недоступна). */
    suspend fun getCurrentLocation(): LatLng? {
        return try {
            val location = fusedLocationClient.lastLocation.await()
            location?.let { LatLng(it.latitude, it.longitude) }
        } catch (e: Exception) {
            null
        }
    }

    /** Координаты по названию/адресу через системный геокодер. */
    suspend fun getCoordinatesFromAddress(address: String): LatLng? {
        return try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault()).getFromLocationName(address, 1)
            addresses?.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
        } catch (e: Exception) {
            null
        }
    }
}
