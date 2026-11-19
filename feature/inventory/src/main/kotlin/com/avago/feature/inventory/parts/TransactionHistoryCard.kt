package com.avago.feature.inventory.parts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avago.core.data.domain.MovementKind
import com.avago.core.data.domain.StockLedgerEntry
import com.avago.core.data.domain.formatQty
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The stock ledger of one product (posted movement legs, newest first). */
@Composable
fun TransactionHistoryCard(
    transactions: List<StockLedgerEntry>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Transaction History",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (transactions.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = transactions.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No transactions yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                transactions.forEachIndexed { index, txn ->
                    TransactionHistoryRow(txn)
                    if (index < transactions.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionHistoryRow(txn: StockLedgerEntry) {
    val icon = when (txn.kind) {
        MovementKind.RECEIPT, MovementKind.OPENING_BALANCE, MovementKind.RETURN -> "📦"
        MovementKind.ISSUE -> "🔧"
        MovementKind.COUNT -> "📋"
        MovementKind.TRANSFER, MovementKind.TRANSFER_DISPATCH, MovementKind.TRANSFER_RECEIPT -> "↔️"
        MovementKind.ADJUSTMENT -> "✏️"
    }
    val positive = txn.quantity.signum() >= 0
    val quantityColor = if (positive) Color(0xFF4CAF50) else Color(0xFFF44336)
    val quantityText = if (positive) "+${txn.quantity.formatQty()}" else "−${txn.quantity.negate().formatQty()}"
    val typeLabel = listOfNotNull(
        txn.kind.label + if (txn.isReversal) " (reversal)" else "",
        txn.number,
    ).joinToString(" · ")
    val note = txn.notes ?: txn.reference

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = icon, style = MaterialTheme.typography.bodyMedium)
                Column {
                    Text(
                        text = quantityText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = quantityColor,
                    )
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = formatTransactionDate(txn.effectiveAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, top = 2.dp),
            )
        }
    }
}

/** "Opening balance", "Transfer dispatch", … */
internal val MovementKind.label: String
    get() = wire.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

/** Convert an epoch-millis timestamp to "MMM d" format for display. */
internal fun formatTransactionDate(epochMillis: Long): String {
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    return fmt.format(Date(epochMillis))
}
