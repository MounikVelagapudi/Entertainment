package com.example.entertainment.pos.domain

import com.example.entertainment.pos.domain.model.RefundResult
import com.example.entertainment.pos.fakes.FakePaymentProcessor
import com.example.entertainment.pos.fakes.FakePosRepository

class ConflictResolverBuilder {

    val repository = FakePosRepository()
    val payments = FakePaymentProcessor()

    fun withRefundResult(result: RefundResult) = apply { payments.nextRefund = result }

    fun build() = ConflictResolver(repository, payments)
}
