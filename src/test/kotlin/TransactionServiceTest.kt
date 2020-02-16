import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import name.aloise.service.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
class TransactionServiceTest {
    private fun getServices(): Pair<TransactionService, AccountService> {
        val accounts = InMemoryAccountService()
        return Pair(GlobalMutexTransactionService(accounts), accounts)
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

}