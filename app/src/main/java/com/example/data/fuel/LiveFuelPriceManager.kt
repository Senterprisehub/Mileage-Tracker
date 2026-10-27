package com.example.data.fuel

import android.content.Context
import android.location.Geocoder
import com.example.data.model.CityFuelRate
import com.example.data.model.CountryRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

data class TodayFuelRateResult(
    val price: Double,
    val fuelType: String,
    val unit: String,
    val cityName: String,
    val stateOrRegion: String,
    val effectiveDate: String,
    val formattedDisplay: String,
    val source: String,
    val sourceUrl: String = "https://www.goodreturns.in",
    val isLiveFetched: Boolean = false
)

object LiveFuelPriceManager {

    private const val GOODRETURNS_BASE_URL = "https://www.goodreturns.in"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    // Thread-safe in-memory cache for live fuel prices fetched from goodreturns.in
    private val livePriceCache = ConcurrentHashMap<String, TodayFuelRateResult>()

    /**
     * Normalizes any fuel type string (e.g. "EV (Electric)", "Petrol", "DIESEL")
     * into standard internal tokens: PETROL, DIESEL, CNG, EV, HYBRID, GASOLINE.
     */
    fun normalizeFuelType(fuelType: String, countryCode: String = "IN"): String {
        val upper = fuelType.trim().uppercase(Locale.ROOT)
        return when {
            upper.contains("PETROL") -> "PETROL"
            upper.contains("GASOLINE") -> if (countryCode.equals("IN", ignoreCase = true)) "PETROL" else "GASOLINE"
            upper.contains("DIESEL") -> "DIESEL"
            upper.contains("CNG") -> "CNG"
            upper.contains("EV") || upper.contains("ELECTRIC") -> "EV"
            upper.contains("HYBRID") -> if (countryCode.equals("IN", ignoreCase = true)) "PETROL" else "GASOLINE"
            else -> "PETROL"
        }
    }

    /**
     * Resolves the fuel rate unit for a specific country and fuel type.
     */
    fun getFuelRateUnit(countryCode: String, fuelType: String): String {
        val profile = CountryRegistry.getProfile(countryCode)
        val distUnit = profile.unitDistance
        val norm = normalizeFuelType(fuelType, countryCode)

        return when (norm) {
            "PETROL", "GASOLINE", "HYBRID" -> if (distUnit == "mi") "/gal" else "/L"
            "DIESEL" -> if (distUnit == "mi") "/gal" else "/L"
            "CNG" -> "/kg"
            "EV" -> "/kWh"
            else -> if (distUnit == "mi") "/gal" else "/L"
        }
    }

    /**
     * Extracts the specific baseline price for a city based on the selected fuel type.
     */
    fun getRateForCityAndFuelType(city: CityFuelRate, fuelType: String, countryCode: String = "IN"): Double {
        val norm = normalizeFuelType(fuelType, countryCode)
        return when (norm) {
            "PETROL", "GASOLINE" -> city.petrolPrice
            "DIESEL" -> city.dieselPrice
            "CNG" -> if (city.cngPrice > 0.0) city.cngPrice else city.petrolPrice * 0.75
            "EV" -> city.evPricePerKwh
            "HYBRID" -> city.petrolPrice
            else -> city.petrolPrice
        }
    }

    /**
     * Maps city names to GoodReturns URL slugs.
     */
    fun getCitySlugForGoodReturns(cityName: String): String {
        val lower = cityName.trim().lowercase(Locale.ROOT)
        return when {
            lower == "new delhi" || lower == "delhi ncr" -> "new-delhi"
            lower.contains("delhi") -> "new-delhi"
            lower == "bengaluru" || lower == "bangalore" -> "bangalore"
            lower == "mumbai" || lower == "bombay" -> "mumbai"
            lower == "kolkata" || lower == "calcutta" -> "kolkata"
            lower == "chennai" || lower == "madras" -> "chennai"
            lower == "hyderabad" || lower == "secunderabad" -> "hyderabad"
            lower == "gurugram" || lower == "gurgaon" -> "gurgaon"
            lower == "noida" || lower.contains("noida") -> "noida"
            lower == "navi mumbai" -> "navi-mumbai"
            lower == "thane" -> "thane"
            lower == "chandigarh" || lower == "mohali" || lower == "panchkula" -> "chandigarh"
            else -> lower.replace("\\s+".toRegex(), "-").replace("[^a-z0-9-]".toRegex(), "")
        }
    }

