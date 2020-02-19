package name.aloise.service

import mu.KotlinLogging
import name.aloise.repository.AccountBalance
import name.aloise.utils.generic.fold
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
        val status: TransactionStatus,
        val createdAt: Long // Unix timestamp in millis
)

sealed class TransactionValidationException(msg: String) : Exception(msg) {
    class InvalidTransactionAmount() : TransactionValidationException("Invalid Transaction Amount")
    class TransactionFromAndToAccountIdsAreEqual() : TransactionValidationException("Transaction From And To AccountIds Are Equal")
}

interface TransactionService {
    @Throws(TransactionValidationException::class)
    suspend fun create(fromAccountId: Int, toAccountId: Int, transferCentAmount: Int): Transaction

    suspend fun get(transactionId: Int): Transaction?

    // TODO - This function should stream results (rx stream)
    suspend fun list(): List<Transaction>
}

class InMemoryTransactionService(private val accountService: AccountService) : TransactionService {
    private val logger = KotlinLogging.logger {}
    private val nextId = AtomicInteger(0)
    private val transactions = ConcurrentHashMap<Int, Transaction>()

    @Throws(TransactionValidationException::class)
    override suspend fun create(fromAccountId: Int, toAccountId: Int, transferCentAmount: Int): Transaction {
        if (transferCentAmount <= 0) throw TransactionValidationException.InvalidTransactionAmount()
        if (fromAccountId == toAccountId) throw TransactionValidationException.TransactionFromAndToAccountIdsAreEqual()

        val nextTransactionId = nextId.incrementAndGet()
        val transaction = Transaction(
                nextTransactionId,
                fromAccountId,
                toAccountId,
                transferCentAmount,
                TransactionStatus.PENDING,
                System.currentTimeMillis()
        )

        val updatedBalances = accountService.updateBalances(listOf(fromAccountId, toAccountId)) { accounts ->
            val from = accounts[fromAccountId]
            val to = accounts[toAccountId]
            if (from != null && to != null) {
                from.withdraw(transferCentAmount, nextTransactionId).fold({ newFromBalance ->
                    val newToBalance = to.deposit(transferCentAmount, nextTransactionId)
                    mapOf(
                            fromAccountId to newFromBalance,
                            toAccountId to newToBalance
                    )
                }, {
                    // failed to withdraw -> responding with old accounts data
                    accounts
                })

            } else {
                // failed to withdraw -> responding with old accounts data
                accounts
            }
        }
        val finalTransaction =
                when {
                    (updatedBalances[fromAccountId]?.lastTransactionId == nextTransactionId) &&
                            (updatedBalances[toAccountId]?.lastTransactionId == nextTransactionId) ->
                        // both accounts were updated
                        transaction.copy(status = TransactionStatus.SUCCESS)
                    else ->
                        // withdraw operation failed, the exact reason might be found by inspecting updatedBalances
                        processFailedTransaction(transaction, updatedBalances)
                }

        transactions[finalTransaction.id] = finalTransaction

        return finalTransaction
    }

    private fun processFailedTransaction(transaction: Transaction, updatedBalances: Map<Int, AccountBalance>): Transaction {
        if (updatedBalances.containsKey(transaction.fromAccountId)) {
            logger.error { "Source account was not found for transaction $transaction" }
        } else if (updatedBalances[transaction.fromAccountId]?.lastTransactionId != transaction.id) {
            logger.error { "Withdraw transaction failed for transaction $transaction" }
        }

        if (updatedBalances.containsKey(transaction.toAccountId)) {
            logger.error { "Target account was not found in transaction $transaction" }
        } else if (updatedBalances[transaction.toAccountId]?.lastTransactionId != transaction.id) {
            logger.error { "Deposit transaction failed for transaction $transaction" }
        }

        return transaction.copy(status = TransactionStatus.FAILED)
    }

    override suspend fun get(transactionId: Int): Transaction? = transactions[transactionId]
    override suspend fun list(): List<Transaction> = transactions.values.toList().sortedBy { it.id }

}



