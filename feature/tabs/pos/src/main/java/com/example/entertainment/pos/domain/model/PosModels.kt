package com.example.entertainment.pos.domain.model

/* Money stays in cents end to end -- no Double rounding on totals. */
data class Product(
    val barcode: String,
    val name: String,
    val priceCents: Long,
)

data class CartLine(
    val product: Product,
    val quantity: Int,
) {
    val lineTotalCents: Long get() = product.priceCents * quantity
}

/**
 * AWAITING_PAYMENT -- row written before the charge, so a crash mid-payment still
 *                     leaves a record to reconcile against the gateway.
 * PENDING      -- paid, saved locally, waiting on WorkManager.
 * SYNCED       -- backend accepted it.
 * CONFLICT     -- backend rejected on business rules (e.g. stock sold elsewhere while
 *                 we were offline). Retrying won't help; a person has to resolve it.
 * FAILED       -- any other non-retryable rejection, incl. an offline card that
 *                 declined once forwarded.
 * VOIDED       -- reversed after a conflict, payment refunded. Kept for audit.
 * SHIP_TO_HOME -- conflict resolved by fulfilling from the DC instead.
 */
enum class SyncStatus { AWAITING_PAYMENT, PENDING, SYNCED, CONFLICT, FAILED, VOIDED, SHIP_TO_HOME }

enum class PaymentMethod(val label: String, val requiresNetwork: Boolean) {
    CASH("Cash", requiresNetwork = false),
    /* Stripe Terminal-style card-present. Can authorize offline and forward later. */
    CARD_TERMINAL("Card (terminal)", requiresNetwork = false),
    /* Stripe PaymentSheet-style card-not-present. */
    STRIPE_ONLINE("Stripe online", requiresNetwork = true),
    PAYPAL("PayPal", requiresNetwork = true),
}

enum class PaymentStatus { NONE, APPROVED, STORED_OFFLINE, DECLINED, REFUNDED }

/* Stripe's published test numbers, so the same scenarios work against the real sandbox. */
enum class TestCard(val number: String, val label: String) {
    VISA_SUCCESS("4242424242424242", "4242 approves"),
    GENERIC_DECLINE("4000000000000002", "0002 declines"),
    INSUFFICIENT_FUNDS("4000000000009995", "9995 no funds"),
    ;

    val last4: String get() = number.takeLast(4)
}

data class Transaction(
    val id: String,
    val lines: List<CartLine>,
    val totalCents: Long,
    val createdAtMillis: Long,
    val status: SyncStatus,
    val serverReference: String?,
    val statusReason: String? = null,
    val conflictBarcode: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val paymentStatus: PaymentStatus = PaymentStatus.NONE,
    val paymentReference: String? = null,
    val cardLast4: String? = null,
    val managerOverride: Boolean = false,
) {
    val conflictItemName: String?
        get() = lines.find { it.product.barcode == conflictBarcode }?.product?.name
}

data class TransactionEvent(
    val atMillis: Long,
    val type: String,
    val detail: String?,
)

fun Long.formatCents(): String = "$%d.%02d".format(this / 100, this % 100)