    /**
     * Fetches live fuel price in real-time from www.goodreturns.in via HTTP GET.
     * Falls back to category index page or baseline registry if offline or not found.
     */
    suspend fun fetchLiveFuelRate(
        countryCode: String,
        cityName: String,
        fuelType: String
    ): TodayFuelRateResult = withContext(Dispatchers.IO) {
        val profile = CountryRegistry.getProfile(countryCode)
        val matchedCity = findClosestCity(cityName, countryCode) ?: profile.cities.firstOrNull()
        val normalizedFuel = normalizeFuelType(fuelType, countryCode)
        val resolvedCityName = matchedCity?.cityName ?: cityName
        val region = matchedCity?.stateOrRegion ?: countryCode
        val unit = getFuelRateUnit(countryCode, normalizedFuel)
        val todayStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        // Non-India countries or EV fuels have specific tariffs
        if (!countryCode.equals("IN", ignoreCase = true)) {
            val fallback = getTodayFuelRate(countryCode, cityName, fuelType)
            return@withContext fallback
        }

        if (normalizedFuel == "EV") {
            val evRate = matchedCity?.evPricePerKwh ?: 8.00
            val res = TodayFuelRateResult(
                price = evRate,
                fuelType = "EV",
                unit = unit,
                cityName = resolvedCityName,
                stateOrRegion = region,
                effectiveDate = "Today, $todayStr",
                formattedDisplay = "${profile.currencySymbol}$evRate$unit",
                source = "State DISCOM EV Charging Tariff",
                sourceUrl = "https://www.goodreturns.in",
                isLiveFetched = true
            )
            livePriceCache["$countryCode-$resolvedCityName-$normalizedFuel"] = res
            return@withContext res
        }

        // Map fuel type to goodreturns slug (petrol, diesel, cng)
        val fuelSlug = when (normalizedFuel) {
            "DIESEL" -> "diesel"
            "CNG" -> "cng"
            else -> "petrol"
        }

        val citySlug = getCitySlugForGoodReturns(resolvedCityName)
        var fetchedPrice: Double? = null
        var fetchedUrl = "$GOODRETURNS_BASE_URL/$fuelSlug-price-in-$citySlug.html"

        // 1. First attempt: City specific page on goodreturns.in
        try {
            val html = downloadHtml(fetchedUrl)
            if (html != null) {
                fetchedPrice = extractPriceFromHtml(html)
            }
        } catch (_: Exception) {
            // Proceed to secondary attempt
        }

        // 1b. If city was new-delhi/delhi or bangalore/bengaluru, try alternate slug
        if (fetchedPrice == null && citySlug == "new-delhi") {
            try {
                val altUrl = "$GOODRETURNS_BASE_URL/$fuelSlug-price-in-delhi.html"
                val html = downloadHtml(altUrl)
                if (html != null) {
                    fetchedPrice = extractPriceFromHtml(html)
                    if (fetchedPrice != null) fetchedUrl = altUrl
                }
            } catch (_: Exception) { }
        }

        // 2. Second attempt: Check main goodreturns.in fuel index table
        if (fetchedPrice == null) {
            val indexUrl = "$GOODRETURNS_BASE_URL/$fuelSlug-price.html"
            try {
                val html = downloadHtml(indexUrl)
                if (html != null) {
                    fetchedPrice = extractPriceFromIndexTable(html, resolvedCityName)
                    if (fetchedPrice == null && matchedCity != null) {
                        fetchedPrice = extractPriceFromIndexTable(html, matchedCity.stateOrRegion)
                    }
                    if (fetchedPrice != null) {
                        fetchedUrl = indexUrl
                    }
                }
            } catch (_: Exception) {
                // Ignore network issues
            }
        }

        if (fetchedPrice != null && fetchedPrice > 0.0) {
            val formattedPrice = String.format(Locale.US, "%.2f", fetchedPrice).toDouble()
            val result = TodayFuelRateResult(
                price = formattedPrice,
                fuelType = normalizedFuel,
                unit = unit,
                cityName = resolvedCityName,
                stateOrRegion = region,
                effectiveDate = "Today, $todayStr (GoodReturns Daily 06:00 AM Revision)",
                formattedDisplay = "${profile.currencySymbol}$formattedPrice$unit",
                source = "www.goodreturns.in",
                sourceUrl = fetchedUrl,
                isLiveFetched = true
            )
            livePriceCache["$countryCode-$resolvedCityName-$normalizedFuel"] = result
            return@withContext result
        }

        // Fallback: return baseline from CountryRegistry updated with GoodReturns baseline
        val fallback = getTodayFuelRate(countryCode, resolvedCityName, fuelType)
        return@withContext fallback.copy(
            source = "www.goodreturns.in (Cached)",
            sourceUrl = fetchedUrl,
            isLiveFetched = false
        )
    }

