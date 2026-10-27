package com.example.data.model

data class CityFuelRate(
    val cityName: String,
    val stateOrRegion: String,
    val petrolPrice: Double,
    val dieselPrice: Double,
    val cngPrice: Double = 0.0,
    val evPricePerKwh: Double = 8.0
)

data class CountryProfile(
    val code: String,
    val name: String,
    val currencyCode: String,
    val currencySymbol: String,
    val unitDistance: String,
    val standardTaxRatePerDistance: Double,
    val defaultFuelPrice: Double,
    val cities: List<CityFuelRate>
)

object CountryRegistry {

    val INDIA = CountryProfile(
        code = "IN",
        name = "India",
        currencyCode = "INR",
        currencySymbol = "₹",
        unitDistance = "km",
        standardTaxRatePerDistance = 10.0,
        defaultFuelPrice = 102.12,
        cities = listOf(
            CityFuelRate("New Delhi", "Delhi NCR", 102.12, 87.67, 75.09, 8.00),
            CityFuelRate("Mumbai", "Maharashtra", 104.21, 92.15, 76.00, 9.50),
            CityFuelRate("Bengaluru", "Karnataka", 102.92, 88.99, 85.00, 8.50),
            CityFuelRate("Kolkata", "West Bengal", 104.95, 91.76, 86.50, 8.75),
            CityFuelRate("Chennai", "Tamil Nadu", 100.80, 92.39, 91.50, 8.20),
            CityFuelRate("Hyderabad", "Telangana", 107.46, 95.70, 96.00, 9.00),
            CityFuelRate("Pune", "Maharashtra", 104.08, 90.61, 88.00, 9.20),
            CityFuelRate("Ahmedabad", "Gujarat", 94.49, 90.17, 78.50, 7.80),
            CityFuelRate("Jaipur", "Rajasthan", 104.72, 90.21, 89.00, 8.50),
            CityFuelRate("Lucknow", "Uttar Pradesh", 94.69, 87.81, 94.00, 8.10),
            CityFuelRate("Chandigarh", "Chandigarh", 94.30, 82.45, 86.00, 7.50),
            CityFuelRate("Noida", "Uttar Pradesh", 94.83, 87.96, 81.20, 8.10),
            CityFuelRate("Gurugram", "Haryana", 95.19, 88.05, 82.50, 8.20),
            CityFuelRate("Thane", "Maharashtra", 104.35, 92.28, 76.00, 9.50),
            CityFuelRate("Navi Mumbai", "Maharashtra", 104.26, 92.20, 76.00, 9.50),
            CityFuelRate("Patna", "Bihar", 105.58, 92.42, 93.00, 8.60),
            CityFuelRate("Kochi", "Kerala", 105.59, 94.53, 88.00, 8.40),
            CityFuelRate("Bhubaneswar", "Odisha", 101.11, 92.69, 90.00, 7.90),
            CityFuelRate("Guwahati", "Assam", 98.08, 89.30, 88.00, 8.10),
            CityFuelRate("Indore", "Madhya Pradesh", 106.48, 91.88, 91.00, 8.30),
            CityFuelRate("Surat", "Gujarat", 94.25, 89.94, 78.50, 7.80),
            CityFuelRate("Nagpur", "Maharashtra", 104.06, 90.62, 89.00, 9.10),
            CityFuelRate("Visakhapatnam", "Andhra Pradesh", 108.35, 96.12, 92.00, 8.60),
            CityFuelRate("Coimbatore", "Tamil Nadu", 101.29, 92.89, 90.50, 8.20),
            CityFuelRate("Vadodara", "Gujarat", 94.18, 89.85, 78.00, 7.80),
            CityFuelRate("Bhopal", "Madhya Pradesh", 106.52, 91.89, 91.00, 8.30),
            CityFuelRate("Ludhiana", "Punjab", 97.45, 87.92, 87.50, 7.90),
            CityFuelRate("Agra", "Uttar Pradesh", 94.46, 87.52, 93.00, 8.10),
            CityFuelRate("Varanasi", "Uttar Pradesh", 95.32, 88.48, 93.50, 8.10),
            CityFuelRate("Kanpur", "Uttar Pradesh", 94.52, 87.60, 93.00, 8.10),
            CityFuelRate("Dehradun", "Uttarakhand", 93.45, 88.32, 89.00, 7.60),
            CityFuelRate("Ranchi", "Jharkhand", 97.86, 92.62, 91.00, 8.00),
            CityFuelRate("Raipur", "Chhattisgarh", 100.45, 93.39, 90.00, 7.90)
        )
    )

    val UNITED_STATES = CountryProfile(
        code = "US",
        name = "United States",
        currencyCode = "USD",
        currencySymbol = "$",
        unitDistance = "mi",
        standardTaxRatePerDistance = 0.67,
        defaultFuelPrice = 3.45,
        cities = listOf(
            CityFuelRate("New York", "NY", 3.55, 4.10, 2.90, 0.24),
            CityFuelRate("Los Angeles", "CA", 4.85, 5.15, 3.20, 0.28),
            CityFuelRate("Chicago", "IL", 3.75, 4.05, 2.85, 0.18),
            CityFuelRate("Houston", "TX", 2.98, 3.40, 2.50, 0.15),
            CityFuelRate("San Francisco", "CA", 4.95, 5.25, 3.30, 0.30)
        )
    )

    val UNITED_KINGDOM = CountryProfile(
        code = "GB",
        name = "United Kingdom",
        currencyCode = "GBP",
        currencySymbol = "£",
        unitDistance = "mi",
        standardTaxRatePerDistance = 0.45,
        defaultFuelPrice = 1.45,
        cities = listOf(
            CityFuelRate("London", "England", 1.46, 1.54, 1.20, 0.32),
            CityFuelRate("Manchester", "England", 1.43, 1.51, 1.18, 0.30),
            CityFuelRate("Birmingham", "England", 1.44, 1.52, 1.19, 0.30),
            CityFuelRate("Edinburgh", "Scotland", 1.45, 1.53, 1.20, 0.29)
        )
    )

    val CANADA = CountryProfile(
        code = "CA",
        name = "Canada",
        currencyCode = "CAD",
        currencySymbol = "CA$",
        unitDistance = "km",
        standardTaxRatePerDistance = 0.70,
        defaultFuelPrice = 1.62,
        cities = listOf(
            CityFuelRate("Toronto", "Ontario", 1.60, 1.68, 1.30, 0.16),
            CityFuelRate("Vancouver", "British Columbia", 1.85, 1.89, 1.40, 0.15),
            CityFuelRate("Montreal", "Quebec", 1.68, 1.74, 1.35, 0.12)
        )
    )

    val AUSTRALIA = CountryProfile(
        code = "AU",
        name = "Australia",
        currencyCode = "AUD",
        currencySymbol = "A$",
        unitDistance = "km",
        standardTaxRatePerDistance = 0.85,
        defaultFuelPrice = 1.95,
        cities = listOf(
            CityFuelRate("Sydney", "NSW", 1.98, 2.04, 1.55, 0.35),
            CityFuelRate("Melbourne", "VIC", 1.94, 2.01, 1.52, 0.33),
            CityFuelRate("Brisbane", "QLD", 1.96, 2.02, 1.54, 0.32)
        )
    )

    val ALL_COUNTRIES = listOf(
        INDIA,
        UNITED_STATES,
        UNITED_KINGDOM,
        CANADA,
        AUSTRALIA
    )

    fun getProfile(countryCode: String): CountryProfile {
        return ALL_COUNTRIES.find { it.code.equals(countryCode, ignoreCase = true) } ?: INDIA
    }
}
