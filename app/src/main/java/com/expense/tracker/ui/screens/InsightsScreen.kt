package com.expense.tracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.CounterpartySummary
import com.expense.tracker.ui.MainViewModel
import com.expense.tracker.ui.formatAmount
import com.expense.tracker.ui.theme.ExpenseRed
import com.expense.tracker.ui.theme.IncomeGreen

@Composable
fun InsightsScreen(viewModel: MainViewModel) {
    val topReceived by viewModel.topReceivedFrom.collectAsState()
    val topPaid by viewModel.topPaidTo.collectAsState()
    val mostFrequent by viewModel.mostFrequentPaidTo.collectAsState()
    val topTxns by viewModel.topTransactions.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { RangeSelector(viewModel) }

        item { SectionHeader("Top senders (money in)") }
        if (topReceived.isEmpty()) item { EmptyHint() }
        items(topReceived) { CounterpartyRow(it, IncomeGreen, showCount = false) }

        item { SectionHeader("Top spends (money out)") }
        if (topPaid.isEmpty()) item { EmptyHint() }
        items(topPaid) { CounterpartyRow(it, ExpenseRed, showCount = false) }

        item { SectionHeader("Most frequent payments") }
        if (mostFrequent.isEmpty()) item { EmptyHint() }
        items(mostFrequent) { CounterpartyRow(it, ExpenseRed, showCount = true) }

        item { SectionHeader("Top 10 transactions") }
        if (topTxns.isEmpty()) item { EmptyHint() }
        items(topTxns, key = { "top-" + it.id }) { TransactionRow(it) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun EmptyHint() {
    Text(
        "Nothing here for this period.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CounterpartyRow(
    summary: CounterpartySummary,
    amountColor: androidx.compose.ui.graphics.Color,
    showCount: Boolean
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    summary.counterparty,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${summary.txnCount} transaction${if (summary.txnCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (showCount) "${summary.txnCount}×" else formatAmount(summary.total),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
        }
    }
}
