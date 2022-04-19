package com.example.myapplication.data

import android.hardware.SensorManager
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import java.text.NumberFormat
import java.time.temporal.JulianFields
import java.util.*
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

class Data : BaseObservable() {
    @Bindable
    var requestingLocationUpdates: Boolean = false

    @Bindable
    var requestingSensorUpdates: Boolean = false

    @Bindable
    var longitude: Double = 0.0

    @Bindable
    var latitude: Double = 0.0

    @Bindable
    var altitude: Double = 0.0

    @Bindable
    val accelerometerReading = FloatArray(3)

    @Bindable
    val magnetometerReading = FloatArray(3)

    @Bindable
    val rotationMatrix = FloatArray(9)

    @Bindable
    val orientationAngles = FloatArray(3)

    @Bindable("longitude")
    fun getLongitudeStr(): String {
        return getNumberFormat().format(longitude)
    }
    @Bindable("latitude")
    fun getLatitudeStr(): String {
        return getNumberFormat().format(latitude)
    }
    @Bindable("altitude")
    fun getAltitudeStr(): String {
        return getNumberFormat().format(altitude)
    }

    @Bindable("accelerometerReading", "magnetometerReading")
    fun getRotation(): FloatArray {
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        SensorManager.getOrientation(
            rotationMatrix,
            orientationAngles
        )

        return rotationMatrix
    }

    @Bindable("rotation")
    fun getAzimuthStr(): String {
        return getNumberFormat().format(orientationAngles[0])
    }

    @Bindable("rotation")
    fun getPitchStr(): String {
        return getNumberFormat().format(orientationAngles[1])
    }

    @Bindable("rotation")
    fun getRollStr(): String {
        return getNumberFormat().format(orientationAngles[2])
    }

    private fun getNumberFormat(): NumberFormat {
        val nf = NumberFormat.getInstance()
        nf.maximumFractionDigits = 8
        return nf
    }

    private fun getJulianDate(): Double {
        val tz: TimeZone = TimeZone.getTimeZone("UTC")
        val cal: Calendar = GregorianCalendar(tz)

        val M: Int = cal.get(Calendar.MONTH)
        val m: Int = M + (if (M <= 2) 12 else 0)
        val y: Int = cal.get(Calendar.YEAR) - (if (M <= 2) 1 else 0)
        val h: Double = cal.get(Calendar.HOUR) / (if (cal.get(Calendar.HOUR) != 0) 24.0 else 1.0)
        val min: Double = cal.get(Calendar.MINUTE) / (if (cal.get(Calendar.MINUTE) != 0) 1440.0 else 1.0)
        val s: Double = cal.get(Calendar.SECOND) / (if (cal.get(Calendar.SECOND) != 0) 86400.0 else 1.0)
        val ms: Double = cal.get(Calendar.MILLISECOND) / (if (cal.get(Calendar.MILLISECOND) != 0) 86400000.0 else 1.0)

        return (
            floor(365.25 * y)
            + floor(30.6001 * (m + 1.0))
            + cal.get(Calendar.DAY_OF_MONTH)
            + h + min + s + ms
            + 1720981.5
        )
    }

    private fun getJulianCenturiesFromJ2000(jde: Double): Double {
        return (getJulianDate() - 2451545.0) / 36525.0
    }

    private fun transformLonLatToRaDec(longitude: Double, latitude: Double, altitude: Double) {
        var ra = 0.0
        var dec = 0.0

        val deltaPsi = 0.0 // todo
        val epsilon = 0.0 // todo
        val deltaEpsilon = 0.0 // todo

        val cdp = cos(deltaPsi)
        val sdp = sin(deltaPsi)
        val ce = cos(epsilon)
        val se = sin(epsilon)
        val cede = cos(epsilon + deltaEpsilon)
        val sede = sin(epsilon + deltaEpsilon)

        val nutationMatrix = DoubleArray(9);
        nutationMatrix[0] = cdp
        nutationMatrix[1] = -ce * sdp
        nutationMatrix[2] = -se * sdp
        nutationMatrix[3] = -cede * sdp
        nutationMatrix[4] = cede * ce * cdp - sede * se
        nutationMatrix[5] = cede * se * cdp + sede * ce
        nutationMatrix[6] = sede * sdp
        nutationMatrix[7] = -sede * ce * cdp + cede * se
        nutationMatrix[8] = -sede * se * cdp + cede * ce

        // difference between the hour angle of the true equinox and the mean equinox
        val alphaE = atan(nutationMatrix[5] / nutationMatrix[4])

        val tu = 0.0
        val tu2 = tu * tu
        val tu3 = tu2 * tu
        val gmst0 = 6.0 + 41.0 / 60.0 + 50.54841 / 3600.0
            + 8640184.812866 / 3600.0 * tu
            + 0.093104 / 3600.0 * tu2
            - 6.2e-6 / 3600.0 * tu3

        val ut1 = 0.0 // todo
        val gmst = 1.002737909350795 * ut1 + gmst0

        // greenwich apparent sideral time
        val gast = gmst + alphaE
    }
}