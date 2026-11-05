package com.example.ui.viewmodel

import com.example.data.model.TripEntity

data class DayTripsGroup(
    val dateLabel: String,
    val timestamp: Long,
    val trips: List<TripEntity>,
    val totalDistanceKm: Double,
    val totalPersonalCost: Double,
    val totalReimbursement: Double,
    val categoryBreakdown: List<CategoryStat> = emptyList()
)
