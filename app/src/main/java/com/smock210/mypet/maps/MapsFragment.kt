package com.smock210.mypet.maps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.smock210.mypet.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import android.location.Geocoder

class MapsFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            getCurrentLocation()
        } else {
            Snackbar.make(requireView(), R.string.location_permission_denied, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_maps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)
        mapView.onResume()
        mapView.getMapAsync(this)

        val searchInput = view.findViewById<TextInputEditText>(R.id.et_search_clinic)
        val searchBtn = view.findViewById<MaterialButton>(R.id.btn_search)
        val directionsBtn = view.findViewById<MaterialButton>(R.id.btn_directions)

        searchBtn.setOnClickListener {
            val address = searchInput.text?.toString()?.trim()
            if (address.isNullOrBlank()) {
                Snackbar.make(view, R.string.maps_enter_address, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            geocodeAndShow(address, view)
        }

        directionsBtn.setOnClickListener {
            openGoogleMapsDirections(searchInput.text?.toString()?.trim())
        }
    }

    /**
     * Реальный поиск: геокодирует введённый текст через встроенный Geocoder
     * (не требует API-ключа Google Places) и ставит маркер на найденной точке.
     */
    private fun geocodeAndShow(query: String, view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(requireContext(), Locale.getDefault())
                        .getFromLocationName(query, 1)
                        ?.firstOrNull()
                }.getOrNull()
            }
            val point = result
            if (point == null) {
                Snackbar.make(view, R.string.maps_not_found, Snackbar.LENGTH_SHORT).show()
            } else {
                val latLng = LatLng(point.latitude, point.longitude)
                googleMap?.let { map ->
                    map.clear()
                    map.addMarker(MarkerOptions().position(latLng).title(query))
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                } ?: Snackbar.make(view, R.string.maps_not_ready, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isMyLocationButtonEnabled = true
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
        }
        loadClinicMarkers(map)
    }

    /**
     * Ставит маркеры клиник из [ClinicRepository]: адреса геокодируются через
     * встроенный Geocoder, поэтому API-ключ Google Places не нужен.
     * Координаты текущего местоположения используются для сортировки по близости.
     */
    private fun loadClinicMarkers(map: GoogleMap) {
        viewLifecycleOwner.lifecycleScope.launch {
            val repository = ClinicRepository(requireContext())
            val here = withContext(Dispatchers.IO) {
                runCatching { repository.getCurrentLocation() }.getOrNull()
            }
            val clinics = withContext(Dispatchers.IO) {
                repository.searchNearbyClinics(here?.latitude ?: 0.0, here?.longitude ?: 0.0)
            }
            clinics.forEach { clinic ->
                clinic.latLng?.let { point ->
                    map.addMarker(
                        MarkerOptions()
                            .position(point)
                            .title(clinic.name)
                            .snippet("${clinic.address}\n${clinic.phone}")
                    )
                }
            }
            // Центрируем на первом результате или на текущей позиции
            val center = here ?: clinics.firstOrNull()?.latLng
            center?.let { map.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 13f)) }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    private fun getCurrentLocation() {
        val location = LatLng(55.751244, 37.618423) // Москва - фиктивная координата
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
    }

    private fun openGoogleMapsDirections(query: String?) {
        // Если указан запрос — ищем по нему, иначе ищем ветклинику рядом
        val search = query?.takeIf { it.isNotBlank() } ?: getString(R.string.vet_clinic_query)
        val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(search)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (mapIntent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(mapIntent)
        } else {
            // Нет Google Maps — открываем через обычный geo-intent (любой браузер/карты)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(search)}")))
        }
    }
}
