package com.example.entertainment.pos.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private const val ROUTE_SCANNER = "pos/scanner"
private const val ROUTE_CHECKOUT = "pos/checkout"
private const val ROUTE_RECEIPT_BASE = "pos/receipt"
private const val ROUTE_DETAIL_BASE = "pos/detail"
private const val ROUTE_ACTIVITY = "pos/activity"
private const val ROUTE_SYNC_LAB = "pos/sync-lab"

private val idArg = listOf(navArgument(TransactionDetailViewModel.ARG_ID) { type = NavType.StringType })

/**
 * POS tab: scanner -> checkout -> receipt, plus Activity / detail / Sync Lab for
 * watching sales move through sync. Scanner + checkout share one ViewModel for the cart.
 */
@Composable
fun PosScreen(
    modifier: Modifier = Modifier,
    viewModel: PosViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.navigateToReceipt) {
        val id = state.navigateToReceipt ?: return@LaunchedEffect
        navController.navigate("$ROUTE_RECEIPT_BASE/$id") {
            /* Back from the receipt shouldn't land on an emptied checkout. */
            popUpTo(ROUTE_SCANNER)
        }
        viewModel.onReceiptNavigated()
    }

    NavHost(navController = navController, startDestination = ROUTE_SCANNER, modifier = modifier) {
        composable(ROUTE_SCANNER) {
            ScannerScreen(
                state = state,
                onScanClick = viewModel::onScanClick,
                onManualEntry = viewModel::onManualEntry,
                onIncrement = viewModel::onIncrement,
                onDecrement = viewModel::onDecrement,
                onCheckoutClick = { navController.navigate(ROUTE_CHECKOUT) },
                onActivityClick = { navController.navigate(ROUTE_ACTIVITY) },
                onSyncLabClick = { navController.navigate(ROUTE_SYNC_LAB) },
            )
        }
        composable(ROUTE_CHECKOUT) {
            CheckoutScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onPaymentMethodSelected = viewModel::onPaymentMethodSelected,
                onTestCardSelected = viewModel::onTestCardSelected,
                onPayClick = viewModel::onPayClick,
                onPayPalApproved = viewModel::onPayPalApproved,
                onPayPalCancelled = viewModel::onPayPalCancelled,
            )
        }
        composable("$ROUTE_RECEIPT_BASE/{${TransactionDetailViewModel.ARG_ID}}", arguments = idArg) {
            TransactionDetailRoute(
                isReceipt = true,
                onBack = { navController.popBackStack() },
                onNewSale = { navController.popBackStack(ROUTE_SCANNER, inclusive = false) },
            )
        }
        composable(ROUTE_ACTIVITY) {
            ActivityScreen(
                onBack = { navController.popBackStack() },
                onTransactionClick = { id -> navController.navigate("$ROUTE_DETAIL_BASE/$id") },
            )
        }
        composable("$ROUTE_DETAIL_BASE/{${TransactionDetailViewModel.ARG_ID}}", arguments = idArg) {
            TransactionDetailRoute(
                isReceipt = false,
                onBack = { navController.popBackStack() },
                onNewSale = {},
            )
        }
        composable(ROUTE_SYNC_LAB) {
            SyncLabScreen(onBack = { navController.popBackStack() })
        }
    }
}
