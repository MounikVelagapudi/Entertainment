package com.example.entertainment.pos.data.payment

import com.example.entertainment.pos.data.remote.MockPosInterceptor
import com.example.entertainment.pos.fakes.FakeNetworkMonitor
import com.example.entertainment.pos.fakes.RecordingInterceptor
import com.example.entertainment.pos.fakes.posApi
import com.example.entertainment.pos.testdata.aMockBackend
import kotlin.random.Random

/* Real processor -> Retrofit -> recorder -> mock POS server. The recorder sees every request
   and can fake errors before the mock server answers. */
class SimulatedPaymentProcessorBuilder {

    val backend = aMockBackend(initialStock = emptyMap())
    val recorder = RecordingInterceptor()
    val network = FakeNetworkMonitor()

    fun withNetworkDown() = apply { network.goOffline() }

    fun withIoFailure() = apply { recorder.throwIo = true }

    fun withHttpResponse(code: Int, body: String) = apply { recorder.respond = { code to body } }

    fun build() = SimulatedPaymentProcessor(posApi(recorder, MockPosInterceptor(backend, Random(1))), network)
}