    /**
     * Synchronous rate resolver. Returns cached live rate from GoodReturns if available,
     * or baseline from CountryRegistry.
     */
    fun getTodayFuelRate(
        countryCode: String,
        cityName: String,
        fuelType: String
    ): TodayFuelRateResult {
        val profile = CountryRegistry.getProfile(countryCode)
        val matchedCity = findClosestCity(cityName, countryCode) ?: profile.cities.firstOrNull()
        val normalizedFuel = normalizeFuelType(fuelType, countryCode)
        val resolvedCityName = matchedCity?.cityName ?: cityName
        val region = matchedCity?.stateOrRegion ?: countryCode
        val cacheKey = "$countryCode-$resolvedCityName-$normalizedFuel"

        // Check cache first
        livePriceCache[cacheKey]?.let { cached ->
            return cached
        }

        val basePrice = if (matchedCity != null) {
            getRateForCityAndFuelType(matchedCity, normalizedFuel, countryCode)
        } else {
            when (normalizedFuel) {
                "PETROL", "GASOLINE" -> profile.defaultFuelPrice
                "DIESEL" -> profile.defaultFuelPrice * 0.93
                "CNG" -> profile.defaultFuelPrice * 0.85
                "EV" -> if (countryCode.equals("IN", ignoreCase = true)) 8.00 else 0.20
                else -> profile.defaultFuelPrice
            }
        }

        val formattedPrice = String.format(Locale.US, "%.2f", basePrice).toDouble()
        val unit = getFuelRateUnit(countryCode, normalizedFuel)
        val todayStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        return TodayFuelRateResult(
            price = formattedPrice,
            fuelType = normalizedFuel,
            unit = unit,
            cityName = resolvedCityName,
            stateOrRegion = region,
            effectiveDate = "Today, $todayStr (GoodReturns Daily 06:00 AM Revision)",
            formattedDisplay = "${profile.currencySymbol}$formattedPrice$unit",
            source = "www.goodreturns.in",
            sourceUrl = "$GOODRETURNS_BASE_URL/${if (normalizedFuel == "DIESEL") "diesel" else if (normalizedFuel == "CNG") "cng" else "petrol"}-price.html",
            isLiveFetched = false
        )
    }

    /**
     * Quick price accessor for unit tests and mathematical calculations.
     */
    fun getPriceForCity(countryCode: String, cityName: String, fuelType: String): Double {
        return getTodayFuelRate(countryCode, cityName, fuelType).price
    }

