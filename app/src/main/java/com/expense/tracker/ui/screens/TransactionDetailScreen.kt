package com.expense.tracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.expense.tracker.data.db.SplitEntity
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
            val splits by remember(txnId) { viewModel.splitsForTransaction(txnId) }
                .collectAsState(initial = emptyList())

            TransactionDetailContent(
                txn = txn,
                categoryName = categoryName,
                categories = categories.map { it.id to it.name },
                splits = splits,
                onCategorySelected = { viewModel.setCategory(txnId, it) },
                onNoteSaved = { viewModel.setNote(txnId, it) },
                onTransferToggled = { viewModel.setTransfer(txnId, it) },
                onAddSplit = { amount, catId, note -> viewModel.addSplit(txnId, amount, catId, note) },
                onUpdateSplit = { splitId, amount, catId, note -> viewModel.updateSplit(splitId, amount, catId, note) },
                onDeleteSplit = { viewModel.deleteSplit(it) },
                onDeleteManual = if (txn.isManual) {{ viewModel.deleteManualTransaction(txnId) }} else null
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
    splits: List<SplitEntity>,
    onCategorySelected: (Long?) -> Unit,
    onNoteSaved: (String?) -> Unit,
    onTransferToggled: (Boolean) -> Unit,
    onAddSplit: (Double, Long?, String?) -> Unit,
    onUpdateSplit: (Long, Double, Long?, String?) -> Unit,
    onDeleteSplit: (Long) -> Unit,
    onDeleteManual: (() -> Unit)?
) {
    val isCredit = txn.type == TxnType.CREDIT
    val color = if (isCredit) IncomeGreen else ExpenseRed
    var menuOpen by remember { mutableStateOf(false) }

    var noteText by remember(txn.note) { mutableStateOf(txn.note ?: "") }
    var noteEditing by remember { mutableStateOf(false) }

    var showAddSplit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ─── Amount hero card ──────────────────────────────────────────
        SoftHeroCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (txn.isTransfer) "Transfer"
                            else if (isCredit) "Money in" else "Money out",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            (if (isCredit) "+" else "-") + formatAmount(txn.amount),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = color
                        )
                    }
                    if (txn.isManual) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete entry")
                        }
                    }
                }
            }
        }

        // ─── Transfer toggle ───────────────────────────────────────────
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Mark as transfer", fontWeight = FontWeight.Medium)
                    Text(
                        "Pass-through money \u2014 excluded from income/spending summaries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = txn.isTransfer,
                    onCheckedChange = onTransferToggled
                )
            }
        }

        // ─── Detail cards ──────────────────────────────────────────────
        DetailCard(
            if (isCredit) "Sender / from" else "Receiver / paid to",
            txn.counterparty ?: "\u2014"
        )
        DetailCard("Bank", txn.bank)
        DetailCard("Account", txn.accountLast4?.let { "\u2022\u2022$it" } ?: "\u2014")
        DetailCard("Date & time", formatDateTime(txn.timestamp))
        DetailCard("Email subject", txn.subject)

        // ─── Category ──────────────────────────────────────────────────
        SoftCard(modifier = Modifier.fillMaxWidth()) {
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

        // ─── Note ──────────────────────────────────────────────────────
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Note",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        if (noteEditing) {
                            onNoteSaved(noteText.ifBlank { null })
                            noteEditing = false
                        } else {
                            noteEditing = true
                        }
                    }) {
                        Text(if (noteEditing) "Save" else "Edit")
                    }
                }
                if (noteEditing) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Add a note...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5
                    )
                } else {
                    Text(
                        txn.note?.ifBlank { null } ?: "Tap Edit to add a note",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (txn.note.isNullOrBlank())
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // ─── Splits ────────────────────────────────────────────────────
        SoftCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Splits",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showAddSplit = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }

                if (splits.isEmpty()) {
                    Text(
                        "Split this transaction into parts with their own categories and notes.\n" +
                            "e.g. 50k withdrawal \u2192 10k savings + 40k bill",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    val splitTotal = splits.sumOf { it.amount }
                    splits.forEachIndexed { index, split ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        SplitRow(
                            split = split,
                            categories = categories,
                            onEdit = { amount, catId, note -> onUpdateSplit(split.id, amount, catId, note) },
                            onDelete = { onDeleteSplit(split.id) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Text(
                            "Split total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            formatAmount(splitTotal),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (splitTotal > txn.amount) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        DetailCard("Message ID", txn.id)
    }

    // ─── Add split dialog ──────────────────────────────────────────────
    if (showAddSplit) {
        SplitEditDialog(
            categories = categories,
            initialAmount = null,
            initialCategoryId = null,
            initialNote = null,
            title = "New split",
            onConfirm = { amount, catId, note ->
                onAddSplit(amount, catId, note)
                showAddSplit = false
            },
            onDismiss = { showAddSplit = false }
        )
    }

    // ─── Delete manual transaction confirmation ────────────────────────
    if (showDeleteConfirm && onDeleteManual != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete entry?") },
            text = { Text("This manually added transaction will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteManual()
                    showDeleteConfirm = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitRow(
    split: SplitEntity,
    categories: List<Pair<Long, String>>,
    onEdit: (Double, Long?, String?) -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var amountText by remember(split.amount) { mutableStateOf(split.amount.toLong().toString()) }
    var noteText by remember(split.note) { mutableStateOf(split.note ?: "") }
    var selectedCategoryId by remember(split.categoryId) { mutableStateOf(split.categoryId) }
    var catMenuOpen by remember { mutableStateOf(false) }

    val catName = categories.find { it.first == selectedCategoryId }?.second ?: "Uncategorized"

    if (editing) {
        Column {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = catMenuOpen,
                onExpandedChange = { catMenuOpen = it }
            ) {
                OutlinedTextField(
                    value = catName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catMenuOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    label = { Text("Category") }
                )
                ExposedDropdownMenu(
                    expanded = catMenuOpen,
                    onDismissRequest = { catMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Uncategorized") },
                        onClick = { selectedCategoryId = null; catMenuOpen = false }
                    )
                    categories.forEach { (id, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = { selectedCategoryId = id; catMenuOpen = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = { Text("Note (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = { editing = false }) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val amt = amountText.toDoubleOrNull() ?: return@TextButton
                    if (amt > 0) onEdit(amt, selectedCategoryId, noteText)
                    editing = false
                }) { Text("Save") }
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatAmount(split.amount),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (selectedCategoryId != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            catName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (!split.note.isNullOrBlank()) {
                    Text(
                        split.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { editing = true }) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit split",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete split",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitEditDialog(
    categories: List<Pair<Long, String>>,
    initialAmount: Double?,
    initialCategoryId: Long?,
    initialNote: String?,
    title: String,
    onConfirm: (Double, Long?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(initialAmount?.let { it.toLong().toString() } ?: "") }
    var selectedCategoryId by remember { mutableStateOf(initialCategoryId) }
    var noteText by remember { mutableStateOf(initialNote ?: "") }
    var catMenuOpen by remember { mutableStateOf(false) }

    val catName = categories.find { it.first == selectedCategoryId }?.second ?: "Uncategorized"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                ExposedDropdownMenuBox(
                    expanded = catMenuOpen,
                    onExpandedChange = { catMenuOpen = it }
                ) {
                    OutlinedTextField(
                        value = catName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catMenuOpen) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        label = { Text("Category") }
                    )
                    ExposedDropdownMenu(
                        expanded = catMenuOpen,
                        onDismissRequest = { catMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Uncategorized") },
                            onClick = { selectedCategoryId = null; catMenuOpen = false }
                        )
                        categories.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { selectedCategoryId = id; catMenuOpen = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amountText.toDoubleOrNull() ?: return@TextButton
                if (amt > 0) onConfirm(amt, selectedCategoryId, noteText)
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DetailCard(label: String, value: String) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
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
