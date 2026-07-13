package com.expense.tracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.BankSummary
import com.expense.tracker.ui.MainViewModel
import com.expense.tracker.ui.RangePreset
import com.expense.tracker.ui.formatAmount
import com.expense.tracker.ui.theme.ExpenseRed
import com.expense.tracker.ui.theme.IncomeGreen

@Composable
fun RangeSelector(viewModel: MainViewModel) {
    val range by viewModel.range.collectAsState()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RangePreset.entries.forEach { preset ->
            FilterChip(
                selected = range == preset,
                onClick = { viewModel.setRange(preset) },
                label = { Text(preset.label) }
            )
        }
    }
}

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val summaries by viewModel.bankSummaries.collectAsState()
    val totalIn = summaries.sumOf { it.totalIn }
    val totalOut = summaries.sumOf { it.totalOut }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { RangeSelector(viewModel) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TotalCard("Received", totalIn, IncomeGreen, Modifier.weight(1f))
                TotalCard("Spent", totalOut, ExpenseRed, Modifier.weight(1f))
            }
        }

        item {
            Text(
                "By bank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (summaries.isEmpty()) {
            item {
                Text(
                    "No transactions yet. Pull down or tap sync to fetch your Gmail alerts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(summaries) { summary -> BankCard(summary) }
    }
}

@Composable
private fun TotalCard(label: String, amount: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                formatAmount(amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun BankCard(summary: BankSummary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    summary.bank,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${summary.txnCount} txns",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("In", style = MaterialTheme.typography.labelSmall)
                    Text(formatAmount(summary.totalIn), color = IncomeGreen, fontWeight = FontWeight.Medium)
                }
                Column(Modifier.weight(1f)) {
                    Text("Out", style = MaterialTheme.typography.labelSmall)
                    Text(formatAmount(summary.totalOut), color = ExpenseRed, fontWeight = FontWeight.Medium)
                }
                Column(Modifier.weight(1f)) {
                    Text("Net", style = MaterialTheme.typography.labelSmall)
                    val net = summary.totalIn - summary.totalOut
                    Text(
                        formatAmount(net),
                        color = if (net >= 0) IncomeGreen else ExpenseRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
