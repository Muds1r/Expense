package com.expense.tracker

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.expense.tracker.ui.MainViewModel
import com.expense.tracker.ui.SyncState
import com.expense.tracker.ui.screens.AddManualTransactionDialog
import com.expense.tracker.ui.screens.CategoriesScreen
import com.expense.tracker.ui.screens.CategoryDetailScreen
import com.expense.tracker.ui.screens.DashboardScreen
import com.expense.tracker.ui.screens.InsightsScreen
import com.expense.tracker.ui.screens.SetupScreen
import com.expense.tracker.ui.screens.TransactionDetailScreen
import com.expense.tracker.ui.screens.TransactionsScreen
import com.expense.tracker.ui.theme.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExpenseTrackerTheme {
                App()
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private const val SNACKBAR_FRESH_MS = 30_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val viewModel: MainViewModel = viewModel()
    val accountName by viewModel.accountName.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (accountName == null) {
        SetupScreen(
            onConnect = { email, password ->
                ensureNotificationPermission()
                viewModel.connect(email, password)
            },
            isConnecting = syncState is SyncState.Syncing,
            errorMessage = (syncState as? SyncState.Error)?.message
        )
        return
    }

    LaunchedEffect(Unit) {
        ensureNotificationPermission()
    }

    LaunchedEffect(syncState) {
        when (val state = syncState) {
            is SyncState.Error -> snackbarHostState.showSnackbar("Sync failed: ${state.message}")
            is SyncState.Success -> {
                if (System.currentTimeMillis() - state.at <= SNACKBAR_FRESH_MS) {
                    snackbarHostState.showSnackbar("Synced ${state.count} transactions")
                }
            }
            else -> Unit
        }
    }

    val tabs = listOf(
        Tab("dashboard", "Dashboard", Icons.Default.Dashboard),
        Tab("transactions", "Transactions", Icons.AutoMirrored.Filled.ReceiptLong),
        Tab("budget", "Budget", Icons.Default.Category),
        Tab("insights", "Insights", Icons.Default.Insights)
    )
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isDetail = currentRoute?.startsWith("transaction/") == true ||
        currentRoute?.startsWith("category/") == true
    val detailTitle = when {
        currentRoute?.startsWith("transaction/") == true -> "Transaction"
        currentRoute?.startsWith("category/") == true -> "Category"
        else -> null
    }

    val onTransactionClick: (String) -> Unit = { id ->
        navController.navigate("transaction/${Uri.encode(id)}")
    }
    val onCategoryClick: (Long?) -> Unit = { id ->
        navController.navigate("category/${id ?: -1L}")
    }

    var showAddManual by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    if (isDetail) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = {
                    if (detailTitle != null) {
                        Text(
                            detailTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Column {
                            Text(
                                "Expense Tracker",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                accountName ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (!isDetail) {
                        if (syncState is SyncState.Syncing) {
                            CircularProgressIndicator(Modifier.padding(12.dp).height(24.dp))
                        } else {
                            IconButton(onClick = {
                                ensureNotificationPermission()
                                viewModel.syncNow()
                            }) {
                                Icon(Icons.Default.Sync, contentDescription = "Sync now")
                            }
                        }
                        IconButton(onClick = { viewModel.signOut() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isDetail && currentRoute == "transactions") {
                FloatingActionButton(
                    onClick = { showAddManual = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add manual transaction")
                }
            }
        },
        bottomBar = {
            if (!isDetail) {
                Column {
                    NavigationBar(
                        windowInsets = WindowInsets(0),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                    Text(
                        "Made By Muds1r",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .navigationBarsPadding()
                            .padding(bottom = 4.dp)
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            composable("dashboard") { DashboardScreen(viewModel) }
            composable("transactions") { TransactionsScreen(viewModel, onTransactionClick) }
            composable("budget") { CategoriesScreen(viewModel, onCategoryClick) }
            composable("insights") { InsightsScreen(viewModel, onTransactionClick) }
            composable(
                route = "category/{categoryId}",
                arguments = listOf(navArgument("categoryId") { type = NavType.LongType })
            ) { entry ->
                val raw = entry.arguments?.getLong("categoryId") ?: -1L
                CategoryDetailScreen(
                    viewModel = viewModel,
                    categoryId = raw.takeIf { it >= 0 },
                    onTransactionClick = onTransactionClick
                )
            }
            composable(
                route = "transaction/{txnId}",
                arguments = listOf(navArgument("txnId") { type = NavType.StringType })
            ) { entry ->
                val id = Uri.decode(entry.arguments?.getString("txnId").orEmpty())
                TransactionDetailScreen(viewModel, id)
            }
        }
    }

    if (showAddManual) {
        AddManualTransactionDialog(
            categories = viewModel.categories.collectAsState().value.map { it.id to it.name },
            onConfirm = { bank, counterparty, amount, type, note, categoryId ->
                viewModel.addManualTransaction(bank, counterparty, amount, type, note, categoryId)
                showAddManual = false
            },
            onDismiss = { showAddManual = false }
        )
    }
}
