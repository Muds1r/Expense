package com.expense.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.CategorySummary
import com.expense.tracker.data.db.CounterpartySummary
import com.expense.tracker.ui.MainViewModel
import com.expense.tracker.ui.formatAmount
import com.expense.tracker.ui.theme.ExpenseRed
import com.expense.tracker.ui.theme.IncomeGreen

private val chartColors = listOf(
    Color(0xFF2563EB),
    Color(0xFF16A34A),
    Color(0xFFDC2626),
    Color(0xFFF59E0B),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFF0EA5E9),
    Color(0xFF64748B),
    Color(0xFFF97316),
    Color(0xFF14B8A6)
)

@Composable
fun InsightsScreen(viewModel: MainViewModel, onTransactionClick: (String) -> Unit) {
    val topReceived by viewModel.topReceivedFrom.collectAsState()
    val topPaid by viewModel.topPaidTo.collectAsState()
    val mostFrequent by viewModel.mostFrequentPaidTo.collectAsState()
    val topTxns by viewModel.topTransactions.collectAsState()
    val totalIncome by viewModel.totalIncome.collectAsState()
    val totalExpense by viewModel.totalExpense.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()

    val expanded = remember { mutableStateMapOf("in" to true) }
    fun isOpen(key: String) = expanded[key] ?: false

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { RangeSelector(viewModel) }

        // ─── Summary hero ──────────────────────────────────────────────
        item {
            SoftHeroCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Analytics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        StatItem("Income", totalIncome, IncomeGreen, Modifier.weight(1f))
                        StatItem("Expense", totalExpense, ExpenseRed, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    val net = totalIncome - totalExpense
                    Text(
                        "Net  " + (if (net >= 0) "+" else "") + formatAmount(net),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (net >= 0) IncomeGreen else ExpenseRed
                    )
                }
            }
        }

        // ─── Category distribution ─────────────────────────────────────
        if (categoryBreakdown.isNotEmpty()) {
            item {
                CollapsibleHeader("Spending by category", isOpen("cats")) {
                    expanded["cats"] = !isOpen("cats")
                }
            }
            if (isOpen("cats")) {
                item {
                    CategoryBreakdownChart(categoryBreakdown.filter { it.totalOut > 0 })
                }
            }
        }

        // ─── Top senders ───────────────────────────────────────────────
        item {
            CollapsibleHeader("Top 5 senders (money in)", isOpen("in")) {
                expanded["in"] = !isOpen("in")
            }
        }
        if (isOpen("in")) {
            if (topReceived.isEmpty()) item { EmptyState("Nothing here for this period.") }
            leaderboard(topReceived, IncomeGreen, byCount = false, keyPrefix = "in")
        }

        // ─── Top spends ────────────────────────────────────────────────
        item {
            CollapsibleHeader("Top 5 spends (money out)", isOpen("out")) {
                expanded["out"] = !isOpen("out")
            }
        }
        if (isOpen("out")) {
            if (topPaid.isEmpty()) item { EmptyState("Nothing here for this period.") }
            leaderboard(topPaid, ExpenseRed, byCount = false, keyPrefix = "out")
        }

        // ─── Most frequent ─────────────────────────────────────────────
        item {
            CollapsibleHeader("Most frequent payments", isOpen("freq")) {
                expanded["freq"] = !isOpen("freq")
            }
        }
        if (isOpen("freq")) {
            if (mostFrequent.isEmpty()) item { EmptyState("Nothing here for this period.") }
            leaderboard(mostFrequent, ExpenseRed, byCount = true, keyPrefix = "freq")
        }

        // ─── Top transactions ──────────────────────────────────────────
        item {
            CollapsibleHeader("Top 10 transactions", isOpen("top")) {
                expanded["top"] = !isOpen("top")
            }
        }
        if (isOpen("top")) {
            if (topTxns.isEmpty()) item { EmptyState("Nothing here for this period.") }
            itemsIndexed(topTxns, key = { _, txn -> "top-" + txn.id }) { _, txn ->
                TransactionRow(txn, onClick = { onTransactionClick(txn.id) })
            }
        }
    }
}

@Composable
private fun CategoryBreakdownChart(categories: List<CategorySummary>) {
    val totalSpent = categories.sumOf { it.totalOut }.coerceAtLeast(1.0)

    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            categories.forEachIndexed { index, cat ->
                val fraction = (cat.totalOut / totalSpent).toFloat()
                val color = chartColors[index % chartColors.size]

                if (index > 0) {
                    Spacer(Modifier.height(10.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        cat.categoryName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatAmount(cat.totalOut),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ExpenseRed
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

private fun LazyListScope.leaderboard(
    entries: List<CounterpartySummary>,
    color: Color,
    byCount: Boolean,
    keyPrefix: String
) {
    val max = if (byCount) {
        entries.maxOfOrNull { it.txnCount.toDouble() } ?: 1.0
    } else {
        entries.maxOfOrNull { it.total } ?: 1.0
    }
    itemsIndexed(
        entries,
        key = { _, e -> "$keyPrefix-${e.counterparty}" }
    ) { index, entry ->
        val banks = entry.banks.replace(",", ", ")
        RankedRow(
            rank = index + 1,
            name = entry.counterparty,
            subtitle = "${entry.txnCount} transaction${if (entry.txnCount == 1) "" else "s"}" +
                " \u00b7 $banks" +
                if (byCount) " \u00b7 ${formatAmount(entry.total)} total" else "",
            valueText = if (byCount) "${entry.txnCount}\u00d7" else formatAmount(entry.total),
            fraction = if (byCount) {
                (entry.txnCount.toDouble() / max).toFloat()
            } else {
                (entry.total / max).toFloat()
            },
            color = color
        )
    }
}
