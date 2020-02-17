package name.aloise.service

import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class TransactionStatus {
    PENDING,
    FAILED,
    SUCCESS
    // TODO - Enumerate all possible failed states
}

data class Transaction(
        val id: Int,
        val fromAccountId: Int,
        val toAccountId: Int,
        val centAmount: Int,
        val status: TransactionStatus
)

sealed class TransactionValidationException(msg: String) : Exception(msg) {
    class InvalidTransactionAmount() : TransactionValidationException("Invalid Transaction Amount")
    class TransactionFromAndToAccountIdsAreEqual() : TransactionValidationException("Transaction From And To AccountIds Are Equal")
}

interface TransactionService {
    @Throws(TransactionValidationException::class)
    suspend fun create(fromAccountId: Int, toAccountId: Int, transferCentAmount: Int): Transaction

    suspend fun get(transactionId: Int): Transaction?
}

class GlobalMutexTransactionService(private val accountService: AccountService) : TransactionService {
    private val nextId = AtomicInteger(0)
    private val transactions = ConcurrentHashMap<Int, Transaction>()

    @Throws(TransactionValidationException::class)
    override suspend fun create(fromAccountId: Int, toAccountId: Int, transferCentAmount: Int): Transaction {
        if (transferCentAmount <= 0) throw TransactionValidationException.InvalidTransactionAmount()
        if (fromAccountId == toAccountId) throw TransactionValidationException.TransactionFromAndToAccountIdsAreEqual()

        val nextTransactionId = nextId.incrementAndGet()
        val transaction =
                Transaction(nextTransactionId, fromAccountId, toAccountId, transferCentAmount, TransactionStatus.PENDING)
        val finalTransaction =
                accountService.mutex.withLock(this) {
                    val fromAccountUpdatedBalance = accountService.updateBalance(fromAccountId) {
                        it.withdraw(transferCentAmount, nextTransactionId)
                    }
                    when {
                        (fromAccountUpdatedBalance == null) ->
                            // 'From' account was not found
                            transaction.copy(status = TransactionStatus.FAILED)
                        (fromAccountUpdatedBalance.lastTransactionId == nextTransactionId) ->
                            // transaction id was updated -> proceed with the deposit
                            processDeposit(fromAccountId, toAccountId, transferCentAmount, transaction)
                        else ->
                            // withdraw operation failed, transaction number was not updated
                            transaction.copy(status = TransactionStatus.FAILED)
                    }

                }
        transactions[finalTransaction.id] = finalTransaction

        return finalTransaction
    }

    override suspend fun get(transactionId: Int): Transaction? = transactions[transactionId]

    private suspend fun processDeposit(
            fromAccountId: Int,
            toAccountId: Int,
            transferCentAmount: Int,
            transaction: Transaction
    ): Transaction {
        val toAccountUpdatedBalance = accountService.updateBalance(toAccountId) {
            it.deposit(transferCentAmount, transaction.id)
        }
        return if (toAccountUpdatedBalance == null) {
            // toAccount was not found, rolling back transaction
            accountService.updateBalance(fromAccountId) {
                it.rollback(transferCentAmount)
            }
            transaction.copy(status = TransactionStatus.FAILED)
        } else {
            // We are done !
            transaction.copy(status = TransactionStatus.SUCCESS)
        }
    }
}



