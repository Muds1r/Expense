package com.expense.tracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.ui.MainViewModel
import com.expense.tracker.ui.dayLabel

@Composable
fun TransactionsScreen(viewModel: MainViewModel, onTransactionClick: (String) -> Unit) {
    val allTransactions by viewModel.allTransactions.collectAsState()
    val grouped = allTransactions.groupBy { dayLabel(it.timestamp) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { RangeSelector(viewModel) }

        if (allTransactions.isEmpty()) {
            item { EmptyState("No transactions in this period.") }
        }

        grouped.forEach { (day, txns) ->
            item(key = "header-$day") {
                Text(
                    day,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(txns, key = { it.id }) { txn ->
                TransactionRow(txn, onClick = { onTransactionClick(txn.id) })
            }
        }
    }
}
