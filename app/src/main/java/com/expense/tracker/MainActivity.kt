package com.expense.tracker

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.expense.tracker.ui.MainViewModel
import com.expense.tracker.ui.SyncState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val viewModel: MainViewModel = viewModel()
    val accountName by viewModel.accountName.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    if (accountName == null) {
        SetupScreen(
            onConnect = { email, password -> viewModel.connect(email, password) },
            isConnecting = syncState is SyncState.Syncing,
            errorMessage = (syncState as? SyncState.Error)?.message
        )
        return
    }

    LaunchedEffect(syncState) {
        when (val state = syncState) {
            is SyncState.Error -> snackbarHostState.showSnackbar("Sync failed: ${state.message}")
            is SyncState.Success ->
                snackbarHostState.showSnackbar("Synced ${state.count} transactions")
            else -> Unit
        }
    }

    val tabs = listOf(
        Tab("dashboard", "Dashboard", Icons.Default.Dashboard),
        Tab("transactions", "Transactions", Icons.AutoMirrored.Filled.ReceiptLong),
        Tab("insights", "Insights", Icons.Default.Insights)
    )
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isDetail = currentRoute?.startsWith("transaction/") == true

    val onTransactionClick: (String) -> Unit = { id ->
        navController.navigate("transaction/${Uri.encode(id)}")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (isDetail) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = {
                    if (isDetail) {
                        Text("Transaction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    } else {
                        Column {
                            Text(
                                "Expense Tracker",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
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
                            IconButton(onClick = { viewModel.syncNow() }) {
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
        bottomBar = {
            if (!isDetail) {
                Column {
                    NavigationBar(windowInsets = WindowInsets(0)) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer)
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
            composable("insights") { InsightsScreen(viewModel, onTransactionClick) }
            composable(
                route = "transaction/{txnId}",
                arguments = listOf(navArgument("txnId") { type = NavType.StringType })
            ) { entry ->
                val id = Uri.decode(entry.arguments?.getString("txnId").orEmpty())
                TransactionDetailScreen(viewModel, id)
            }
        }
    }
}
