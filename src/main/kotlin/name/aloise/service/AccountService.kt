package name.aloise.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import name.aloise.repository.Account
import name.aloise.repository.AccountBalance
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class CreateAccount(val name: String, val centAmount: Int = 0)

interface AccountService {
    val mutex: Mutex

    suspend fun create(newAccount: CreateAccount): Pair<Account, AccountBalance>
    suspend fun get(accountId: Int): Pair<Account, AccountBalance>?
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
    private val accounts = ConcurrentHashMap<Int, Account>()
    private val balances = ConcurrentHashMap<Int, AccountBalance>()
    private val counter = AtomicInteger(0)
    override val mutex: Mutex = Mutex()

    override suspend fun create(newAccount: CreateAccount): Pair<Account, AccountBalance> {
        val nextId = counter.incrementAndGet()
        val account = Account(nextId, newAccount.name)
        val balance = AccountBalance(newAccount.centAmount)
        accounts[nextId] = account
        balances[nextId] = balance
        return Pair(account, balance)
    }

    override suspend fun get(accountId: Int): Pair<Account, AccountBalance>? =
        accounts[accountId]?.let { acc ->
            balances[accountId]?.let { balance -> Pair(acc, balance) }
        }

    override suspend fun remove(accountId: Int): Boolean = mutex.withLock {
        balances.remove(accountId) != null || accounts.remove(accountId) != null
    }

    override suspend fun updateBalance(
        accountId: Int,
        balanceUpdateIfNotNull: (AccountBalance) -> AccountBalance?
    ): AccountBalance? =
        balances.computeIfPresent(accountId) { _, balance ->
            balanceUpdateIfNotNull(balance) ?: balance
        }

}