package com.expense.tracker

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ReceiptLong
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.expense.tracker.ui.MainViewModel
import com.expense.tracker.ui.SyncState
import com.expense.tracker.ui.screens.DashboardScreen
import com.expense.tracker.ui.screens.InsightsScreen
import com.expense.tracker.ui.screens.SignInScreen
import com.expense.tracker.ui.screens.TransactionsScreen
import com.expense.tracker.ui.theme.ExpenseTrackerTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes

class MainActivity : ComponentActivity() {

    private lateinit var signInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
            .build()
        signInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            ExpenseTrackerTheme {
                App(signInClient)
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(signInClient: GoogleSignInClient) {
    val viewModel: MainViewModel = viewModel()
    val accountName by viewModel.accountName.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var signInError by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        GoogleSignIn.getSignedInAccountFromIntent(result.data)
            .addOnSuccessListener { account ->
                signInError = null
                account.email?.let { viewModel.onSignedIn(it) }
            }
            .addOnFailureListener { e ->
                val code = (e as? ApiException)?.statusCode
                signInError = when (code) {
                    GoogleSignInStatusCodes.DEVELOPER_ERROR ->
                        "Sign-in rejected by Google (error 10: developer error).\n\n" +
                            "This app's package name + SHA-1 aren't registered. In Google " +
                            "Cloud Console create an Android OAuth client with package " +
                            "\"com.expense.tracker\" and this device build's debug SHA-1, " +
                            "and make sure your Gmail address is added as a test user."
                    GoogleSignInStatusCodes.SIGN_IN_CANCELLED ->
                        "Sign-in was cancelled."
                    GoogleSignInStatusCodes.NETWORK_ERROR ->
                        "Network error during sign-in. Check your connection and retry."
                    else ->
                        "Sign-in failed (code $code): ${e.message ?: "unknown error"}"
                }
            }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.syncNow() }

    LaunchedEffect(syncState) {
        when (val state = syncState) {
            is SyncState.NeedsConsent -> consentLauncher.launch(state.intent)
            is SyncState.Error -> snackbarHostState.showSnackbar("Sync failed: ${state.message}")
            is SyncState.Success ->
                snackbarHostState.showSnackbar("Synced ${state.count} transactions")
            else -> Unit
        }
    }

    if (accountName == null) {
        SignInScreen(
            onSignInClick = {
                signInError = null
                signInLauncher.launch(signInClient.signInIntent)
            },
            errorMessage = signInError
        )
        return
    }

    val tabs = listOf(
        Tab("dashboard", "Dashboard", Icons.Default.Dashboard),
        Tab("transactions", "Transactions", Icons.Default.ReceiptLong),
        Tab("insights", "Insights", Icons.Default.Insights)
    )
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        accountName ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    if (syncState is SyncState.Syncing) {
                        CircularProgressIndicator(Modifier.padding(12.dp).height(24.dp))
                    } else {
                        IconButton(onClick = { viewModel.syncNow() }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sync now")
                        }
                    }
                    IconButton(onClick = {
                        signInClient.signOut()
                        viewModel.signOut()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                }
            )
        },
        bottomBar = {
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
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            composable("dashboard") { DashboardScreen(viewModel) }
            composable("transactions") { TransactionsScreen(viewModel) }
            composable("insights") { InsightsScreen(viewModel) }
        }
    }
}
