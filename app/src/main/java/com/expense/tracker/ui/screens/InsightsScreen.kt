package com.expense.tracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { RangeSelector(viewModel) }

        item { SectionHeader("Top senders (money in)") }
        if (topReceived.isEmpty()) item { EmptyState("Nothing here for this period.") }
        leaderboard(topReceived, IncomeGreen, byCount = false, keyPrefix = "in")

        item { SectionHeader("Top spends (money out)") }
        if (topPaid.isEmpty()) item { EmptyState("Nothing here for this period.") }
        leaderboard(topPaid, ExpenseRed, byCount = false, keyPrefix = "out")

        item { SectionHeader("Most frequent payments") }
        if (mostFrequent.isEmpty()) item { EmptyState("Nothing here for this period.") }
        leaderboard(mostFrequent, ExpenseRed, byCount = true, keyPrefix = "freq")

        item { SectionHeader("Top 10 transactions") }
        if (topTxns.isEmpty()) item { EmptyState("Nothing here for this period.") }
        itemsIndexed(topTxns, key = { _, txn -> "top-" + txn.id }) { _, txn ->
            TransactionRow(txn)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.leaderboard(
    entries: List<CounterpartySummary>,
    color: androidx.compose.ui.graphics.Color,
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
        RankedRow(
            rank = index + 1,
            name = entry.counterparty,
            subtitle = "${entry.txnCount} transaction${if (entry.txnCount == 1) "" else "s"}" +
                if (byCount) " · ${formatAmount(entry.total)} total" else "",
            valueText = if (byCount) "${entry.txnCount}×" else formatAmount(entry.total),
            fraction = if (byCount) {
                (entry.txnCount.toDouble() / max).toFloat()
            } else {
                (entry.total / max).toFloat()
            },
            color = color
        )
    }
}
