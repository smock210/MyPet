package com.smock210.mypet.maps

import com.google.android.gms.maps.model.LatLng

data class Clinic(
    val id: Long = 0,
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val latLng: LatLng? = null,
    val rating: Float = 0f,
    val isOpen: Boolean = true
)

data class ClinicSearchResult(
    val clinics: List<Clinic>,
    val totalCount: Int
)