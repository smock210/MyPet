package com.smock210.mypet.maps

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.smock210.mypet.BuildConfig
import com.smock210.mypet.R
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import android.location.Geocoder

/**
 * Экран «Ветклиники на карте».
 *
 * Раньше использовался Google Maps (при этом API-ключа в манифесте не было
 * и карта отображалась пустой). Переведено на Яндекс.Карты (MapKit):
 * работает и на устройствах без Google Play Services — FIX_PLAN.md.
 */
class MapsFragment : Fragment() {

    private lateinit var mapView: MapView

    /** Ложь, если MapKit не удалось инициализировать (экран показывает сообщение). */
    private var mapAvailable: Boolean = true

    /** Геопозиция пользователя (null, если недоступна). */
    private var userPoint: GeoPoint? = null

    /** Точка, найденная поиском по адресу. */
    private var foundPoint: GeoPoint? = null

    /** Текущий запрос из поля поиска (для построения маршрута). */
    private var lastQuery: String = ""

    /**
     * Слушатели тапов MapKit хранит по слабой ссылке, поэтому держим их
     * в полях фрагмента, иначе карта перестанет реагировать на нажатия.
     */
    private val placemarkTapListener = MapObjectTapListener { mapObject, _ ->
        when (val data = (mapObject as? MapObject)?.userData) {
            is Clinic -> {
                showClinicInfo(data)
                true
            }
            is Pair<*, *> -> {
                val title = data.first as? String
                val snippet = data.second as? String
                showPointInfo(title, snippet)
                true
            }
            else -> false
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadMapContent()
        } else {
            // Без разрешения карта всё равно работает, просто без «себя»
            // на карте и сортировки клиник по расстоянию.
            Snackbar.make(requireView(), R.string.location_permission_denied, Snackbar.LENGTH_SHORT)
                .show()
            loadMapContent()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_maps, container, false)
        } catch (e: Throwable) {
            // MapKit не инициализирован (нет API-ключа или упала инициализация
            // в Application) — сам MapView бросает AssertionError/Exception,
            // поэтому не роняем приложение, а показываем сообщение.
            Log.e(TAG, "MapKit is not ready", e)
            mapAvailable = false
            TextView(requireContext()).apply {
                text = if (BuildConfig.MAPKIT_API_KEY.isBlank()) {
                    getString(R.string.maps_key_missing)
                } else {
                    getString(R.string.maps_not_ready)
                }
                gravity = Gravity.CENTER
                setPadding(48, 48, 48, 48)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!mapAvailable) return // карта не инициализирована — показано сообщение

        mapView = view.findViewById(R.id.map_view)

        val searchInput = view.findViewById<TextInputEditText>(R.id.et_search_clinic)
        val searchBtn = view.findViewById<MaterialButton>(R.id.btn_search)
        val directionsBtn = view.findViewById<MaterialButton>(R.id.btn_directions)

        searchBtn.setOnClickListener {
            val address = searchInput.text?.toString()?.trim()
            if (address.isNullOrBlank()) {
                Snackbar.make(view, R.string.maps_enter_address, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lastQuery = address
            geocodeAndShow(address, view)
        }

        directionsBtn.setOnClickListener {
            openYandexMaps(foundPoint, lastQuery)
        }

        if (locationPermissionGranted()) {
            loadMapContent()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun onStart() {
        super.onStart()
        if (!mapAvailable) return
        // Оба вызова обязательны: без них MapKit перестаёт обновлять карту,
        // когда экран становится видимым.
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
    }

    override fun onStop() {
        if (mapAvailable) {
            mapView.onStop()
            MapKitFactory.getInstance().onStop()
        }
        super.onStop()
    }

    private fun locationPermissionGranted(): Boolean = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Реальный поиск: геокодирует введённый текст через встроенный Geocoder
     * (не требует API-ключа) и ставит маркер на найденной точке.
     */
    private fun geocodeAndShow(query: String, view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val repository = ClinicRepository(requireContext())
            val point = withContext(Dispatchers.IO) {
                runCatching { repository.getCoordinatesFromAddress(query) }.getOrNull()
            }
            if (point == null) {
                Snackbar.make(view, R.string.maps_not_found, Snackbar.LENGTH_SHORT).show()
                return@launch
            }
            foundPoint = point
            mapView.map.mapObjects.clear()
            addPlacemark(
                point = point,
                title = query,
                snippet = null,
                icon = placemarkIcon(colorPrimary(), PLACEMARK_SIZE_PX),
                userData = null,
                zIndex = 1f
            )
            moveCamera(point, ZOOM_SEARCH)
        }
    }

    /**
     * Ставит маркеры клиник из [ClinicRepository] и маркер пользователя.
     * Координаты текущего местоположения используются для сортировки по близости.
     */
    private fun loadMapContent() {
        viewLifecycleOwner.lifecycleScope.launch {
            val repository = ClinicRepository(requireContext())
            val here = withContext(Dispatchers.IO) { repository.getCurrentLocation() }
            userPoint = here
            val clinics = withContext(Dispatchers.IO) {
                repository.searchNearbyClinics(here?.latitude ?: 0.0, here?.longitude ?: 0.0)
            }

            mapView.map.mapObjects.clear()
            val clinicIcon = placemarkIcon(colorAccent(), PLACEMARK_SIZE_PX)
            clinics.forEach { clinic ->
                clinic.point?.let { point ->
                    addPlacemark(
                        point = point,
                        title = clinic.name,
                        snippet = clinic.address,
                        icon = clinicIcon,
                        userData = clinic,
                        zIndex = 0f
                    )
                }
            }
            // Маркер пользователя поверх маркеров клиник
            here?.let { point ->
                addPlacemark(
                    point = point,
                    title = getString(R.string.maps_my_location),
                    snippet = null,
                    icon = placemarkIcon(colorPrimary(), USER_MARK_SIZE_PX),
                    userData = null,
                    zIndex = 1f
                )
            }

            val center = here ?: clinics.firstOrNull()?.point
            center?.let { moveCamera(it, ZOOM_NEARBY) }
        }
    }

    private fun addPlacemark(
        point: GeoPoint,
        title: String,
        snippet: String?,
        icon: ImageProvider,
        userData: Any?,
        zIndex: Float
    ): PlacemarkMapObject {
        return mapView.map.mapObjects.addPlacemark().apply {
            geometry = point.toYandexPoint()
            setIcon(icon)
            this.zIndex = zIndex
            if (userData != null) setUserData(userData)
            addListener(title, snippet)
        }
    }

    private fun PlacemarkMapObject.addListener(title: String, snippet: String?) {
        addTapListener(placemarkTapListener)
        // Подпись под маркером при выборе (setText — текст подписи маркера)
        snippet?.takeIf { it.isNotBlank() }?.let { setText(it) }
        // Для маркеров без клиники (поиск/где я) — сохраняем текст в userData
        if (userData == null) {
            setUserData(Pair(title, snippet))
        }
    }

    /** Информация о клинике + действие «Маршрут». */
    private fun showClinicInfo(clinic: Clinic) {
        val view = view ?: return
        val text = listOfNotNull(
            clinic.name,
            clinic.address,
            clinic.phone.takeIf { it.isNotBlank() }
                ?.let { getString(R.string.maps_phone_template, it) }
        ).joinToString("\n")

        Snackbar.make(view, text, Snackbar.LENGTH_LONG)
            .setAction(R.string.maps_route) { openYandexMaps(clinic.point, clinic.address) }
            .show()
    }

    /** Информация о произвольной точке (результат поиска, местоположение). */
    private fun showPointInfo(title: String?, snippet: String?) {
        val view = view ?: return
        val text = listOfNotNull(title, snippet).joinToString("\n")
        if (text.isBlank()) return
        Snackbar.make(view, text, Snackbar.LENGTH_LONG)
            .setAction(R.string.maps_route) { openYandexMaps(foundPoint, lastQuery) }
            .show()
    }

    private fun moveCamera(point: GeoPoint, zoom: Float) {
        mapView.map.move(
            CameraPosition(point.toYandexPoint(), zoom, 0.0f, 0.0f)
        )
    }

    /**
     * Открывает маршрут (или поиск) в Яндекс.Картах.
     *
     * Сначала пробуется URI-схема установленного приложения, затем https-ссылка
     * (откроется в приложении, если оно установлено, иначе — в браузере).
     */
    private fun openYandexMaps(destination: GeoPoint?, query: String) {
        val params = when {
            destination != null && userPoint != null ->
                "rtext=${userPoint!!.asYandexParam()}~${destination.asYandexParam()}&rtt=auto"
            destination != null ->
                "rtext=~${destination.asYandexParam()}&rtt=auto"
            query.isNotBlank() ->
                "text=${Uri.encode(query)}"
            else ->
                "text=${Uri.encode(getString(R.string.vet_clinic_query))}"
        }

        val uris = listOf(
            "yandexmaps://maps.yandex.ru/?$params",
            "https://yandex.ru/maps/?$params"
        )

        val notFound = uris.firstOrNull { uri ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            if (intent.resolveActivity(requireContext().packageManager) == null) {
                false
            } else {
                try {
                    startActivity(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    false
                }
            }
        } == null

        if (notFound) {
            Snackbar.make(requireView(), R.string.maps_no_app, Snackbar.LENGTH_LONG).show()
        }
    }

    // ------------------------------------------------------------------
    // Маркеры
    // ------------------------------------------------------------------

    /**
     * Круглый маркер, нарисованный в коде: векторные drawable MapKit не
     * умеет (использует BitmapFactory), поэтому битмапа создаётся программно.
     */
    private fun placemarkIcon(@ColorInt color: Int, sizePx: Int): ImageProvider {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = (sizePx * 0.14f).coerceAtLeast(2f)
        val center = sizePx / 2f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            setColor(color)
        }
        canvas.drawCircle(center, center, center - stroke / 2f, fill)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            setColor(0xFFFFFFFF.toInt())
        }
        canvas.drawCircle(center, center, center - stroke / 2f, ring)

        return ImageProvider.fromBitmap(bitmap)
    }

    @ColorInt
    private fun colorPrimary(): Int = MaterialColors.getColor(
        requireView(), com.google.android.material.R.attr.colorPrimary
    )

    @ColorInt
    private fun colorAccent(): Int = MaterialColors.getColor(
        requireView(), com.google.android.material.R.attr.colorTertiary
    )

    private fun GeoPoint.toYandexPoint(): Point = Point(latitude, longitude)

    /** Формат «широта,долгота» для ссылок Яндекс.Карт. */
    private fun GeoPoint.asYandexParam(): String =
        String.format(Locale.US, "%.6f,%.6f", latitude, longitude)

    private companion object {
        const val TAG = "MapsFragment"
        const val ZOOM_NEARBY = 13.0f
        const val ZOOM_SEARCH = 16.0f
        const val PLACEMARK_SIZE_PX = 96
        const val USER_MARK_SIZE_PX = 72
    }
}
