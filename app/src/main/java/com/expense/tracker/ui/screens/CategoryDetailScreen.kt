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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.TxnType
import com.expense.tracker.ui.MainViewModel
import com.expense.tracker.ui.formatAmount
import com.expense.tracker.ui.theme.ExpenseRed
import com.expense.tracker.ui.theme.IncomeGreen
import kotlinx.coroutines.flow.flowOf

@Composable
fun CategoryDetailScreen(
    viewModel: MainViewModel,
    categoryId: Long?,
    onTransactionClick: (String) -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val category by remember(categoryId) {
        if (categoryId != null) viewModel.observeCategory(categoryId) else flowOf(null)
    }.collectAsState(initial = null)

    val txns by remember(categoryId) { viewModel.transactionsForCategory(categoryId) }
        .collectAsState(initial = emptyList())

    val name = when {
        categoryId == null -> "Uncategorized"
        category != null -> category!!.name
        else -> categories.find { it.id == categoryId }?.name ?: "Category"
    }
    val budget = category?.budgetAmount
    val totalIn = txns.filter { it.type == TxnType.CREDIT }.sumOf { it.amount }
    val totalOut = txns.filter { it.type == TxnType.DEBIT }.sumOf { it.amount }
    val net = totalIn - totalOut

    var showBudgetDialog by remember { mutableStateOf(false) }
    var budgetText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { RangeSelector(viewModel) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (categoryId != null) {
                            IconButton(onClick = {
                                budgetText = budget?.let {
                                    if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
                                } ?: ""
                                showBudgetDialog = true
                            }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Set budget",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        StatItem("Received", totalIn, IncomeGreen, Modifier.weight(1f))
                        StatItem("Spent", totalOut, ExpenseRed, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Net  " + (if (net >= 0) "+" else "") + formatAmount(net),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (net >= 0) IncomeGreen else ExpenseRed
                    )
                    if (categoryId != null) {
                        Spacer(Modifier.height(10.dp))
                        if (budget != null && budget > 0) {
                            BudgetProgressBar(spent = totalOut, budget = budget)
                        } else {
                            Text(
                                "No budget set — tap the edit icon to add one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Transactions (${txns.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (txns.isEmpty()) {
            item { EmptyState("No transactions in this category for this period.") }
        }

        items(txns, key = { it.id }) { txn ->
            TransactionRow(txn, onClick = { onTransactionClick(txn.id) })
        }
    }

    if (showBudgetDialog && categoryId != null) {
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("Budget for $name") },
            text = {
                Column {
                    Text(
                        "Spending limit in PKR for the selected time range. Leave empty and tap Clear to remove.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { budgetText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Budget amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = budgetText.toDoubleOrNull()
                    viewModel.setCategoryBudget(categoryId, amount?.takeIf { it > 0 })
                    showBudgetDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setCategoryBudget(categoryId, null)
                    showBudgetDialog = false
                }) { Text("Clear") }
            }
        )
    }
}