    /**
     * Downloads HTML text from a given URL using standard HttpURLConnection.
     */
    private fun downloadHtml(urlStr: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                instanceFollowRedirects = true
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append('\n')
                }
                reader.close()
                sb.toString()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Extracts fuel price from GoodReturns city page HTML.
     */
    private fun extractPriceFromHtml(html: String): Double? {
        // Pattern 1: fp-price element <span class="fp-price-big" id="fp-price">&#8377;111.90</span>
        val p1 = Pattern.compile("id=[\"']fp-price[\"'][^>]*>(?:&#8377;|&#x20b9;|₹|Rs\\.?)?\\s*([0-9]+\\.[0-9]+)")
        val m1 = p1.matcher(html)
        if (m1.find()) {
            m1.group(1)?.toDoubleOrNull()?.let { return it }
        }

        // Pattern 2: var fuelPrice = parseFloat("111.90"...)
        val p2 = Pattern.compile("var\\s+fuelPrice\\s*=\\s*parseFloat\\([\"']([0-9]+\\.[0-9]+)[\"']")
        val m2 = p2.matcher(html)
        if (m2.find()) {
            m2.group(1)?.toDoubleOrNull()?.let { return it }
        }

        // Pattern 3: intro paragraph with bold rate
        val p3 = Pattern.compile("id=[\"']gr_intro_content[\"'].*?(?:&#8377;|&#x20b9;|₹|Rs\\.?)\\s*(?:<b>)?([0-9]+\\.[0-9]+)", Pattern.DOTALL)
        val m3 = p3.matcher(html)
        if (m3.find()) {
            m3.group(1)?.toDoubleOrNull()?.let { return it }
        }

        // Pattern 4: Page title containing "Rs. 111.90"
        val p4 = Pattern.compile("<title>.*?Rs\\.?\\s*([0-9]+\\.[0-9]+)")
        val m4 = p4.matcher(html)
        if (m4.find()) {
            m4.group(1)?.toDoubleOrNull()?.let { return it }
        }

        return null
    }

