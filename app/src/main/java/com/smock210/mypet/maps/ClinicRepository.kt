package com.smock210.mypet.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Источник данных о ветклиниках и геопозиции.
 *
 * Сейчас используется демонстрационный набор клиник (реальный поиск
 * по Яндекс API требует ключа и сервера), но координаты известны,
 * поэтому клиники сортируются по расстоянию до пользователя
 * и фильтруются по радиусу — FIX_PLAN.md, 5.2.
 *
 * Геопозиция берётся из системного [LocationManager], а не из
 * Google Play Services: карта переведена на Яндекс.Карты и должна
 * работать на устройствах без GMS (например, Huawei).
 */
class ClinicRepository(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val mockClinics = listOf(
        Clinic(
            id = 1,
            name = "ВетКлиника «Счастливый Пёс»",
            address = "г. Москва, ул. Ленина, 123",
            phone = "+7 (495) 123-45-67",
            point = GeoPoint(55.751244, 37.618423),
            rating = 4.8f,
            isOpen = true
        ),
        Clinic(
            id = 2,
            name = "ВетМед «ЗооЗдоровье»",
            address = "г. Москва, ул. Пушкина, 45",
            phone = "+7 (495) 987-65-43",
            point = GeoPoint(55.755709, 37.621702),
            rating = 4.5f,
            isOpen = true
        ),
        Clinic(
            id = 3,
            name = "Клиника «Лапы»",
            address = "г. Москва, просп. Мира, 89",
            phone = "+7 (495) 111-22-33",
            point = GeoPoint(55.779947, 37.617314),
            rating = 4.9f,
            isOpen = false
        )
    )

    /**
     * Клиники, отсортированные по расстоянию до точки [lat]/[lng].
     * Если ни одна клиника не попала в радиус — возвращаются все (по расстоянию).
     */
    suspend fun searchNearbyClinics(lat: Double, lng: Double, radiusKm: Int = 10): List<Clinic> {
        val sorted = mockClinics.sortedBy { it.point?.let { p -> distanceMeters(lat, lng, p.latitude, p.longitude) } ?: Double.MAX_VALUE }
        val nearby = sorted.filter { clinic ->
            clinic.point?.let {
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

    /**
     * Текущая геопозиция (null, если геолокация недоступна).
     *
     * Сначала берётся последнее известное положение (быстро, без ожидания),
     * затем — однократный запрос свежего (с таймаутом, чтобы не крутить
     * GPS бесконечно).
     */
    @SuppressLint("MissingPermission") // проверка разрешения делается в hasLocationPermission()
    suspend fun getCurrentLocation(): GeoPoint? {
        if (!hasLocationPermission()) return null
        return withContext(Dispatchers.IO) {
            lastKnownPoint() ?: withTimeoutOrNull(SINGLE_UPDATE_TIMEOUT_MS) { freshPoint() }
        }
    }

    /** Есть ли хотя бы одно разрешение на геолокацию. */
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun lastKnownPoint(): GeoPoint? {
        return enabledProviders()
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
            ?.let { GeoPoint(it.latitude, it.longitude) }
    }

    /** Однократный запрос свежей позиции (null, если провайдер недоступен). */
    @SuppressLint("MissingPermission")
    private suspend fun freshPoint(): GeoPoint? {
        val provider = bestProvider() ?: return null
        return suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { locationManager.removeUpdates(this) }
                    if (continuation.isActive) {
                        continuation.resume(GeoPoint(location.latitude, location.longitude))
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(providerName: String?, status: Int, extras: Bundle?) = Unit
            }
            try {
                @Suppress("DEPRECATION") // requestSingleUpdate устарел, но работает на всех версиях
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
            continuation.invokeOnCancellation {
                runCatching { locationManager.removeUpdates(listener) }
            }
        }
    }

    private fun enabledProviders(): List<String> = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }

    private fun bestProvider(): String? {
        val enabled = enabledProviders()
        return when {
            LocationManager.GPS_PROVIDER in enabled -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in enabled -> LocationManager.NETWORK_PROVIDER
            LocationManager.PASSIVE_PROVIDER in enabled -> LocationManager.PASSIVE_PROVIDER
            else -> null
        }
    }

    /** Координаты по названию/адресу через системный геокодер. */
    suspend fun getCoordinatesFromAddress(address: String): GeoPoint? {
        return try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault()).getFromLocationName(address, 1)
            addresses?.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val SINGLE_UPDATE_TIMEOUT_MS = 10_000L
    }
}
