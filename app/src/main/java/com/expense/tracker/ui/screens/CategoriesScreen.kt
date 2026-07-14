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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import com.expense.tracker.data.db.CategorySummary
import com.expense.tracker.ui.MainViewModel
import com.expense.tracker.ui.formatAmount
import com.expense.tracker.ui.theme.ExpenseRed
import com.expense.tracker.ui.theme.IncomeGreen

@Composable
fun CategoriesScreen(viewModel: MainViewModel) {
    val summaries by viewModel.categorySummaries.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    val totalIn = summaries.sumOf { it.totalIn }
    val totalOut = summaries.sumOf { it.totalOut }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { RangeSelector(viewModel) }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Budget by category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        StatItem("Received", totalIn, IncomeGreen, Modifier.weight(1f))
                        StatItem("Spent", totalOut, ExpenseRed, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    val net = totalIn - totalOut
                    Text(
                        "Net  " + (if (net >= 0) "+" else "") + formatAmount(net),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (net >= 0) IncomeGreen else ExpenseRed
                    )
                    Text(
                        "Assign categories on a transaction’s detail screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        if (summaries.isEmpty()) {
            item { EmptyState("No categorized (or any) transactions in this period.") }
        }

        items(summaries, key = { "${it.categoryId}-${it.categoryName}" }) { summary ->
            CategoryBudgetCard(summary)
        }

        item {
            Text(
                "Manage categories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(categories, key = { it.id }) { cat ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(cat.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    IconButton(onClick = { pendingDeleteId = cat.id }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${cat.name}")
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New category") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addCategory(newName)
                        newName = ""
                        showAdd = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Cancel") }
            }
        )
    }

    pendingDeleteId?.let { id ->
        val name = categories.find { it.id == id }?.name ?: "this category"
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete category?") },
            text = { Text("\"$name\" will be removed. Transactions in it become Uncategorized.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(id)
                    pendingDeleteId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CategoryBudgetCard(summary: CategorySummary) {
    val net = summary.totalIn - summary.totalOut
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    summary.categoryName,
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
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Row {
                AmountCol("In", summary.totalIn, IncomeGreen, Modifier.weight(1f))
                AmountCol("Out", summary.totalOut, ExpenseRed, Modifier.weight(1f))
                AmountCol("Net", net, if (net >= 0) IncomeGreen else ExpenseRed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AmountCol(
    label: String,
    amount: Double,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatAmount(amount), color = color, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}
