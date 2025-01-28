package com.example.flower

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        // Inicjalizacja fragmentu mapy
        mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Sprawdzenie i uzyskanie aktualnej lokalizacji użytkownika
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        try {
            mMap.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            Log.e("MapActivity", "Błąd: ${e.message}")
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val userLocation = LatLng(it.latitude, it.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 12f))

                // Pobranie zapisanych pinezek z SharedPreferences
                val sharedPreferences = getSharedPreferences("PINS", Context.MODE_PRIVATE)
                val pins = sharedPreferences.all
                pins.forEach { entry ->
                    val coordinates = entry.value.toString().split(",")
                    val latitude = coordinates[0].toDouble()
                    val longitude = coordinates[1].toDouble()
                    val flowerName = coordinates[2]
                    addMarker(LatLng(latitude, longitude), flowerName)
                }
            }
        }.addOnFailureListener { exception ->
            Log.e("MapActivity", "Błąd lokalizacji: ${exception.message}")
        }
    }

    private fun addMarker(latLng: LatLng, title: String) {
        mMap.addMarker(MarkerOptions().position(latLng).title(title))
    }
}
