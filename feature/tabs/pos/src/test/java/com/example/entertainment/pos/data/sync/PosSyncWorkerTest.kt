package com.example.entertainment.pos.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.entertainment.pos.data.OfflineFirstPosRepository
import com.example.entertainment.pos.data.local.PosDatabase
import com.example.entertainment.pos.data.remote.MockBackend
import com.example.entertainment.pos.data.remote.MockBackend.Companion.HOT_DOG_BARCODE
import com.example.entertainment.pos.data.remote.MockPosInterceptor
import com.example.entertainment.pos.data.remote.PosApi
import com.example.entertainment.pos.data.remote.TransactionRequest
import com.example.entertainment.pos.domain.model.CartLine
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.PaymentResult
import com.example.entertainment.pos.domain.model.PaymentStatus
import com.example.entertainment.pos.domain.model.Product
import com.example.entertainment.pos.domain.model.SyncStatus
import com.example.entertainment.pos.fakes.FakePaymentProcessor
import com.example.entertainment.pos.fakes.RecordingInterceptor
import com.example.entertainment.pos.fakes.posApi
import io.mockk.mockk
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosSyncWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.transactionDao()
    private val repository = OfflineFirstPosRepository(dao, mockk(relaxed = true))
    private val backend = MockBackend(latencyMs = 0, failureRatePercent = 0, initialStock = mapOf(HOT_DOG_BARCODE to 1), defaultStock = 99)
    private val recorder = RecordingInterceptor()
    private val payments = FakePaymentProcessor()
    private var api: PosApi = posApi(recorder, MockPosInterceptor(backend, Random(7)))

    private val popcorn = Product("012345678905", "Popcorn (L)", 899)
    private val hotDog = Product(HOT_DOG_BARCODE, "Hot Dog", 599)

    @After
    fun tearDown() = db.close()

    private fun worker(attempt: Int = 0) = TestListenableWorkerBuilder<PosSyncWorker>(context)
        .setRunAttemptCount(attempt)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                PosSyncWorker(appContext, workerParameters, dao, api, payments)
        })
        .build()

    private suspend fun sale(
        product: Product = popcorn,
        qty: Int = 1,
        payment: PaymentResult.Approved = PaymentResult.Approved("pi_1", "4242"),
    ): String {
        val draft = repository.createDraft(listOf(CartLine(product, qty)), PaymentMethod.CARD_TERMINAL)
        repository.completePayment(draft.id, payment)
        // getByStatus orders by createdAtMillis; keep sales from sharing a millisecond
        Thread.sleep(2)
        return draft.id
    }

    private suspend fun offlineSale(product: Product = popcorn) =
        sale(product, payment = PaymentResult.Approved(null, "4242", storedOffline = true, offlineToken = "4242424242424242"))

    private suspend fun tx(id: String) = repository.observeTransaction(id).first()!!
    private suspend fun events(id: String) = repository.observeEvents(id).first().map { it.type }
    private fun transactionPosts() = recorder.requests.filter { it.path == "/v1/transactions" }

    @Test
    fun `given nothing pending then the worker succeeds without calling the api`() = runTest {
        assertEquals(Result.success(), worker().doWork())
        assertTrue(recorder.requests.isEmpty())
    }

    @Test
    fun `given a pending sale when the backend accepts then it is synced with the server reference`() = runTest {
        val id = sale()
        val key = repository.idempotencyKeyOf(id)!!

        assertEquals(Result.success(), worker().doWork())

        val saved = tx(id)
        assertEquals(SyncStatus.SYNCED, saved.status)
        assertEquals("TXN-" + key.takeLast(8).uppercase(), saved.serverReference)
        assertEquals(listOf("CREATED", "PAYMENT_APPROVED", "SYNC_ATTEMPT", "SYNCED"), events(id))
    }

    @Test
    fun `given a pending sale then the request carries the stored key and the sale snapshot`() = runTest {
        val id = sale(popcorn, qty = 2)

        worker().doWork()

        val sent = transactionPosts().single()
        assertEquals(repository.idempotencyKeyOf(id), sent.idempotencyKey)
        val body = Json.decodeFromString(TransactionRequest.serializer(), sent.body)
        assertEquals(id, body.clientTransactionId)
        assertEquals("USD", body.currency)
        assertEquals(1798L, body.totalCents)
        assertEquals("CARD_TERMINAL", body.paymentMethod)
        assertEquals("pi_1", body.paymentReference)
        assertEquals(popcorn.barcode, body.items.single().barcode)
        assertEquals(2, body.items.single().quantity)
        assertEquals(899L, body.items.single().unitPriceCents)
    }

    @Test
    fun `given drafts and already synced rows then only pending sales are posted`() = runTest {
        repository.createDraft(listOf(CartLine(popcorn, 1)), PaymentMethod.CASH)
        val synced = sale().also { dao.markSynced(it, "TXN-OLD") }
        val pending = sale()

        worker().doWork()

        assertEquals(listOf(pending), transactionPosts().map { Json.decodeFromString(TransactionRequest.serializer(), it.body).clientTransactionId })
        assertEquals("TXN-OLD", tx(synced).serverReference)
    }

    @Test
    fun `given the last hot dog sold twice offline then the first syncs and the second is a conflict`() = runTest {
        val first = sale(hotDog)
        val second = sale(hotDog)

        assertEquals(Result.success(), worker().doWork())

        assertEquals(SyncStatus.SYNCED, tx(first).status)
        val conflict = tx(second)
        assertEquals(SyncStatus.CONFLICT, conflict.status)
        assertEquals("INVENTORY_EXHAUSTED", conflict.statusReason)
        assertEquals(HOT_DOG_BARCODE, conflict.conflictBarcode)
        assertEquals("CONFLICT", events(second).last())
    }

    @Test
    fun `given a conflict then the worker does not retry and the next run leaves it alone`() = runTest {
        sale(hotDog)
        val second = sale(hotDog)
        worker().doWork()
        recorder.requests.clear()

        assertEquals(Result.success(), worker().doWork())

        assertTrue(recorder.requests.isEmpty())
        assertEquals(SyncStatus.CONFLICT, tx(second).status)
    }

    @Test
    fun `given another 4xx then the sale is failed with the backend error code`() = runTest {
        recorder.respond = { 422 to """{"error":"PRICE_MISMATCH"}""" }
        val id = sale()

        assertEquals(Result.success(), worker().doWork())

        assertEquals(SyncStatus.FAILED, tx(id).status)
        assertEquals("PRICE_MISMATCH", tx(id).statusReason)
        assertNull(tx(id).conflictBarcode)
    }

    @Test
    fun `given a 4xx with no readable body then the reason falls back to the http code`() = runTest {
        recorder.respond = { 400 to "" }
        val id = sale()

        worker().doWork()

        assertEquals(SyncStatus.FAILED, tx(id).status)
        assertEquals("HTTP_400", tx(id).statusReason)
    }

    @Test
    fun `given a 503 then the sale stays pending and the worker retries`() = runTest {
        backend.setFailureRate(100)
        val id = sale()

        assertEquals(Result.retry(), worker().doWork())

        assertEquals(SyncStatus.PENDING, tx(id).status)
        assertEquals("SYNC_RETRYABLE", events(id).last())
    }

    @Test
    fun `given an io error then the sale stays pending and the worker retries`() = runTest {
        recorder.throwIo = true
        val id = sale()

        assertEquals(Result.retry(), worker().doWork())
        assertEquals(SyncStatus.PENDING, tx(id).status)
    }

    @Test
    fun `given retryable errors on the fifth attempt then the worker gives up but keeps the row pending`() = runTest {
        recorder.throwIo = true
        val id = sale()

        assertEquals(Result.retry(), worker(attempt = 4).doWork())
        assertEquals(Result.failure(), worker(attempt = 5).doWork())
        assertEquals(SyncStatus.PENDING, tx(id).status)
    }

    @Test
    fun `given the attempt count then it is logged one based`() = runTest {
        val id = sale()
        worker(attempt = 2).doWork()
        assertEquals("run 3", repository.observeEvents(id).first().first { it.type == "SYNC_ATTEMPT" }.detail)
    }

    @Test
    fun `given one sale retries and one conflicts then both are handled in the same pass`() = runTest {
        val synced = sale(hotDog)
        val conflict = sale(hotDog)
        val flaky = sale(popcorn)
        recorder.respond = { if (popcorn.barcode in it.body) 503 to """{"error":"SERVICE_UNAVAILABLE"}""" else null }

        assertEquals(Result.retry(), worker().doWork())

        assertEquals(SyncStatus.SYNCED, tx(synced).status)
        assertEquals(SyncStatus.CONFLICT, tx(conflict).status)
        assertEquals(SyncStatus.PENDING, tx(flaky).status)
    }

    @Test
    fun `given the response is lost after the backend took the sale then the retry is a duplicate not a conflict`() = runTest {
        val id = sale(hotDog)
        recorder.dropNextResponse = true

        assertEquals(Result.retry(), worker().doWork())
        assertEquals(SyncStatus.PENDING, tx(id).status)
        assertEquals(0, backend.stockOf(HOT_DOG_BARCODE))

        assertEquals(Result.success(), worker(attempt = 1).doWork())

        assertEquals(SyncStatus.SYNCED, tx(id).status)
        assertEquals(0, backend.stockOf(HOT_DOG_BARCODE))
        val keys = transactionPosts().map { it.idempotencyKey }
        assertEquals(2, keys.size)
        assertEquals(1, keys.toSet().size)
        assertTrue(events(id).last() == "SYNCED")
        assertTrue(repository.observeEvents(id).first().last().detail!!.contains("DUPLICATE"))
    }

    @Test
    fun `given a manager override then the flag is sent and the backend lets stock go negative`() = runTest {
        sale(hotDog)
        val id = sale(hotDog)
        worker().doWork()
        repository.requeueWithManagerOverride(id)

        assertEquals(Result.success(), worker().doWork())

        assertEquals(SyncStatus.SYNCED, tx(id).status)
        assertTrue(Json.decodeFromString(TransactionRequest.serializer(), transactionPosts().last().body).managerOverride)
        assertEquals(-1, backend.stockOf(HOT_DOG_BARCODE))
    }

    @Test
    fun `given an offline card auth when forwarded ok then payment is approved and the sale posts with the new reference`() = runTest {
        payments.nextResult = PaymentResult.Approved("pi_fwd", "4242")
        val id = offlineSale()
        val key = repository.idempotencyKeyOf(id)!!

        assertEquals(Result.success(), worker().doWork())

        assertEquals(FakePaymentProcessor.Forward(id, "$key:pay", 899, "4242424242424242"), payments.forwards.single())
        val saved = tx(id)
        assertEquals(SyncStatus.SYNCED, saved.status)
        assertEquals(PaymentStatus.APPROVED, saved.paymentStatus)
        assertEquals("pi_fwd", saved.paymentReference)
        assertNull(dao.getByStatus(SyncStatus.SYNCED).single().header.offlinePaymentToken)
        assertEquals("pi_fwd", Json.decodeFromString(TransactionRequest.serializer(), transactionPosts().single().body).paymentReference)
        assertTrue("OFFLINE_PAYMENT_FORWARDED" in events(id))
    }

    @Test
    fun `given an offline card that declines when forwarded then the sale fails and is never posted`() = runTest {
        payments.nextResult = PaymentResult.Declined("insufficient_funds")
        val id = offlineSale()

        assertEquals(Result.success(), worker().doWork())

        val saved = tx(id)
        assertEquals(SyncStatus.FAILED, saved.status)
        assertEquals(PaymentStatus.DECLINED, saved.paymentStatus)
        assertEquals("OFFLINE_PAYMENT_DECLINED: insufficient_funds", saved.statusReason)
        assertTrue(transactionPosts().isEmpty())
    }

    @Test
    fun `given the forward errors then the sale stays stored offline and the worker retries`() = runTest {
        payments.nextResult = PaymentResult.Error("Network error, try again")
        val id = offlineSale()

        assertEquals(Result.retry(), worker().doWork())

        assertEquals(SyncStatus.PENDING, tx(id).status)
        assertEquals(PaymentStatus.STORED_OFFLINE, tx(id).paymentStatus)
        assertTrue(transactionPosts().isEmpty())
    }

    @Test
    fun `given an online card sale then nothing is forwarded`() = runTest {
        sale()
        worker().doWork()
        assertTrue(payments.forwards.isEmpty())
    }
}
