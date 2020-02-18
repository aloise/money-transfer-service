package name.aloise.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import name.aloise.repository.Account
import name.aloise.repository.AccountBalance
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class CreateAccount(val name: String, val centAmount: Int = 0)
data class AccountWithBalance(val account: Account, val balance: AccountBalance)

interface AccountService {
    val mutex: Mutex

    suspend fun create(newAccount: CreateAccount): AccountWithBalance
    suspend fun get(accountId: Int): AccountWithBalance?
    suspend fun remove(accountId: Int): Boolean
    /**
     * A way to atomically update balance
     * @param accountId Account ID
     * @param balanceUpdateIfNotNull - Function which maps existing balance to a new one, updated would not be performed if it returns false
     * @return Boolean - new balance if updated, old otherwise or null if accountId was not found
     */
    suspend fun updateBalance(
            accountId: Int,
            balanceUpdateIfNotNull: (AccountBalance) -> AccountBalance?
    ): AccountBalance?
}

class InMemoryAccountService : AccountService {
    private val accounts = ConcurrentHashMap<Int, AccountWithBalance>()
    private val counter = AtomicInteger(0)
    override val mutex: Mutex = Mutex()

    override suspend fun create(newAccount: CreateAccount): AccountWithBalance {
        val nextId = counter.incrementAndGet()
        val account = Account(nextId, newAccount.name)
        val balance = AccountBalance(newAccount.centAmount)
        val accountWithBalance = AccountWithBalance(account, balance)
        accounts[nextId] = accountWithBalance
        return accountWithBalance
    }

    override suspend fun get(accountId: Int): AccountWithBalance? = accounts[accountId]

    override suspend fun remove(accountId: Int): Boolean = mutex.withLock {
        accounts.remove(accountId) != null
    }

    override suspend fun updateBalance(
            accountId: Int,
            balanceUpdateIfNotNull: (AccountBalance) -> AccountBalance?
    ): AccountBalance? =
            accounts.computeIfPresent(accountId) { _, accountWithBalance ->
                val updatedBalance = balanceUpdateIfNotNull(accountWithBalance.balance)
                if (updatedBalance == null)
                    accountWithBalance
                else
                    accountWithBalance.copy(balance = updatedBalance)
            }?.balance

}