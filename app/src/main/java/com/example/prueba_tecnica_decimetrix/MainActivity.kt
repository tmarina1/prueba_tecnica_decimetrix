package com.example.prueba_tecnica_decimetrix

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.CameraOptions
import androidx.core.content.ContextCompat
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin
import com.mapbox.maps.plugin.locationcomponent.location
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
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

    /**
     * Este método se encarga de detectar pulsaciones largas en el mapa.
     * Cuando el usuario mantiene presionado un punto en el mapa, se muestra
     * un diálogo para seleccionar el tipo de punto a crear.
     */
    private fun setupMapClickListener() {
        mapView.getMapboxMap().addOnMapLongClickListener { point ->
            showPointTypeSelectionDialog(point)
            true
        }
    }

    /**
    * Este método se encarga de extraer los marcadores favoritos del usuario almacenados en la base de datos,
     * además de llamar a las funciones encargadas de agregar los marcadores en el mapa.
    * */
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


    /**
    * Este método se encarga de gestionar el aumento de zoom en una unidad.
    * */
    private fun zoomIn() {
        val actualZoom = mapView.getMapboxMap().cameraState.zoom
        val newZoom = actualZoom + ZOOM_INCREMENT
        updateCameraZoom(newZoom)
    }

    /**
    *  Este método se encarga de gestionar la disminución de zoom en una unidad.
    * */
    private fun zoomOut() {
        val actualZoom = mapView.getMapboxMap().cameraState.zoom
        val newZoom = actualZoom - ZOOM_INCREMENT
        updateCameraZoom(newZoom)
    }

    /**
    * Este método se encarga de aplicar el zoom definido en las dos funciones anteriores.
    * */
    private fun updateCameraZoom(zoomLevel: Double) {
        val cameraOptions = CameraOptions.Builder()
            .zoom(zoomLevel)
            .center(mapView.getMapboxMap().cameraState.center)
            .bearing(mapView.getMapboxMap().cameraState.bearing)
            .pitch(mapView.getMapboxMap().cameraState.pitch)
            .build()
        mapView.getMapboxMap().setCamera(cameraOptions)
    }

    /**
    * Este método se encarga de centrar el mapa en la última ubicación del usuario.
    * */
    private fun centerOnUserLocation() {
        lastKnownUserPosition?.let { userPosition ->
            val cameraOptions = CameraOptions.Builder()
                .center(userPosition)
                .zoom(15.0)
                .build()
            mapView.getMapboxMap().setCamera(cameraOptions)
        }
    }

    /**
    * Este método se encarga de agregar un marcador normal definido por el usuario en el mapa.
    * */
    private fun addMarker(longitude: Double, latitude: Double) {
        val annotationInstance = mapView.annotations
        val marker = annotationInstance.createPointAnnotationManager()

        val markerImage = (ResourcesCompat.getDrawable(resources, R.drawable.yellow_marker, null) as BitmapDrawable).bitmap

        val markerOptions = PointAnnotationOptions().withPoint(PointMap.fromLngLat(longitude, latitude)).withIconImage(markerImage)
        marker.create(markerOptions)
    }

    /*
    * Este método se encarga de mostrar un Dialog donde el usuario puede seleccionar que tipo de estilo de mapa quiere.
    * */
    private fun showStyleChooser() {
        val stylesTypes = arrayOf("Streets", "Satellite", "Satellite Streets", "Dark", "Light")
        val builder = AlertDialog.Builder(this)
            .setTitle("Seleccionar estilo de mapa")
            .setItems(stylesTypes) { dialog, which ->
                val styleType = when (which) {
                    0 -> Style.MAPBOX_STREETS
                    1 -> Style.SATELLITE
                    2 -> Style.SATELLITE_STREETS
                    3 -> Style.DARK
                    4 -> Style.LIGHT
                    else -> Style.MAPBOX_STREETS
                }
                loadMapStyle(styleType)

                dialog.dismiss()
            }
        builder.show()
    }

    /**
    * Este método se encarga de cargar los puntos que se encuentran en el JSON de la URL establecida, además de
     * invocar diferentes funciones como por ejemplo la de cargar los puntos favoritos del usuario.
    * */
    private fun loadMapStyle(styleType: String) {
        mapView.getMapboxMap().loadStyle(styleExtension = style(styleType) {
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
            val marker = ResourcesCompat.getDrawable(resources, R.drawable.red_marker, null)
            if (marker is BitmapDrawable) {
                style.addImage("marker", marker.bitmap)
            }

            if (styleType == Style.MAPBOX_STREETS && lastKnownUserPosition == null) {
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

    /**
    * Este método se encarga de validar los permisos para acceder a la ubicación del usuario.
    * */
    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /*
    * Este método se encarga de solicitar permisos para acceder a la ubicación del usuario en caso de ser necesario.
    * */
    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
             arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            1
        )
    }

/**
 * Este método habilita el componente de ubicación del usuario en el mapa.
 * */
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

    /**
    * Este método se encarga de crear y mostrar el Dialog en donde se visualizan los puntos seleccionados como
    * favoritos por el usuario.
    * */
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
                    val selectedFavorite = favorites[which]
                    centerMapOnFavorite(selectedFavorite)
                }
                .show()
        }
    }

    /**
    * Este método se encarga de crear y mostrar el Dialog que permite al usuario agregar marcadores en el mapa.
    * */
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

    /**
    * Este método se encarga de agregar un marcador tipo alerta definido por el usuario en el mapa.
    * */
    private fun addAlertMarker(longitude: Double, latitude: Double) {
        val annotationInstance = mapView.annotations
        val pointAnnotationManager = annotationInstance.createPointAnnotationManager()

        val markerImage = (ResourcesCompat.getDrawable(resources, R.drawable.yellow_marker, null) as BitmapDrawable).bitmap

        val pointAnnotationOptions = PointAnnotationOptions()
            .withPoint(PointMap.fromLngLat(longitude, latitude))
            .withIconImage(markerImage)

        val annotation = pointAnnotationManager.create(pointAnnotationOptions)

        setupPulseAnimation(annotation, pointAnnotationManager)
    }

    /**
    * Este método se encarga de definir el pulso y comportamiento
    * que va tener los marcadores tipo alerta agregados por el usuario.
    * */
    private fun setupPulseAnimation(annotation: PointAnnotation, manager: PointAnnotationManager) {
        val handler = Handler(Looper.getMainLooper())
        val pulse = object : Runnable {
            private var scale = 1.0
            private var aument = true

            override fun run() {
                if (aument) {
                    scale += 0.05
                    if (scale >= 1.5) aument = false
                } else {
                    scale -= 0.05
                    if (scale <= 1.0) aument = true
                }

                annotation.iconSize = scale
                manager.update(annotation)

                handler.postDelayed(this, 50)
            }
        }
        handler.post(pulse)
    }

    /**
    * Este método permite al usuario moverse entre sus marcadores favoritos, centrandolos según los seleccione.
    * */
    private fun centerMapOnFavorite(place: FavoritePoint) {
        val cameraOptions = CameraOptions.Builder()
            .center(PointMap.fromLngLat(place.longitude, place.latitude))
            .zoom(15.0)
            .build()
        mapView.getMapboxMap().setCamera(cameraOptions)
    }
}