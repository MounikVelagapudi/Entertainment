package com.example.entertainment.pos.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.entertainment.pos.data.local.TransactionDao
import com.example.entertainment.pos.data.local.TransactionEventEntity
import com.example.entertainment.pos.data.local.TransactionWithLines
import com.example.entertainment.pos.data.remote.PosApi
import com.example.entertainment.pos.data.remote.PosErrorResponse
import com.example.entertainment.pos.data.remote.TransactionItemDto
import com.example.entertainment.pos.data.remote.TransactionRequest
import com.example.entertainment.pos.domain.PaymentProcessor
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.PaymentStatus
import com.example.entertainment.pos.domain.model.SyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Pushes every PENDING sale to the backend. Runs only once the device is online.
 *
 * Per sale: forward the offline card auth first (if any), then post the transaction.
 * Retry only what time can fix (IO, 5xx). A 409 means the backend said no on business
 * rules -- usually stock that sold elsewhere while we were offline -- so the row goes
 * to CONFLICT for an associate to resolve instead of looping through backoff forever.
 */
@HiltWorker
class PosSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: TransactionDao,
    private val api: PosApi,
    private val payments: PaymentProcessor,
) : CoroutineWorker(context, params) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        var shouldRetry = false

        for (sale in dao.getByStatus(SyncStatus.PENDING)) {
            val id = sale.header.id
            log(id, "SYNC_ATTEMPT", "run ${runAttemptCount + 1}")

            val paymentReference = when (val forward = forwardOfflinePayment(sale)) {
                Forward.Retry -> { shouldRetry = true; continue }
                Forward.Declined -> continue
                is Forward.Done -> forward.paymentReference
            }

            try {
                val response = api.postTransaction(sale.header.idempotencyKey, sale.toRequest(paymentReference))
                dao.markSynced(id, response.reference)
                log(id, "SYNCED", "${response.reference} (${response.status})")
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                shouldRetry = true
                log(id, "SYNC_RETRYABLE", e.message ?: "IO error")
            } catch (e: HttpException) {
                if (e.code() >= 500) {
                    shouldRetry = true
                    log(id, "SYNC_RETRYABLE", "HTTP ${e.code()}")
                } else {
                    val error = e.parseError()
                    val status = if (e.code() == 409) SyncStatus.CONFLICT else SyncStatus.FAILED
                    val reason = error?.error ?: "HTTP_${e.code()}"
                    dao.markRejected(id, status, reason, error?.conflictingBarcode)
                    log(id, status.name, reason)
                }
            }
        }

        return when {
            !shouldRetry -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            /* Rows stay PENDING; the next checkout or app open schedules another pass. */
            else -> Result.failure()
        }
    }

    private sealed interface Forward {
        data class Done(val paymentReference: String?) : Forward
        data object Retry : Forward
        data object Declined : Forward
    }

    private suspend fun forwardOfflinePayment(sale: TransactionWithLines): Forward {
        val header = sale.header
        val token = header.offlinePaymentToken
        if (header.paymentStatus != PaymentStatus.STORED_OFFLINE || token == null) {
            return Forward.Done(header.paymentReference)
        }

        return when (val result = payments.forwardOffline(header.id, "${header.idempotencyKey}:pay", header.totalCents, token)) {
            is PaymentResult.Approved -> {
                dao.markOfflinePaymentForwarded(header.id, result.reference.orEmpty())
                log(header.id, "OFFLINE_PAYMENT_FORWARDED", result.reference)
                Forward.Done(result.reference)
            }
            /* Customer's gone with the goods and the card bounced -- merchant eats it.
               Why real terminals cap offline amounts. */
            is PaymentResult.Declined -> {
                dao.setPaymentStatus(header.id, PaymentStatus.DECLINED)
                dao.markRejected(header.id, SyncStatus.FAILED, "OFFLINE_PAYMENT_DECLINED: ${result.reason}", null)
                log(header.id, "OFFLINE_PAYMENT_DECLINED", result.reason)
                Forward.Declined
            }
            is PaymentResult.Error -> {
                log(header.id, "SYNC_RETRYABLE", result.message)
                Forward.Retry
            }
        }
    }

    private suspend fun log(id: String, type: String, detail: String?) =
        dao.insertEvent(TransactionEventEntity(transactionId = id, type = type, detail = detail))

    private fun HttpException.parseError(): PosErrorResponse? = runCatching {
        json.decodeFromString<PosErrorResponse>(response()?.errorBody()?.string().orEmpty())
    }.getOrNull()

    private fun TransactionWithLines.toRequest(paymentReference: String?) = TransactionRequest(
        clientTransactionId = header.id,
        createdAtMillis = header.createdAtMillis,
        currency = "USD",
        totalCents = header.totalCents,
        items = lines.map { TransactionItemDto(it.barcode, it.name, it.unitPriceCents, it.quantity) },
        paymentMethod = header.paymentMethod.name,
        paymentReference = paymentReference,
        managerOverride = header.managerOverride,
    )

    companion object {
        const val UNIQUE_WORK_NAME = "pos_sync"
        private const val MAX_ATTEMPTS = 5
    }
}
