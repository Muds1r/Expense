package com.expense.tracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.TransactionEntity
import com.expense.tracker.data.db.TxnType
import com.expense.tracker.ui.MainViewModel
import com.expense.tracker.ui.formatAmount
import com.expense.tracker.ui.formatDateTime
import com.expense.tracker.ui.theme.ExpenseRed
import com.expense.tracker.ui.theme.IncomeGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(viewModel: MainViewModel, txnId: String) {
    val seed = remember(txnId) { viewModel.cachedTransaction(txnId) }
    val observed by remember(txnId) { viewModel.observeTransaction(txnId) }
        .collectAsState(initial = seed)
    val txn = observed ?: seed
    val categories by viewModel.categories.collectAsState()

    when {
        txn != null -> {
            val categoryName = categories.find { it.id == txn.categoryId }?.name ?: "Uncategorized"
            TransactionDetailContent(
                txn = txn,
                categoryName = categoryName,
                categories = categories.map { it.id to it.name },
                onCategorySelected = { viewModel.setCategory(txnId, it) }
            )
        }
        else -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(36.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailContent(
    txn: TransactionEntity,
    categoryName: String,
    categories: List<Pair<Long, String>>,
    onCategorySelected: (Long?) -> Unit
) {
    val isCredit = txn.type == TxnType.CREDIT
    val color = if (isCredit) IncomeGreen else ExpenseRed
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (isCredit) "Money in" else "Money out",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    (if (isCredit) "+" else "-") + formatAmount(txn.amount),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }

        DetailCard(
            if (isCredit) "Sender / from" else "Receiver / paid to",
            txn.counterparty ?: "—"
        )
        DetailCard("Bank", txn.bank)
        DetailCard("Account", txn.accountLast4?.let { "••$it" } ?: "—")
        DetailCard("Date & time", formatDateTime(txn.timestamp))
        DetailCard("Email subject", txn.subject)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Budget category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = menuOpen,
                    onExpandedChange = { menuOpen = it }
                ) {
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        label = { Text("Category") }
                    )
                    ExposedDropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Uncategorized") },
                            onClick = {
                                onCategorySelected(null)
                                menuOpen = false
                            }
                        )
                        categories.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    onCategorySelected(id)
                                    menuOpen = false
                                }
                            )
                        }
                    }
                }
            }
        }

        DetailCard("Message ID", txn.id)
    }
}

@Composable
private fun DetailCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
