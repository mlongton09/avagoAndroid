package com.avago.feature.provider.nav

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.avago.feature.provider.R
import com.avago.feature.provider.ui.ProviderCustomerScreen
import com.avago.feature.provider.ui.ProviderCustomersScreen
import com.avago.feature.provider.ui.ProviderDashboardScreen
import com.avago.feature.provider.ui.ProviderInvoiceScreen
import com.avago.feature.provider.ui.ProviderInvoicesScreen
import com.avago.feature.provider.ui.ProviderJobScreen
import com.avago.feature.provider.ui.ProviderJobsScreen
import com.avago.feature.provider.ui.ProviderQuoteScreen
import com.avago.feature.provider.ui.ProviderQuotesScreen

/** Field-mechanic workspace destinations; every screen draws its own top bar. */
object ProviderRoutes {
    const val PREFIX = "provider/"
    const val HOME = "provider/home"
    const val CUSTOMERS = "provider/customers"
    const val CUSTOMER = "provider/customers/{customerId}"
    const val JOBS = "provider/jobs"
    const val JOB = "provider/jobs/{jobId}"
    const val QUOTES = "provider/quotes"
    const val QUOTE = "provider/quotes/{quoteId}"
    const val INVOICES = "provider/invoices"
    const val INVOICE = "provider/invoices/{invoiceId}"

    fun customer(id: String) = "provider/customers/${Uri.encode(id)}"
    fun job(id: String) = "provider/jobs/${Uri.encode(id)}"
    fun quote(id: String) = "provider/quotes/${Uri.encode(id)}"
    fun invoice(id: String) = "provider/invoices/${Uri.encode(id)}"
}

fun NavGraphBuilder.providerNavGraph(navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack() }
    val go: (String) -> Unit = { navController.navigate(it) { launchSingleTop = true } }
    fun arg(name: String) = navArgument(name) { type = NavType.StringType }

    composable(ProviderRoutes.HOME) {
        ProviderDashboardScreen(
            onBack = back,
            onCustomers = { go(ProviderRoutes.CUSTOMERS) },
            onJobs = { go(ProviderRoutes.JOBS) },
            onQuotes = { go(ProviderRoutes.QUOTES) },
            onInvoices = { go(ProviderRoutes.INVOICES) },
            onCustomer = { go(ProviderRoutes.customer(it)) },
            onJob = { go(ProviderRoutes.job(it)) },
            onQuote = { go(ProviderRoutes.quote(it)) },
            onInvoice = { go(ProviderRoutes.invoice(it)) },
        )
    }
    composable(ProviderRoutes.CUSTOMERS) { ProviderCustomersScreen(onBack = back, onCustomer = { go(ProviderRoutes.customer(it)) }) }
    composable(ProviderRoutes.CUSTOMER, arguments = listOf(arg("customerId"))) {
        ProviderCustomerScreen(
            onBack = back,
            onJob = { go(ProviderRoutes.job(it)) },
            onQuote = { go(ProviderRoutes.quote(it)) },
            onInvoice = { go(ProviderRoutes.invoice(it)) },
        )
    }
    composable(ProviderRoutes.JOBS) { ProviderJobsScreen(onBack = back, onJob = { go(ProviderRoutes.job(it)) }) }
    composable(ProviderRoutes.JOB, arguments = listOf(arg("jobId"))) {
        ProviderJobScreen(onBack = back, onCustomer = { go(ProviderRoutes.customer(it)) }, onInvoice = { go(ProviderRoutes.invoice(it)) })
    }
    composable(ProviderRoutes.QUOTES) { ProviderQuotesScreen(onBack = back, onQuote = { go(ProviderRoutes.quote(it)) }) }
    composable(ProviderRoutes.QUOTE, arguments = listOf(arg("quoteId"))) {
        ProviderQuoteScreen(onBack = back, onCustomer = { go(ProviderRoutes.customer(it)) }, onJob = { go(ProviderRoutes.job(it)) })
    }
    composable(ProviderRoutes.INVOICES) { ProviderInvoicesScreen(onBack = back, onInvoice = { go(ProviderRoutes.invoice(it)) }) }
    composable(ProviderRoutes.INVOICE, arguments = listOf(arg("invoiceId"))) {
        ProviderInvoiceScreen(onBack = back, onCustomer = { go(ProviderRoutes.customer(it)) }, onJob = { go(ProviderRoutes.job(it)) })
    }
}

/** Drawer entry for the host's side menu. */
@Composable
fun ProviderDrawerItem(currentRoute: String?, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    NavigationDrawerItem(
        label = { Text(stringResource(R.string.provider_title)) },
        icon = { Icon(Icons.Filled.Handyman, contentDescription = null) },
        selected = currentRoute?.startsWith(ProviderRoutes.PREFIX) == true,
        onClick = { onNavigate(ProviderRoutes.HOME) },
        modifier = modifier,
    )
}
