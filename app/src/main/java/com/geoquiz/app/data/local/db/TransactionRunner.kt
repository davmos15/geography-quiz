package com.geoquiz.app.data.local.db

import androidx.room.withTransaction
import javax.inject.Inject

/** Runs a block of database work atomically. Abstracted so callers can be unit tested on the JVM. */
interface TransactionRunner {
    suspend operator fun <R> invoke(block: suspend () -> R): R
}

class RoomTransactionRunner @Inject constructor(
    private val database: AppDatabase
) : TransactionRunner {
    override suspend fun <R> invoke(block: suspend () -> R): R = database.withTransaction(block)
}
