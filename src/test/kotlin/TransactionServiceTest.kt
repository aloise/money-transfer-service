import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runBlockingTest
import name.aloise.service.*
import kotlin.test.*

@ExperimentalCoroutinesApi
class TransactionServiceTest {
    private fun getServices(): Pair<TransactionService, AccountService> {
        val accounts = InMemoryAccountService()
        return Pair(InMemoryTransactionService(accounts), accounts)
    }

    @Test
    fun testTransferFunds() = runBlockingTest {
        val (transactions, accounts) = getServices()

        val (acc1, _) = accounts.create(CreateAccount("test", 1000))
        val (acc2, _) = accounts.create(CreateAccount("test2", 1000))

        val trx = transactions.create(acc1.id, acc2.id, 500)

        assertEquals(500, trx.centAmount)
        assertEquals(acc1.id, trx.fromAccountId)
        assertEquals(acc2.id, trx.toAccountId)
        assertEquals(TransactionStatus.SUCCESS, trx.status)

        val (_, updatedAcc1) = assertNotNull(accounts.get(acc1.id))
        val (_, updatedAcc2) = assertNotNull(accounts.get(acc2.id))

        assertEquals(500, updatedAcc1.centAmount)
        assertEquals(1500, updatedAcc2.centAmount)
        assertEquals(trx.id, updatedAcc1.lastTransactionId)
        assertEquals(trx.id, updatedAcc2.lastTransactionId)

    }

    @Test
    fun testFailToTransferInsufficientFunds() = runBlockingTest {
        val (transactions, accounts) = getServices()

        val (acc1, _) = accounts.create(CreateAccount("test3", 1000))
        val (acc2, _) = accounts.create(CreateAccount("test4", 1000))

        val trx = transactions.create(acc1.id, acc2.id, 2500)

        assertEquals(2500, trx.centAmount)
        assertEquals(acc1.id, trx.fromAccountId)
        assertEquals(acc2.id, trx.toAccountId)
        assertEquals(TransactionStatus.FAILED, trx.status)

        val (_, updatedAcc1) = assertNotNull(accounts.get(acc1.id))
        val (_, updatedAcc2) = assertNotNull(accounts.get(acc2.id))

        assertEquals(1000, updatedAcc1.centAmount)
        assertEquals(1000, updatedAcc2.centAmount)
        assertNull(updatedAcc1.lastTransactionId)
        assertNull(updatedAcc2.lastTransactionId)

    }

    @Test
    fun testFailToTransferNegativeAmount() = runBlockingTest {
        val (transactions, accounts) = getServices()

        val (acc1, _) = accounts.create(CreateAccount("test3", 1000))
        val (acc2, _) = accounts.create(CreateAccount("test4", 1000))

        assertFailsWith(TransactionValidationException::class) { transactions.create(acc1.id, acc2.id, -100) }

        val (_, updatedAcc1) = assertNotNull(accounts.get(acc1.id))
        val (_, updatedAcc2) = assertNotNull(accounts.get(acc2.id))

        assertEquals(1000, updatedAcc1.centAmount)
        assertEquals(1000, updatedAcc2.centAmount)
        assertNull(updatedAcc1.lastTransactionId)
        assertNull(updatedAcc2.lastTransactionId)

    }

    @Test
    fun testFailToTransferFromDeletedAccount() = runBlockingTest {
        val (transactions, accounts) = getServices()

        val (acc1, _) = accounts.create(CreateAccount("test3", 1000))
        val (acc2, _) = accounts.create(CreateAccount("test4", 1000))
        accounts.remove(acc1.id)

        val trx = transactions.create(acc1.id, acc2.id, 500)

        assertEquals(TransactionStatus.FAILED, trx.status)

        val (_, updatedAcc2) = assertNotNull(accounts.get(acc2.id))

        assertEquals(1000, updatedAcc2.centAmount)
        assertNull(updatedAcc2.lastTransactionId)
    }

    @Test
    fun testFailToTransferToDeletedAccount() = runBlockingTest {
        val (transactions, accounts) = getServices()

        val (acc1, _) = accounts.create(CreateAccount("test3", 1000))
        val (acc2, _) = accounts.create(CreateAccount("test4", 1000))
        accounts.remove(acc2.id)

        val trx = transactions.create(acc1.id, acc2.id, 500)

        assertEquals(TransactionStatus.FAILED, trx.status)

        val (_, updatedAcc1) = assertNotNull(accounts.get(acc1.id))

        assertEquals(1000, updatedAcc1.centAmount)
        assertNull(updatedAcc1.lastTransactionId)
    }

