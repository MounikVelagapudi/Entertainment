package com.example.entertainment.pos.testdata

import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentRequest
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.Product
import com.example.entertainment.pos.domain.model.SyncStatus
import com.example.entertainment.pos.domain.model.TestCard
import com.example.entertainment.pos.domain.model.Transaction

/* Same rows as ProductCatalog, so lookups by barcode return equal products. */
object TestProducts {
    val POPCORN = Product("012345678905", "Popcorn (L)", 899)
    val NACHOS = Product("036000291452", "Nachos", 749)
    val SODA = Product("042100005264", "Soda (M)", 499)
    val CANDY_BAR = Product("070847811169", "Candy Bar", 325)
    val HOT_DOG = Product("490000000016", "Hot Dog", 599)
    val MOVIE_TICKET = Product("885909950805", "Movie Ticket", 1499)

    val ALL = listOf(POPCORN, NACHOS, SODA, CANDY_BAR, HOT_DOG, MOVIE_TICKET)
}

const val UNKNOWN_BARCODE = "000000000000"

fun aProduct(barcode: String = "x", name: String = "Item", priceCents: Long = 100) =
    Product(barcode, name, priceCents)

fun aCartLine(product: Product = TestProducts.POPCORN, quantity: Int = 1) = CartLine(product, quantity)

/* Leaves the payment fields on their model defaults; use copy() to set them. */
fun aTransaction(
    id: String = "t",
    lines: List<CartLine> = listOf(aCartLine()),
    status: SyncStatus = SyncStatus.PENDING,
    conflictBarcode: String? = null,
    createdAtMillis: Long = 0,
) = Transaction(
    id = id,
    lines = lines,
    totalCents = lines.sumOf { it.lineTotalCents },
    createdAtMillis = createdAtMillis,
    status = status,
    serverReference = null,
    conflictBarcode = conflictBarcode,
)

/* One sale per SyncStatus, id = the status name in lower case. */
fun oneTransactionPerStatus() = SyncStatus.entries.map { aTransaction(id = it.name.lowercase(), status = it) }

fun anApprovedPayment(reference: String? = "pi_1", cardLast4: String? = "4242") =
    PaymentResult.Approved(reference, cardLast4)

/* Card terminal auth taken offline; the card number rides along as the token for the sync worker. */
fun anOfflineCardPayment(card: TestCard = TestCard.VISA_SUCCESS) =
    PaymentResult.Approved(null, card.last4, storedOffline = true, offlineToken = card.number)

fun aPaymentRequest(
    method: PaymentMethod,
    testCard: TestCard = TestCard.VISA_SUCCESS,
    saleId: String = "sale-12345678-abcd",
    idempotencyKey: String = "k:pay",
    amountCents: Long = 1798,
) = PaymentRequest(
    saleId = saleId,
    idempotencyKey = idempotencyKey,
    amountCents = amountCents,
    method = method,
    testCard = testCard,
)