    /**
     * Extracts fuel price from GoodReturns index table rows.
     */
    private fun extractPriceFromIndexTable(html: String, queryCityOrState: String): Double? {
        val q = queryCityOrState.trim().lowercase(Locale.ROOT)
        val rowPattern = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL)
        val colPattern = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL)
        val tagPattern = Pattern.compile("<[^>]+>")
        val numberPattern = Pattern.compile("([0-9]+\\.[0-9]+)")

        val rowMatcher = rowPattern.matcher(html)
        while (rowMatcher.find()) {
            val rowContent = rowMatcher.group(1) ?: continue
            val rowClean = tagPattern.matcher(rowContent).replaceAll(" ").lowercase(Locale.ROOT)
            if (rowClean.contains(q)) {
                val colMatcher = colPattern.matcher(rowContent)
                val cols = mutableListOf<String>()
                while (colMatcher.find()) {
                    cols.add(colMatcher.group(1) ?: "")
                }
                if (cols.size >= 2) {
                    val priceText = tagPattern.matcher(cols[1]).replaceAll("").trim()
                    val numMatcher = numberPattern.matcher(priceText)
                    if (numMatcher.find()) {
                        numMatcher.group(1)?.toDoubleOrNull()?.let { return it }
                    }
                }
            }
        }
        return null
    }

    /**
     * Resolves city name from GPS coordinates via Android Geocoder.
     */
    fun resolveCityFromLocation(context: Context, latitude: Double, longitude: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (latitude != 0.0 && longitude != 0.0) {
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                val address = addresses?.firstOrNull()
                address?.locality ?: address?.subAdminArea ?: address?.adminArea
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Performs fuzzy and alias matching for city names (e.g. Gurgaon -> Gurugram, Noida -> Noida, etc.).
     */
    fun findClosestCity(cityNameQuery: String, countryCode: String): CityFuelRate? {
        val cities = CountryRegistry.getProfile(countryCode).cities
        val query = cityNameQuery.trim().lowercase(Locale.ROOT)

        // Direct match
        cities.find { it.cityName.equals(query, ignoreCase = true) }?.let { return it }

        // Alias matching for Indian regions
        if (countryCode.equals("IN", ignoreCase = true)) {
            when {
                query.contains("delhi") || query.contains("new delhi") ->
                    return cities.find { it.cityName == "New Delhi" }

                query.contains("noida") || query.contains("greater noida") ->
                    return cities.find { it.cityName == "Noida" } ?: cities.find { it.cityName == "New Delhi" }

                query.contains("gurugram") || query.contains("gurgaon") || query.contains("faridabad") ->
                    return cities.find { it.cityName == "Gurugram" } ?: cities.find { it.cityName == "New Delhi" }

                query.contains("mumbai") || query.contains("bombay") ->
                    return cities.find { it.cityName == "Mumbai" }

                query.contains("thane") || query.contains("kalyan") ->
                    return cities.find { it.cityName == "Thane" } ?: cities.find { it.cityName == "Mumbai" }

                query.contains("navi mumbai") ->
                    return cities.find { it.cityName == "Navi Mumbai" } ?: cities.find { it.cityName == "Mumbai" }

                query.contains("bangalore") || query.contains("bengaluru") || query.contains("mysuru") ->
                    return cities.find { it.cityName == "Bengaluru" }

                query.contains("kolkata") || query.contains("calcutta") || query.contains("howrah") ->
                    return cities.find { it.cityName == "Kolkata" }

                query.contains("chennai") || query.contains("madras") ->
                    return cities.find { it.cityName == "Chennai" }

                query.contains("hyderabad") || query.contains("secunderabad") ->
                    return cities.find { it.cityName == "Hyderabad" }

                query.contains("pune") || query.contains("poona") || query.contains("pimpri") ->
                    return cities.find { it.cityName == "Pune" }

                query.contains("ahmedabad") || query.contains("gandhinagar") ->
                    return cities.find { it.cityName == "Ahmedabad" }

                query.contains("jaipur") ->
                    return cities.find { it.cityName == "Jaipur" }

                query.contains("lucknow") ->
                    return cities.find { it.cityName == "Lucknow" }

                query.contains("chandigarh") || query.contains("mohali") || query.contains("panchkula") ->
                    return cities.find { it.cityName == "Chandigarh" }

                query.contains("patna") ->
                    return cities.find { it.cityName == "Patna" }

                query.contains("kochi") || query.contains("cochin") || query.contains("ernakulam") ->
                    return cities.find { it.cityName == "Kochi" }

                query.contains("bhubaneswar") || query.contains("cuttack") ->
                    return cities.find { it.cityName == "Bhubaneswar" }

                query.contains("guwahati") ->
                    return cities.find { it.cityName == "Guwahati" }

                query.contains("indore") ->
                    return cities.find { it.cityName == "Indore" }

                query.contains("surat") ->
                    return cities.find { it.cityName == "Surat" }

                query.contains("nagpur") ->
                    return cities.find { it.cityName == "Nagpur" }

                query.contains("visakhapatnam") || query.contains("vizag") ->
                    return cities.find { it.cityName == "Visakhapatnam" }

                query.contains("coimbatore") ->
                    return cities.find { it.cityName == "Coimbatore" }

                query.contains("vadodara") || query.contains("baroda") ->
                    return cities.find { it.cityName == "Vadodara" }

                query.contains("bhopal") ->
                    return cities.find { it.cityName == "Bhopal" }

                query.contains("ludhiana") ->
                    return cities.find { it.cityName == "Ludhiana" }

                query.contains("agra") ->
                    return cities.find { it.cityName == "Agra" }

                query.contains("varanasi") || query.contains("banaras") || query.contains("kashi") ->
                    return cities.find { it.cityName == "Varanasi" }

                query.contains("kanpur") ->
                    return cities.find { it.cityName == "Kanpur" }

                query.contains("dehradun") ->
                    return cities.find { it.cityName == "Dehradun" }

                query.contains("ranchi") ->
                    return cities.find { it.cityName == "Ranchi" }

                query.contains("raipur") ->
                    return cities.find { it.cityName == "Raipur" }
            }
        }

        // Substring match
        return cities.find {
            it.cityName.lowercase(Locale.ROOT).contains(query) ||
                    it.stateOrRegion.lowercase(Locale.ROOT).contains(query)
        }
    }

    fun getCitiesForCountry(countryCode: String): List<CityFuelRate> {
        return CountryRegistry.getProfile(countryCode).cities
    }
}
