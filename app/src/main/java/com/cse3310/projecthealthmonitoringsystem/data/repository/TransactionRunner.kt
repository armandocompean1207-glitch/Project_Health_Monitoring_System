package com.cse3310.projecthealthmonitoringsystem.data.repository

import androidx.room.withTransaction
import com.cse3310.projecthealthmonitoringsystem.data.AppDatabase

/** Runs a block in one database transaction, so multi-step writes across DAOs succeed or fail together. */
interface TransactionRunner {
    suspend fun <R> run(block: suspend () -> R): R
}

class RoomTransactionRunner(private val db: AppDatabase) : TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R = db.withTransaction { block() }
}

/** Current time in epoch ms. Repositories take one so tests can control time. */
typealias Clock = () -> Long

val SystemClock: Clock = { System.currentTimeMillis() }
