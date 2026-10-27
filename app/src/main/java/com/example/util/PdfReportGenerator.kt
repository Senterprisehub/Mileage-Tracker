package com.example.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.AppSettingsEntity
import com.example.data.model.TripCategory
import com.example.data.model.TripEntity
import com.example.data.model.VehicleEntity
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 36f
    private const val MARGIN_BOTTOM = 800f
    private const val CONTENT_WIDTH = 523f // 595 - 72

    // Column widths (Total = 523f)
    private const val COL_DATE = 72f
    private const val COL_ROUTE = 155f
    private const val COL_CAT = 50f
    private const val COL_VEH = 56f
    private const val COL_DIST = 50f
    private const val COL_EXP = 65f
    private const val COL_NOTES = 75f

    fun generatePdf(
        trips: List<TripEntity>,
        vehicles: List<VehicleEntity>,
        settings: AppSettingsEntity,
        periodLabel: String,
        outputStream: OutputStream
    ) {
        val document = PdfDocument()
        val vehicleMap = vehicles.associateBy { it.id }

        // Formatting utilities
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val generatedAtFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        // Calculate summary metrics
        val totalTrips = trips.size
        val totalDistanceKm = trips.sumOf { it.distanceKm }
        val businessTrips = trips.filter { it.getParsedCategory() == TripCategory.BUSINESS }
        val personalTrips = trips.filter { it.getParsedCategory() != TripCategory.BUSINESS }

        val businessDistanceKm = businessTrips.sumOf { it.distanceKm }
        val personalDistanceKm = personalTrips.sumOf { it.distanceKm }

        val totalFuelCost = trips.sumOf { it.fuelCost }
        val totalTaxDeduction = trips.sumOf { it.reimbursementAmount }
        val totalTolls = trips.sumOf { it.tolls }
        val totalParking = trips.sumOf { it.parking }
        val totalPersonalCost = trips.sumOf { it.totalPersonalCost }

        // Setup paint objects
        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42) // NavyDark
            typeface = Typeface.DEFAULT
        }
        val paintBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            typeface = Typeface.DEFAULT_BOLD
        }
        val paintSecondary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139) // SlateLight
            typeface = Typeface.DEFAULT
        }
        val paintAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(37, 99, 235) // Cobalt Blue
            typeface = Typeface.DEFAULT_BOLD
        }
        val paintBg = Paint(Paint.ANTI_ALIAS_FLAG)
        val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var currentY = 40f

        // Draw Document Header (Page 1)
        // Top Brand Accent Line
        paintBg.color = Color.rgb(37, 99, 235)
        canvas.drawRect(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY + 4f, paintBg)
        currentY += 18f

        // Document Title
        paintBold.textSize = 17f
        paintBold.color = Color.rgb(15, 23, 42)
        canvas.drawText("MILEAGE & EXPENSE LOG REPORT", MARGIN_X, currentY, paintBold)

        // Generation Metadata (Right aligned)
        paintSecondary.textSize = 8.5f
        val genText = "Generated: ${generatedAtFormat.format(Date())}"
        val genTextWidth = paintSecondary.measureText(genText)
        canvas.drawText(genText, MARGIN_X + CONTENT_WIDTH - genTextWidth, currentY, paintSecondary)
        currentY += 14f

        // Period Subtitle
        paintAccent.textSize = 10f
        canvas.drawText("PERIOD: ${periodLabel.uppercase()}", MARGIN_X, currentY, paintAccent)

        val countryText = "${settings.countryName} • Tax Rate: ${settings.currencySymbol}${settings.standardTaxRatePerKm}/${settings.unitDistance}"
        val countryTextWidth = paintSecondary.measureText(countryText)
        canvas.drawText(countryText, MARGIN_X + CONTENT_WIDTH - countryTextWidth, currentY, paintSecondary)
        currentY += 16f

        // Divider
        canvas.drawLine(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY, paintLine)
        currentY += 12f

        // Summary Metric Cards Box
        val summaryBoxHeight = 58f
        paintBg.color = Color.rgb(248, 250, 252)
        val summaryRect = RectF(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY + summaryBoxHeight)
        canvas.drawRoundRect(summaryRect, 6f, 6f, paintBg)
        paintBg.style = Paint.Style.STROKE
        paintBg.color = Color.rgb(226, 232, 240)
        paintBg.strokeWidth = 1f
        canvas.drawRoundRect(summaryRect, 6f, 6f, paintBg)
        paintBg.style = Paint.Style.FILL

        // Summary Columns (4 columns)
        val colWidth = CONTENT_WIDTH / 4f
        val metricY = currentY + 16f
        val valY = currentY + 34f
        val subY = currentY + 47f

        // Col 1: Total Distance & Trips
        paintSecondary.textSize = 7.5f
        canvas.drawText("TOTAL DISTANCE", MARGIN_X + 10f, metricY, paintSecondary)
        paintBold.textSize = 12f
        canvas.drawText(String.format(Locale.US, "%.1f %s", totalDistanceKm, settings.unitDistance), MARGIN_X + 10f, valY, paintBold)
        paintSecondary.textSize = 7.5f
        canvas.drawText("$totalTrips total trips logged", MARGIN_X + 10f, subY, paintSecondary)

        // Col 2: Business & Tax Deduction
        val col2X = MARGIN_X + colWidth + 5f
        paintSecondary.textSize = 7.5f
        canvas.drawText("TAX WRITE-OFF (DED)", col2X, metricY, paintSecondary)
        paintBold.textSize = 12f
        paintBold.color = Color.rgb(5, 150, 105) // Emerald
        canvas.drawText("${settings.currencySymbol}${String.format(Locale.US, "%.2f", totalTaxDeduction)}", col2X, valY, paintBold)
        paintBold.color = Color.rgb(15, 23, 42)
        paintSecondary.textSize = 7.5f
        canvas.drawText(String.format(Locale.US, "%.1f %s business", businessDistanceKm, settings.unitDistance), col2X, subY, paintSecondary)

        // Col 3: Fuel Cost & Personal Dist
        val col3X = MARGIN_X + (colWidth * 2f) + 5f
        paintSecondary.textSize = 7.5f
        canvas.drawText("FUEL CONSUMPTION", col3X, metricY, paintSecondary)
        paintBold.textSize = 12f
        paintBold.color = Color.rgb(217, 119, 6) // Amber
        canvas.drawText("${settings.currencySymbol}${String.format(Locale.US, "%.2f", totalFuelCost)}", col3X, valY, paintBold)
        paintBold.color = Color.rgb(15, 23, 42)
        paintSecondary.textSize = 7.5f
        canvas.drawText(String.format(Locale.US, "%.1f %s personal", personalDistanceKm, settings.unitDistance), col3X, subY, paintSecondary)

        // Col 4: Tolls, Parking & Total Expenses
        val col4X = MARGIN_X + (colWidth * 3f) + 5f
        paintSecondary.textSize = 7.5f
        canvas.drawText("TOLLS & PARKING", col4X, metricY, paintSecondary)
        paintBold.textSize = 12f
        canvas.drawText("${settings.currencySymbol}${String.format(Locale.US, "%.2f", totalTolls + totalParking)}", col4X, valY, paintBold)
        paintSecondary.textSize = 7.5f
        canvas.drawText("Total Cost: ${settings.currencySymbol}${String.format(Locale.US, "%.2f", totalPersonalCost)}", col4X, subY, paintSecondary)

        currentY += summaryBoxHeight + 16f

        // Table Header Function
        fun drawTableHeader(c: Canvas, y: Float): Float {
            val headerHeight = 20f
            paintBg.color = Color.rgb(15, 23, 42) // NavyDark
            c.drawRect(MARGIN_X, y, MARGIN_X + CONTENT_WIDTH, y + headerHeight, paintBg)

            paintBold.textSize = 8f
            paintBold.color = Color.WHITE
            val textY = y + 13.5f

            var x = MARGIN_X + 6f
            c.drawText("DATE & TIME", x, textY, paintBold)
            x += COL_DATE
            c.drawText("ROUTE (FROM / TO)", x, textY, paintBold)
            x += COL_ROUTE
            c.drawText("CATEGORY", x, textY, paintBold)
            x += COL_CAT
            c.drawText("VEHICLE", x, textY, paintBold)
            x += COL_VEH
            c.drawText("DISTANCE", x, textY, paintBold)
            x += COL_DIST
            c.drawText("COST / DED", x, textY, paintBold)
            x += COL_EXP
            c.drawText("PURPOSE / NOTES", x, textY, paintBold)

            return y + headerHeight
        }

        currentY = drawTableHeader(canvas, currentY)

        // Helper to draw footer on each page
        fun drawPageFooter(c: Canvas, pageNum: Int) {
            val footerY = PAGE_HEIGHT - 24f
            c.drawLine(MARGIN_X, footerY - 8f, MARGIN_X + CONTENT_WIDTH, footerY - 8f, paintLine)
            paintSecondary.textSize = 7.5f
            c.drawText("Mileage Tracker • 100% Private Offline Ledger", MARGIN_X, footerY, paintSecondary)
            val pNumText = "Page $pageNum"
            val pNumWidth = paintSecondary.measureText(pNumText)
            c.drawText(pNumText, MARGIN_X + CONTENT_WIDTH - pNumWidth, footerY, paintSecondary)
        }

        if (trips.isEmpty()) {
            currentY += 24f
            paintSecondary.textSize = 10f
            canvas.drawText("No completed trips found for the selected period.", MARGIN_X + 16f, currentY, paintSecondary)
            drawPageFooter(canvas, pageNumber)
            document.finishPage(page)
        } else {
            // Iterate and draw trip rows
            val rowHeight = 30f
            trips.forEachIndexed { index, trip ->
                // Check if page overflows
                if (currentY + rowHeight > MARGIN_BOTTOM) {
                    drawPageFooter(canvas, pageNumber)
                    document.finishPage(page)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = 40f
                    // Draw continuation title and table header
                    paintSecondary.textSize = 8.5f
                    canvas.drawText("Mileage & Expense Log Report • $periodLabel (Cont.)", MARGIN_X, currentY - 6f, paintSecondary)
                    currentY = drawTableHeader(canvas, currentY)
                }

                // Row background (alternating subtle zebra)
                if (index % 2 == 1) {
                    paintBg.color = Color.rgb(248, 250, 252)
                    canvas.drawRect(MARGIN_X, currentY, MARGIN_X + CONTENT_WIDTH, currentY + rowHeight, paintBg)
                }

                // Row border bottom
                canvas.drawLine(MARGIN_X, currentY + rowHeight, MARGIN_X + CONTENT_WIDTH, currentY + rowHeight, paintLine)

                val line1Y = currentY + 12f
                val line2Y = currentY + 23f
                var x = MARGIN_X + 6f

                // 1. Date & Time
                paintBold.textSize = 7.5f
                paintBold.color = Color.rgb(15, 23, 42)
                canvas.drawText(dateFormat.format(Date(trip.startTime)), x, line1Y, paintBold)
                paintSecondary.textSize = 7f
                val timeStr = "${timeFormat.format(Date(trip.startTime))}${trip.endTime?.let { " - " + timeFormat.format(Date(it)) } ?: ""}"
                canvas.drawText(timeStr.take(16), x, line2Y, paintSecondary)
                x += COL_DATE

                // 2. Route (From & To)
                paintText.textSize = 7.5f
                val fromAddr = "From: " + trip.startAddress.replace("\n", " ").take(30)
                canvas.drawText(fromAddr, x, line1Y, paintText)
                val toAddr = "To: " + (trip.endAddress ?: "In Progress").replace("\n", " ").take(30)
                paintSecondary.textSize = 7f
                canvas.drawText(toAddr, x, line2Y, paintSecondary)
                x += COL_ROUTE

                // 3. Category
                val category = trip.getParsedCategory()
                paintBold.textSize = 7.5f
                if (category == TripCategory.BUSINESS) {
                    paintBold.color = Color.rgb(5, 150, 105) // Emerald
                } else {
                    paintBold.color = Color.rgb(217, 119, 6) // Amber
                }
                canvas.drawText(category.displayName, x, line1Y, paintBold)
                paintSecondary.textSize = 6.5f
                canvas.drawText(if (trip.isManual) "Manual" else "GPS Auto", x, line2Y, paintSecondary)
                x += COL_CAT

                // 4. Vehicle
                val vehicleName = vehicleMap[trip.vehicleId]?.name ?: "Vehicle"
                paintText.textSize = 7.5f
                canvas.drawText(vehicleName.take(12), x, line1Y, paintText)
                if (trip.startOdometer != null || trip.endOdometer != null) {
                    val odoStr = "${trip.startOdometer?.toInt() ?: "-"}-${trip.endOdometer?.toInt() ?: "-"} Odo"
                    paintSecondary.textSize = 6.5f
                    canvas.drawText(odoStr.take(12), x, line2Y, paintSecondary)
                }
                x += COL_VEH

                // 5. Distance
                paintBold.textSize = 8f
                paintBold.color = Color.rgb(37, 99, 235) // Blue Cobalt
                canvas.drawText(String.format(Locale.US, "%.1f %s", trip.distanceKm, settings.unitDistance), x, line1Y, paintBold)
                x += COL_DIST

                // 6. Cost / Ded
                paintBold.textSize = 7.5f
                if (category == TripCategory.BUSINESS && trip.reimbursementAmount > 0) {
                    paintBold.color = Color.rgb(5, 150, 105)
                    canvas.drawText("Ded: ${settings.currencySymbol}${String.format(Locale.US, "%.1f", trip.reimbursementAmount)}", x, line1Y, paintBold)
                } else {
                    paintBold.color = Color.rgb(15, 23, 42)
                    canvas.drawText("Fuel: ${settings.currencySymbol}${String.format(Locale.US, "%.1f", trip.fuelCost)}", x, line1Y, paintBold)
                }
                if (trip.tolls > 0 || trip.parking > 0) {
                    val extraCost = trip.tolls + trip.parking
                    paintSecondary.textSize = 6.5f
                    canvas.drawText("+${settings.currencySymbol}${String.format(Locale.US, "%.0f", extraCost)} Toll/Park", x, line2Y, paintSecondary)
                }
                x += COL_EXP

                // 7. Purpose / Notes
                paintSecondary.textSize = 7f
                val noteText = trip.notes.ifBlank { "Regular Trip" }.replace("\n", " ").take(18)
                canvas.drawText(noteText, x, line1Y, paintSecondary)

                currentY += rowHeight
            }

            // Finish the final page
            drawPageFooter(canvas, pageNumber)
            document.finishPage(page)
        }

        document.writeTo(outputStream)
        document.close()
    }
}
