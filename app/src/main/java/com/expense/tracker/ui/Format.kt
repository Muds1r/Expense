package com.expense.tracker.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val pkrFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "PK"))

fun formatAmount(amount: Double): String = pkrFormat.format(amount)

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(timestamp))

fun formatTime(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(timestamp))

/** "Today", "Yesterday", or "14 Jul 2026" — used as list section headers. */
fun dayLabel(timestamp: Long): String {
    val dayFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val day = dayFormat.format(Date(timestamp))
    val now = System.currentTimeMillis()
    return when (day) {
        dayFormat.format(Date(now)) -> "Today"
        dayFormat.format(Date(now - 86_400_000L)) -> "Yesterday"
        else -> day
    }
}