    @Test
    fun transferCorrectlyInParallelMultipleTransaction() = runBlockingTest {
        val (transactions, accounts) = getServices()

        val (acc1, _) = accounts.create(CreateAccount("test3", 1000))
        val (acc2, _) = accounts.create(CreateAccount("test4", 1000))

        val num = 1005 // We are expecting to have first 1000 exceptions to be processed

        // running transactions concurrently
        val allTransactionsDeferred =
                (1..num).map {
                    async {
                        transactions.create(acc1.id, acc2.id, 1)
                    }
                }

        // waiting for results
        val allTransactions = allTransactionsDeferred.awaitAll()

        assertEquals(1000, allTransactions.filter { it.status == TransactionStatus.SUCCESS }.size)
        assertEquals(5, allTransactions.filter { it.status == TransactionStatus.FAILED }.size)

        val (_, updatedAcc1) = assertNotNull(accounts.get(acc1.id))
        val (_, updatedAcc2) = assertNotNull(accounts.get(acc2.id))

        assertEquals(0, updatedAcc1.centAmount)
        assertEquals(2000, updatedAcc2.centAmount)
    }

    @Test
    fun transferCorrectlyInParallelMultipleTransactionBetweenAccounts() = runBlockingTest {
        val (transactions, accounts) = getServices()

        val (acc1, acc1Balance) = accounts.create(CreateAccount("test3", 2000000))
        val (acc2, acc2Balance) = accounts.create(CreateAccount("test4", 1000000))

        val num = 2000 // We would run twice this amount of transactions

        // running transactions concurrently - we are making $num + 1 transactions one way and $num - 1 back
        val allTransactionsDeferred =
                (1..num * 2).map { i ->
                    async {
                        val (from, to) = if (i <= num + 1) Pair(acc1.id, acc2.id) else Pair(acc2.id, acc1.id)
                        transactions.create(from, to, 1)
                    }
                }

        // waiting for results
        val allTransactions = allTransactionsDeferred.awaitAll()

        assertEquals(num * 2, allTransactions.filter { it.status == TransactionStatus.SUCCESS }.size)
        assertEquals(0, allTransactions.filter { it.status == TransactionStatus.FAILED }.size)

        assertEquals(num + 1, allTransactions.filter { it.fromAccountId == acc1.id }.size)
        assertEquals(num - 1, allTransactions.filter { it.fromAccountId == acc2.id }.size)

        assertEquals(num + 1, allTransactions.filter { it.fromAccountId == acc1.id }.map { it.centAmount }.sum())
        assertEquals(num - 1, allTransactions.filter { it.fromAccountId == acc2.id }.map { it.centAmount }.sum())


        val (_, updatedAcc1) = assertNotNull(accounts.get(acc1.id))
        val (_, updatedAcc2) = assertNotNull(accounts.get(acc2.id))

        assertEquals(acc1Balance.centAmount - 2, updatedAcc1.centAmount)
        assertEquals(acc2Balance.centAmount + 2, updatedAcc2.centAmount)
    }

    @Test
    fun transferCorrectlyInParallelMultipleTransactionBetweenAccountsAndAccountRemoval() = runBlockingTest {
        val (transactions, accounts) = getServices()

        val (acc1, acc1Balance) = accounts.create(CreateAccount("test3", 2000000))
        val (acc2, _) = accounts.create(CreateAccount("test4", 1000000))

        val num = 50 // We would run twice this amount of transactions

        // running transactions - we are making (num - 1) transactions and removing the destination account afterwards
        val allTransactions =
                (1..num * 2).map { i ->
                    val (from, to) = if (i <= num + 1) Pair(acc1.id, acc2.id) else Pair(acc2.id, acc1.id)
                    if (i == num) accounts.remove(to)
                    transactions.create(from, to, 1)
                }

        assertEquals(num - 1, allTransactions.filter { it.status == TransactionStatus.SUCCESS }.size)
        assertEquals(num + 1, allTransactions.filter { it.status == TransactionStatus.FAILED }.size)

        assertEquals(num + 1, allTransactions.filter { it.fromAccountId == acc1.id }.size)
        assertEquals(num - 1, allTransactions.filter { it.fromAccountId == acc2.id }.size)

        assertEquals(num + 1, allTransactions.filter { it.fromAccountId == acc1.id }.map { it.centAmount }.sum())
        assertEquals(num - 1, allTransactions.filter { it.fromAccountId == acc2.id }.map { it.centAmount }.sum())


        val (_, updatedAcc1) = assertNotNull(accounts.get(acc1.id))

        assertEquals(acc1Balance.centAmount - (num - 1), updatedAcc1.centAmount)
    }

    // TODO - property tests and additional parallelism tests

}