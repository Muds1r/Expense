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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.expense.tracker.ui.formatAmount
import com.expense.tracker.ui.theme.ExpenseRed
import com.expense.tracker.ui.theme.IncomeGreen

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val summaries by viewModel.bankSummaries.collectAsState()
    val totalIn = summaries.sumOf { it.totalIn }
    val totalOut = summaries.sumOf { it.totalOut }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { RangeSelector(viewModel) }
        item { NetBalanceCard(totalIn, totalOut) }
        item { SectionHeader("By bank") }
        if (summaries.isEmpty()) {
            item { EmptyState("No transactions yet. Tap the sync icon to fetch your Gmail alerts.") }
        }
        items(summaries, key = { it.bank }) { summary -> BankCard(summary) }
    }
}

@Composable
private fun NetBalanceCard(totalIn: Double, totalOut: Double) {
    val net = totalIn - totalOut
    SoftHeroCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Net for this period",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                (if (net >= 0) "+" else "") + formatAmount(net),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (net >= 0) IncomeGreen else ExpenseRed
            )
            Spacer(Modifier.height(16.dp))
            Row {
                StatItem("Received", totalIn, IncomeGreen, Modifier.weight(1f))
                StatItem("Spent", totalOut, ExpenseRed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BankCard(summary: BankSummary) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialAvatar(summary.bank, MaterialTheme.colorScheme.primary, size = 38)
                Spacer(Modifier.width(12.dp))
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
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            Row {
                AmountColumn("In", summary.totalIn, IncomeGreen, Modifier.weight(1f))
                AmountColumn("Out", summary.totalOut, ExpenseRed, Modifier.weight(1f))
                val net = summary.totalIn - summary.totalOut
                AmountColumn("Net", net, if (net >= 0) IncomeGreen else ExpenseRed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AmountColumn(
    label: String,
    amount: Double,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatAmount(amount), style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
    }
}
