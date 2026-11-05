package com.example.ui.viewmodel

sealed interface MonthFilter {
    data class Month(val year: Int, val month: Int) : MonthFilter
    data object AllTime : MonthFilter
}

data class MonthlyMetrics(
    val totalDistanceKm: Double = 0.0,
    val totalBusinessDeduction: Double = 0.0,
    val totalPersonalCost: Double = 0.0,
    val totalFuelCost: Double = 0.0,
    val totalFuelConsumed: Double = 0.0,
    val avgEfficiency: Double? = null,
    val tripCount: Int = 0,
    val tagBreakdown: List<CategoryStat> = emptyList(),
    val periodLabel: String = "This Month",
    val hasNextPeriod: Boolean = false,
    val hasPreviousPeriod: Boolean = true,
    val currentFilter: MonthFilter = MonthFilter.AllTime
)

