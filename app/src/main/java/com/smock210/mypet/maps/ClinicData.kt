package com.smock210.mypet.maps

/**
 * Географическая точка.
 *
 * Своя модель координат вместо `com.google.android.gms.maps.model.LatLng`:
 * карточка клиники и репозиторий не должны зависеть от конкретного
 * картографического SDK (карты переведены на Яндекс.Карты, FIX_PLAN.md).
 * Перевод в `com.yandex.mapkit.geometry.Point` делается в UI-слое.
 */
data class GeoPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class Clinic(
    val id: Long = 0,
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val point: GeoPoint? = null,
    val rating: Float = 0f,
    val isOpen: Boolean = true
)

data class ClinicSearchResult(
    val clinics: List<Clinic>,
    val totalCount: Int
)
