package app.tempo.weather.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import app.tempo.weather.data.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Localização via LocationManager do próprio Android (sem Google Play Services).
 * Usa precisão aproximada: suficiente para meteorologia e mais econômica em bateria.
 */
class DeviceLocator(private val context: Context) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** Localização do aparelho ligada nas configurações rápidas. */
    fun isLocationEnabled(): Boolean = runCatching { LocationManagerCompat.isLocationEnabled(lm) }.getOrDefault(true)

    /**
     * Retorna uma posição recente, ou null se não houver permissão/sinal.
     * Tenta todos os provedores disponíveis em sequência: em vários aparelhos (ex.: Xiaomi/HyperOS
     * sem serviços do Google) o provedor "fused" existe mas nunca entrega posição.
     */
    @SuppressLint("MissingPermission")
    suspend fun locate(maxAgeMs: Long = 30 * 60_000L): Location? {
        if (!hasPermission()) return null
        val last = lastKnown()
        if (last != null && System.currentTimeMillis() - last.time <= maxAgeMs) return last
        for (provider in liveProviders()) {
            val loc = current(provider)
            if (loc != null) return loc
        }
        // Sem posição nova: uma posição antiga ainda indica a cidade corretamente na maioria dos casos.
        return last
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? = allProviders().mapNotNull {
        runCatching { lm.getLastKnownLocation(it) }.getOrNull()
    }.maxByOrNull { it.time }

    @SuppressLint("MissingPermission")
    private suspend fun current(provider: String): Location? = withTimeoutOrNull(10_000L) {
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /** Provedores ativos, do mais rápido/econômico ao mais lento. */
    private fun liveProviders(): List<String> = buildList {
        add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }.filter { p -> p in allProviders() && runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }

    private fun allProviders(): List<String> = runCatching { lm.allProviders }.getOrDefault(emptyList())

    /**
     * Converte coordenadas em nome de cidade com o Geocoder do sistema.
     * Se o serviço não estiver disponível, reaproveita o nome anterior quando a posição é próxima.
     */
    suspend fun describe(location: Location, previous: Place?): Place {
        val address = reverseGeocode(location.latitude, location.longitude)
        val name = address?.locality ?: address?.subAdminArea ?: address?.adminArea
        val region = listOfNotNull(
            address?.adminArea?.takeIf { it != name }?.let { abbreviateState(it, address.countryCode) },
            address?.countryCode,
        ).joinToString(", ")
        if (name != null) return Place(name, region, location.latitude, location.longitude)

        val near = previous != null && distanceKm(previous, location) < 10.0
        return Place(
            name = if (near) previous!!.name else "Localização atual",
            region = if (near) previous!!.region else "",
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale("pt", "BR"))
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(8_000L) {
                suspendCancellableCoroutine { cont ->
                    try {
                        geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
            }
        }
    }

    private fun distanceKm(p: Place, l: Location): Double {
        val out = FloatArray(1)
        Location.distanceBetween(p.latitude, p.longitude, l.latitude, l.longitude, out)
        return out[0] / 1000.0
    }

    /** Siglas dos estados brasileiros para uma linha de localização mais limpa. */
    private fun abbreviateState(state: String, countryCode: String?): String {
        if (countryCode != "BR") return state
        return BR_STATES[state.lowercase(Locale.ROOT)] ?: state
    }

    companion object {
        private val BR_STATES = mapOf(
            "acre" to "AC", "alagoas" to "AL", "amapá" to "AP", "amazonas" to "AM", "bahia" to "BA",
            "ceará" to "CE", "distrito federal" to "DF", "espírito santo" to "ES", "goiás" to "GO",
            "maranhão" to "MA", "mato grosso" to "MT", "mato grosso do sul" to "MS",
            "minas gerais" to "MG", "pará" to "PA", "paraíba" to "PB", "paraná" to "PR",
            "pernambuco" to "PE", "piauí" to "PI", "rio de janeiro" to "RJ",
            "rio grande do norte" to "RN", "rio grande do sul" to "RS", "rondônia" to "RO",
            "roraima" to "RR", "santa catarina" to "SC", "são paulo" to "SP", "sergipe" to "SE",
            "tocantins" to "TO",
            "state of são paulo" to "SP", "state of rio de janeiro" to "RJ",
        )
    }
}
