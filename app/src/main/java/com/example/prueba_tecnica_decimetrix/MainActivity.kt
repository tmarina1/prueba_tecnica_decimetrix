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
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.geojson.Point as PointMap
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapLongClickListener

import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.TextAnchor
import com.mapbox.maps.extension.style.style

import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import com.example.prueba_tecnica_decimetrix.DataBaseConection

private const val ZOOM_INCREMENT = 1.0

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationComponent: LocationComponentPlugin
    private lateinit var fabStyles: FloatingActionButton
    private lateinit var fabCenterLocation: FloatingActionButton
    private var lastKnownUserPosition: PointMap? = null
    private lateinit var fabZoomIn: FloatingActionButton
    private lateinit var fabZoomOut: FloatingActionButton
    private lateinit var DataBase: DataBaseConection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        fabStyles = findViewById(R.id.fabStyles)
        fabCenterLocation = findViewById(R.id.fabCenterLocation)
        fabZoomIn = findViewById(R.id.fabZoomIn)
        fabZoomOut = findViewById(R.id.fabZoomOut)

        mapView.getMapboxMap().loadStyle(styleExtension = style(Style.MAPBOX_STREETS) {
            +geoJsonSource("places-source") {
                url("https://d2ad6b4ur7yvpq.cloudfront.net/naturalearth-3.3.0/ne_50m_populated_places_simple.geojson")
            }

            +symbolLayer("places-layer", "places-source") {
                iconImage("marker")
                iconAnchor(IconAnchor.BOTTOM)
                textField("{name}")
                textAnchor(TextAnchor.TOP)
            }
        }) { style ->
            Log.d("MapboxStyle", "Style loaded successfully")
            val drawable = ResourcesCompat.getDrawable(resources, R.drawable.red_marker, null)
            if (drawable is BitmapDrawable) {
                style.addImage("marker", drawable.bitmap)
            }

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


        mapView.getMapboxMap().addOnMapLongClickListener() { point ->
            Log.d("MapClick", "Long Click Detected at: Long=${point.longitude()}, Lat=${point.latitude()}")
            addMarker(point.longitude(), point.latitude())
            true
        }

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

        val bitmap = (ResourcesCompat.getDrawable(resources, R.drawable.red_marker, null) as BitmapDrawable).bitmap

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
                when (which) {
                    0 -> mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS)
                    1 -> mapView.getMapboxMap().loadStyleUri(Style.SATELLITE)
                    2 -> mapView.getMapboxMap().loadStyleUri(Style.SATELLITE_STREETS)
                    3 -> mapView.getMapboxMap().loadStyleUri(Style.DARK)
                    4 -> mapView.getMapboxMap().loadStyleUri(Style.LIGHT)
                }
                dialog.dismiss()
            }
        builder.show()
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

    private fun showPointNameDialog(point: PointMap) {
        val input = EditText(this)
        val favoriteCheckbox = CheckBox(this)
        favoriteCheckbox.text = "Guardar como favorito"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(input)
            addView(favoriteCheckbox)
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Nombre del Punto")
            .setView(layout)
            .setPositiveButton("Guardar") { dialog, _ ->
                val pointName = input.text.toString()
                val isFavorite = favoriteCheckbox.isChecked
                addPointToMap(point, pointName, isFavorite)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.cancel()
            }
        builder.show()
    }

    private fun addPointToMap(point: PointMap, name: String, isFavorite: Boolean) {
        mapView.getMapboxMap().getStyle { style ->
            val sourceId = "point-source-${placedPoints.size}"
            val layerId = "point-layer-${placedPoints.size}"
            placedPoints.add(Triple(point, name, isFavorite)) // Almacena si es favorito

            style.addSource(geoJsonSource(sourceId) {
                geometry(point)
            })

            style.addLayer(symbolLayer(layerId, sourceId) {
                iconImage("red_marker")
                textField(name)
                textSize(12.0)
                textAnchor(TextAnchor.BOTTOM)
                textOffset(listOf(0.0, -0.7))
            })

            if (isFavorite) {
                DataBase.saveFavoritePoint(point, name)
            }
        }

    /*
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableLocationComponent()
        }
    }
    */

}
