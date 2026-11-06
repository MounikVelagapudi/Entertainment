package com.example.entertainment.pos.data.payment

import com.example.entertainment.pos.data.remote.PaymentDto
import com.example.entertainment.pos.data.remote.PosApi
import com.example.entertainment.pos.data.remote.PosErrorResponse
import com.example.entertainment.pos.data.remote.RefundDto
import com.example.entertainment.pos.domain.NetworkMonitor
import com.example.entertainment.pos.domain.PaymentProcessor
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentRequest
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.RefundResult
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Mimics each provider's shape against the mock `v1/payments` endpoint:
 *
 * - CARD_TERMINAL -> Stripe Terminal. Online: PaymentIntent created + confirmed. Offline:
 *   approved locally and stored for forwarding (Stripe Terminal "offline mode").
 * - STRIPE_ONLINE -> Stripe PaymentSheet. Online only.
 * - PAYPAL        -> PayPal order create + capture, after the buyer approves. Online only.
 * - CASH          -> no gateway at all.
 *
 * Swapping in the real SDKs means replacing the `when` branches; nothing above this
 * class changes. See README "Real payment SDKs".
 */
class SimulatedPaymentProcessor @Inject constructor(
    private val api: PosApi,
    private val networkMonitor: NetworkMonitor,
) : PaymentProcessor {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun authorize(request: PaymentRequest): PaymentResult {
        val online = networkMonitor.isOnline.first()
        if (request.method.requiresNetwork && !online) {
            return PaymentResult.Error("${request.method.label} needs a connection")
        }

        return when (request.method) {
            PaymentMethod.CASH -> PaymentResult.Approved(reference = "CASH-" + request.saleId.take(8), cardLast4 = null)

            PaymentMethod.CARD_TERMINAL ->
                if (online) {
                    charge(request.idempotencyKey, PaymentDto("stripe_terminal", request.amountCents, testCardNumber = request.testCard.number))
                } else {
                    /* The risk the merchant takes on: we won't know about a decline until
                       this is forwarded. Real terminals cap offline amounts for that reason. */
                    PaymentResult.Approved(
                        reference = null,
                        cardLast4 = request.testCard.last4,
                        storedOffline = true,
                        offlineToken = request.testCard.number,
                    )
                }

            PaymentMethod.STRIPE_ONLINE ->
                charge(request.idempotencyKey, PaymentDto("stripe", request.amountCents, testCardNumber = request.testCard.number))

            PaymentMethod.PAYPAL ->
                charge(request.idempotencyKey, PaymentDto("paypal", request.amountCents))
        }
    }

    override suspend fun forwardOffline(
        saleId: String,
        idempotencyKey: String,
        amountCents: Long,
        token: String,
    ): PaymentResult = charge(idempotencyKey, PaymentDto("stripe_terminal", amountCents, testCardNumber = token))

    override suspend fun refund(
        method: PaymentMethod,
        paymentReference: String?,
        amountCents: Long,
        idempotencyKey: String,
    ): RefundResult {
        if (method == PaymentMethod.CASH || paymentReference == null) {
            return RefundResult.Refunded("CASH-REFUND-" + UUID.randomUUID().toString().take(8))
        }
        return try {
            RefundResult.Refunded(api.createRefund(idempotencyKey, RefundDto(paymentReference, amountCents)).id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            RefundResult.Error("Network error, try again")
        } catch (e: HttpException) {
            RefundResult.Error("Refund failed (${e.code()})")
        }
    }

    private suspend fun charge(idempotencyKey: String, body: PaymentDto): PaymentResult = try {
        val response = api.createPayment(idempotencyKey, body)
        PaymentResult.Approved(reference = response.id, cardLast4 = response.last4)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        PaymentResult.Error("Network error, try again")
    } catch (e: HttpException) {
        if (e.code() == 402) {
            val error = runCatching {
                json.decodeFromString<PosErrorResponse>(e.response()?.errorBody()?.string().orEmpty())
            }.getOrNull()
            PaymentResult.Declined(error?.declineCode ?: "card_declined")
        } else {
            PaymentResult.Error("Payment failed (${e.code()})")
        }
    }
}
