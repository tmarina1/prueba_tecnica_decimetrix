package com.example.prueba_tecnica_decimetrix

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.maps.MapView
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.Style
import com.mapbox.maps.CameraOptions
import androidx.core.content.ContextCompat
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin
import com.mapbox.maps.plugin.locationcomponent.location
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.geojson.Point as PointMap
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapLongClickListener

import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.TextAnchor
import com.mapbox.maps.extension.style.style

import com.example.prueba_tecnica_decimetrix.model.FavoritePoint
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager


private const val ZOOM_INCREMENT = 1.0

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationComponent: LocationComponentPlugin
    private lateinit var fabStyles: FloatingActionButton
    private lateinit var fabCenterLocation: FloatingActionButton
    private var lastKnownUserPosition: PointMap? = null
    private lateinit var fabZoomIn: FloatingActionButton
    private lateinit var fabZoomOut: FloatingActionButton
    private lateinit var fabFavorites: FloatingActionButton
    private lateinit var dataBase: DataBaseConection
    private val favoritePlaces = mutableListOf<FavoritePoint>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dataBase = DataBaseConection(this)

        mapView = findViewById(R.id.mapView)
        fabStyles = findViewById(R.id.fabStyles)
        fabCenterLocation = findViewById(R.id.fabCenterLocation)
        fabZoomIn = findViewById(R.id.fabZoomIn)
        fabZoomOut = findViewById(R.id.fabZoomOut)
        fabFavorites = findViewById(R.id.fabFavorites)

        loadMapStyle(Style.MAPBOX_STREETS)

        setupMapClickListener()

        fabStyles.setOnClickListener {
            showStyleChooser()
        }

        fabCenterLocation.setOnClickListener {
            centerOnUserLocation()
        }

        fabZoomIn.setOnClickListener {
            zoomIn()
        }

        fabZoomOut.setOnClickListener {
            zoomOut()
        }

        fabFavorites.setOnClickListener {
            showFavoritesDialog()
        }

    }

    private fun setupMapClickListener() {
        mapView.getMapboxMap().addOnMapLongClickListener { point ->
            showPointTypeSelectionDialog(point)
            true
        }
    }

    private fun loadFavoritesFromDatabase() {
        val favorites = dataBase.getAllFavorites()
        favorites.forEach {
            if (it.isAlertPoint) {
                addAlertMarker(it.longitude, it.latitude)
            } else {
                addMarker(it.longitude, it.latitude)
            }
        }
    }

    private fun zoomIn() {
        val currentZoom = mapView.getMapboxMap().cameraState.zoom
        val newZoom = currentZoom + ZOOM_INCREMENT
        updateCameraZoom(newZoom)
    }

    private fun zoomOut() {
        val currentZoom = mapView.getMapboxMap().cameraState.zoom
        val newZoom = currentZoom - ZOOM_INCREMENT
        updateCameraZoom(newZoom)
    }

    private fun updateCameraZoom(zoomLevel: Double) {
        val cameraOptions = CameraOptions.Builder()
            .zoom(zoomLevel)
            .center(mapView.getMapboxMap().cameraState.center)
            .bearing(mapView.getMapboxMap().cameraState.bearing)
            .pitch(mapView.getMapboxMap().cameraState.pitch)
            .build()
        mapView.getMapboxMap().setCamera(cameraOptions)
    }

    private fun centerOnUserLocation() {
        lastKnownUserPosition?.let { userPosition ->
            val cameraOptions = CameraOptions.Builder()
                .center(userPosition)
                .zoom(15.0)
                .build()
            mapView.getMapboxMap().setCamera(cameraOptions)
        }
    }

    private fun addMarker(longitude: Double, latitude: Double) {
        val annotationApi = mapView.annotations
        val pointAnnotationManager = annotationApi.createPointAnnotationManager()

        val bitmap = (ResourcesCompat.getDrawable(resources, R.drawable.yellow_marker, null) as BitmapDrawable).bitmap

        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(PointMap.fromLngLat(longitude, latitude))
            .withIconImage(bitmap)
        pointAnnotationManager.create(pointAnnotationOptions)
    }

    private fun showStyleChooser() {
        val styles = arrayOf("Streets", "Satellite", "Satellite Streets", "Dark", "Light")
        val builder = AlertDialog.Builder(this)
            .setTitle("Seleccionar Estilo de Mapa")
            .setItems(styles) { dialog, which ->
                val styleUri = when (which) {
                    0 -> Style.MAPBOX_STREETS
                    1 -> Style.SATELLITE
                    2 -> Style.SATELLITE_STREETS
                    3 -> Style.DARK
                    4 -> Style.LIGHT
                    else -> Style.MAPBOX_STREETS
                }

                loadMapStyle(styleUri)

                dialog.dismiss()
            }
        builder.show()
    }

    private fun loadMapStyle(styleUri: String) {
        mapView.getMapboxMap().loadStyle(styleExtension = style(styleUri) {
            +geoJsonSource("places-source") {
                url(getString(R.string.URL_geojson))
            }

            +symbolLayer("places-layer", "places-source") {
                iconImage("marker")
                iconAnchor(IconAnchor.BOTTOM)
                textField("{name}")
                textAnchor(TextAnchor.TOP)
            }
        }) { style ->
            Log.d("MapboxStyle", "Style loaded successfully: $styleUri")

            val drawable = ResourcesCompat.getDrawable(resources, R.drawable.red_marker, null)
            if (drawable is BitmapDrawable) {
                style.addImage("marker", drawable.bitmap)
            }

            if (styleUri == Style.MAPBOX_STREETS && lastKnownUserPosition == null) {
                mapView.getMapboxMap().setCamera(
                    CameraOptions.Builder()
                        .center(PointMap.fromLngLat(0.0, 0.0))
                        .zoom(2.0)
                        .build()
                )

                if (checkLocationPermissions()) {
                    enableLocationComponent(style)
                } else {
                    requestLocationPermissions()
                }
            }
            loadFavoritesFromDatabase()
        }
    }

    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            1
        )
    }

    private fun enableLocationComponent(style: Style) {
        locationComponent = mapView.location
        locationComponent.updateSettings {
            this.enabled = true
            this.pulsingEnabled = true
        }

        locationComponent.addOnIndicatorPositionChangedListener { position ->
            lastKnownUserPosition = position
            val cameraOptions = CameraOptions.Builder()
                .center(position)
                .zoom(15.0)
                .build()
            mapView.getMapboxMap().setCamera(cameraOptions)
        }
    }

    private fun showFavoritesDialog() {
        val favorites = dataBase.getAllFavorites()

        if (favorites.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Favoritos")
                .setMessage("No tienes favoritos aún.")
                .setPositiveButton("OK", null)
                .show()
        } else {
            val favoriteNames = favorites.map { it.name }.toTypedArray()

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Favoritos")
                .setItems(favoriteNames) { _, which ->
                    val selectedPlace = favorites[which]
                    centerMapOnFavorite(selectedPlace)
                }
                .show()
        }
    }

    private fun showPointTypeSelectionDialog(point: PointMap) {
        val options = arrayOf("Punto Normal", "Punto de Alerta")

        AlertDialog.Builder(this)
            .setTitle("Seleccionar tipo de punto")
            .setItems(options) { _, which ->
                val isAlertPoint = which == 1

                val builder = AlertDialog.Builder(this)
                builder.setTitle("Agregar favorito")

                val input = android.widget.EditText(this)
                builder.setView(input)

                builder.setPositiveButton("Guardar") { _, _ ->
                    val name = input.text.toString()
                    if (name.isNotEmpty()) {
                        val favorite = FavoritePoint(
                            name = name,
                            latitude = point.latitude(),
                            longitude = point.longitude(),
                            isAlertPoint = isAlertPoint
                        )

                        dataBase.saveFavoritePoint(favorite)

                        if (isAlertPoint) {
                            addAlertMarker(point.longitude(), point.latitude())
                        } else {
                            addMarker(point.longitude(), point.latitude())
                        }

                        loadFavoritesFromDatabase()
                    }
                }

                builder.setNegativeButton("Cancelar") { dialog, _ ->
                    dialog.cancel()
                }

                builder.show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


    private fun addAlertMarker(longitude: Double, latitude: Double) {
        val annotationApi = mapView.annotations
        val pointAnnotationManager = annotationApi.createPointAnnotationManager()

        val bitmap = (ResourcesCompat.getDrawable(resources, R.drawable.yellow_marker, null) as BitmapDrawable).bitmap

        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(PointMap.fromLngLat(longitude, latitude))
            .withIconImage(bitmap)

        val annotation = pointAnnotationManager.create(pointAnnotationOptions)

        setupPulseAnimation(annotation, pointAnnotationManager)
    }

    private fun setupPulseAnimation(annotation: PointAnnotation, manager: PointAnnotationManager) {
        val handler = Handler(Looper.getMainLooper())
        val pulseRunnable = object : Runnable {
            private var scale = 1.0
            private var increasing = true

            override fun run() {
                if (increasing) {
                    scale += 0.05
                    if (scale >= 1.5) increasing = false
                } else {
                    scale -= 0.05
                    if (scale <= 1.0) increasing = true
                }

                annotation.iconSize = scale
                manager.update(annotation)

                handler.postDelayed(this, 50)
            }
        }
        handler.post(pulseRunnable)
    }

    private fun centerMapOnFavorite(place: FavoritePoint) {
        val cameraOptions = CameraOptions.Builder()
            .center(PointMap.fromLngLat(place.longitude, place.latitude))
            .zoom(15.0)
            .build()
        mapView.getMapboxMap().setCamera(cameraOptions)
    }
}