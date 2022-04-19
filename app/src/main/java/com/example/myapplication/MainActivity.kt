package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.myapplication.data.Data
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*


val REQUEST_CHECK_SETTINGS = 0x1
val REQUESTING_LOCATION_UPDATES_KEY = "requesting-location-updates-key"
val LOCATION_LON_KEY = "location-lon"
val LOCATION_LAT_KEY = "location-lat"
val LOCATION_ALT_KEY = "location-alt"

class MainActivity : AppCompatActivity(), SensorEventListener  {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager
    private lateinit var binding: ActivityMainBinding
    private val viewmodel: Data = Data()

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.data = viewmodel
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        locationCallback = object : LocationCallback() {
            override fun onLocationAvailability(p0: LocationAvailability) {
                Log.i("Location", "onLocationAvailability: ${p0.isLocationAvailable}")
            }

            override fun onLocationResult(p0: LocationResult) {
                Log.i("Location", "onLocationResult: $p0")
                if (p0.locations.isEmpty()) {
                    return
                }

                viewmodel.longitude = p0.locations.last().longitude
                viewmodel.latitude = p0.locations.last().latitude
                viewmodel.altitude = p0.locations.last().altitude

                binding.data = viewmodel
                printLocation()
            }
        }

        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            var hasLocationPermission: Boolean = false
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    hasLocationPermission = true
                    Log.i("Permission", "Fine location granted")
                }
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    hasLocationPermission = true
                    Log.i("Permission", "Coarse location granted")
                }
            }

            if (!hasLocationPermission) {
                Log.i("Permission", "Missing location permissions")
                return@registerForActivityResult
            }

            Log.i("Permission", "Location permissions granted")
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                Log.i("Location", "lastLocation: $loc")
                loc?.let {
                    viewmodel.longitude = loc.longitude
                    viewmodel.latitude = loc.latitude
                    viewmodel.altitude = loc.altitude
                    binding.data = viewmodel
                    printLocation()
                }
            }

            createLocationRequest()
        }

        // Before you perform the actual permission request, check whether your app
        // already has the permissions, and whether your app needs to show a permission
        // rationale dialog. For more details, see Request permissions.
        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY, viewmodel.requestingLocationUpdates)
        outState.putDouble(LOCATION_LON_KEY, viewmodel.longitude)
        outState.putDouble(LOCATION_LAT_KEY, viewmodel.latitude)
        outState.putDouble(LOCATION_ALT_KEY, viewmodel.altitude)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
        startSensorsUpdates()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
        stopSensorsUpdates()
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 500
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            // isWaitForAccurateLocation = false
            // numUpdates = 10
        }

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setNeedBle(false)
            .setAlwaysShow(false)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val locationSettingsReq = builder.build()
        Log.d("Location", "Location settings req: $locationSettingsReq")

        client.checkLocationSettings(locationSettingsReq)
            .addOnSuccessListener { locationSettingsResponse ->
                Log.i("Location", "Location settings response: $locationSettingsResponse")
                // All location settings are satisfied. The client can initialize
                // location requests here.
                startLocationUpdates()
            }
            .addOnFailureListener { exception ->
                Log.d("Location", "Location settings exception: ${exception.message}")
                if (exception is ResolvableApiException){
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        exception.startResolutionForResult(this@MainActivity, REQUEST_CHECK_SETTINGS)
                    } catch (sendEx: IntentSender.SendIntentException) {
                        // Ignore the error.
                    }
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!this::fusedLocationClient.isInitialized || viewmodel.requestingLocationUpdates) {
            return
        }

        Log.i("Location", "starting updates")
        viewmodel.requestingLocationUpdates = true

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        if (!this::fusedLocationClient.isInitialized || !viewmodel.requestingLocationUpdates) {
            return
        }

        Log.i("Location", "stopping updates")
        fusedLocationClient.removeLocationUpdates(locationCallback)
        viewmodel.requestingLocationUpdates = false
    }

    private fun startSensorsUpdates() {
        if (viewmodel.requestingSensorUpdates) {
            return
        }

        viewmodel.requestingSensorUpdates = true
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this,
                magneticField,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    private fun stopSensorsUpdates() {
        if (!viewmodel.requestingSensorUpdates) {
            return
        }

        sensorManager.unregisterListener(this)
        viewmodel.requestingSensorUpdates = false
    }

    @SuppressLint("NewApi")
    private fun printLocation() {
        if (binding.data === null) {
           return
        }

        Log.i("Location", "Longitude: ${viewmodel.longitude}")
        Log.i("Location", "Latitude: ${viewmodel.latitude}")
        Log.i("Location", "Altitude: ${viewmodel.altitude}")
    }

    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CHECK_SETTINGS -> when (resultCode) {
                RESULT_OK -> {
                    Log.i("Location", "User agreed to make required location settings changes.")
                    startLocationUpdates()
                }
                RESULT_CANCELED -> Log.i(
                    "Location",
                    "User chose not to make required location settings changes."
                )
            }
        }
    }

    override fun onSensorChanged(p0: SensorEvent?) {
        p0 ?: return
        if (p0.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(
                p0.values,
                0, viewmodel.accelerometerReading,
                0, viewmodel.accelerometerReading.size
            )
        } else if (p0.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(
                p0.values,
                0, viewmodel.magnetometerReading,
                0, viewmodel.magnetometerReading.size
            )
        }

        binding.data = viewmodel
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
}