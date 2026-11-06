package com.example.entertainment.pos.testdata

import com.example.entertainment.pos.data.local.TransactionEntity
import com.example.entertainment.pos.domain.model.PaymentMethod
import com.example.entertainment.pos.domain.model.SyncStatus

/* Room header row, for inserting straight through the DAO. */
fun aTransactionEntity(
    id: String,
    createdAtMillis: Long = 0,
    status: SyncStatus = SyncStatus.PENDING,
    totalCents: Long = 100,
    paymentMethod: PaymentMethod = PaymentMethod.CASH,
) = TransactionEntity(
    id,
    "k-$id",
    totalCents,
    createdAtMillis,
    status,
    paymentMethod = paymentMethod,
)
