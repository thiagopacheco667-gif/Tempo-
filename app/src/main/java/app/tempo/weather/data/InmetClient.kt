package app.tempo.weather.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Avisos meteorológicos oficiais do INMET (alertas.inmet.gov.br), via API pública
 * `apiprevmet3.inmet.gov.br/avisos/ativos`.
 *
 * O aviso vale para o local quando a coordenada está dentro do polígono GeoJSON do aviso,
 * que é a área oficial delimitada pelo INMET.
 */
object InmetClient {

    private const val URL = "https://apiprevmet3.inmet.gov.br/avisos/ativos"
    // O servidor do INMET recusa clientes sem User-Agent de navegador.
    private const val UA = "Mozilla/5.0 (Linux; Android) TempoWidget/1.0"
    private val BRT: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** Os avisos só cobrem o Brasil; fora dele não há o que consultar. */
    fun covers(place: Place): Boolean =
        place.latitude in -34.0..5.5 && place.longitude in -74.5..-28.5

    fun fetchFor(place: Place): List<WeatherAlert> {
        if (!covers(place)) return emptyList()
        return parse(OpenMeteoClient.get(URL, UA), place.latitude, place.longitude)
    }

    fun parse(body: String, lat: Double, lon: Double, now: LocalDateTime = LocalDateTime.now(BRT)): List<WeatherAlert> {
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw WeatherApiException("Resposta inválida do INMET", e)
        }
        val items = listOfNotNull(root.optJSONArray("hoje"), root.optJSONArray("futuro"))
            .flatMap { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }

        return items.asSequence()
            .distinctBy { it.optString("id") }
            .filter { it.optString("encerrado") != "True" }
            .filter { a -> a.optString("poligono").takeIf { it.isNotBlank() }?.let { contains(it, lon, lat) } == true }
            .mapNotNull { a ->
                val end = a.optString("fim")
                val endTime = runCatching { LocalDateTime.parse(end, FMT) }.getOrNull()
                if (endTime != null && endTime.isBefore(now)) return@mapNotNull null
                WeatherAlert(
                    id = a.optString("id"),
                    event = a.optString("descricao").ifBlank { "Condição severa" },
                    level = level(a.optString("severidade"), a.optString("id_severidade")),
                    start = a.optString("inicio"),
                    end = end,
                    risks = pyList(a.optString("riscos")),
                    instructions = pyList(a.optString("instrucoes")),
                )
            }
            .sortedWith(compareByDescending<WeatherAlert> { it.level.ordinal }.thenBy { it.start })
            .toList()
    }

    private fun level(name: String, id: String): AlertLevel = when {
        name.equals("Grande Perigo", true) -> AlertLevel.GREAT_DANGER
        name.equals("Perigo", true) -> AlertLevel.DANGER
        name.equals("Perigo Potencial", true) -> AlertLevel.POTENTIAL
        id == "8" -> AlertLevel.GREAT_DANGER
        id == "7" -> AlertLevel.DANGER
        else -> AlertLevel.POTENTIAL
    }

    /** "riscos"/"instrucoes" chegam como texto no formato de lista: "['a', 'b']". */
    internal fun pyList(raw: String): List<String> =
        Regex("'((?:[^'\\\\]|\\\\.)*)'").findAll(raw).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
            .ifEmpty { listOf(raw.trim('[', ']', ' ')).filter { it.isNotBlank() } }

    // --- Geometria -------------------------------------------------------------------------

    internal fun contains(geoJson: String, x: Double, y: Double): Boolean {
        val g = runCatching { JSONObject(geoJson) }.getOrNull() ?: return false
        val coords = g.optJSONArray("coordinates") ?: return false
        return when (g.optString("type")) {
            "Polygon" -> inPolygon(coords, x, y)
            "MultiPolygon" -> (0 until coords.length()).any { inPolygon(coords.getJSONArray(it), x, y) }
            else -> false
        }
    }

    /** Anel externo contém o ponto e nenhum buraco o contém. */
    private fun inPolygon(rings: JSONArray, x: Double, y: Double): Boolean {
        if (rings.length() == 0 || !inRing(rings.getJSONArray(0), x, y)) return false
        return (1 until rings.length()).none { inRing(rings.getJSONArray(it), x, y) }
    }

    /** Ray casting. */
    private fun inRing(ring: JSONArray, x: Double, y: Double): Boolean {
        var inside = false
        var j = ring.length() - 1
        for (i in 0 until ring.length()) {
            val pi = ring.getJSONArray(i)
            val pj = ring.getJSONArray(j)
            val xi = pi.getDouble(0); val yi = pi.getDouble(1)
            val xj = pj.getDouble(0); val yj = pj.getDouble(1)
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}
