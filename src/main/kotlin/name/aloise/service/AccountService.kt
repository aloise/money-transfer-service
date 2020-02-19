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
    suspend fun create(newAccount: CreateAccount): AccountWithBalance
    suspend fun get(accountId: Int): AccountWithBalance?
    suspend fun remove(accountId: Int): Boolean
    /**
     * A way to atomically update balances
     * @param accountIds a list of Accounts IDs to update
     * @param balanceUpdates - Function which maps existing balance to a new one, updated would not be performed if the Map would be empty, otherwise it updates all listed accounts ids
     * @return Boolean - new balance if updated, old otherwise or null if accountId was not found
     */
    suspend fun updateBalances(
            accountIds: List<Int>,
            balanceUpdates: (Map<Int, AccountBalance>) -> Map<Int, AccountBalance>
    ): Map<Int, AccountBalance>
}

class InMemoryAccountService : AccountService {
    data class AccountWithMutex(val account: AccountWithBalance, val mutex: Mutex)

    private val accounts = ConcurrentHashMap<Int, AccountWithMutex>()
    private val counter = AtomicInteger(0)
    private val mutex: Mutex = Mutex()

    override suspend fun create(newAccount: CreateAccount): AccountWithBalance {
        val nextId = counter.incrementAndGet()
        val account = Account(nextId, newAccount.name)
        val balance = AccountBalance(newAccount.centAmount)
        val accountWithBalance = AccountWithBalance(account, balance)
        accounts[nextId] = AccountWithMutex(accountWithBalance, Mutex())
        return accountWithBalance
    }

    override suspend fun get(accountId: Int): AccountWithBalance? = accounts[accountId]?.account

    override suspend fun remove(accountId: Int): Boolean = mutex.withLock {
        accounts[accountId]?.let {
            it.mutex.withLock {
                accounts.remove(accountId)
            }
        } != null
    }

    override suspend fun updateBalances(accountIds: List<Int>, balanceUpdates: (Map<Int, AccountBalance>) -> Map<Int, AccountBalance>): Map<Int, AccountBalance> {
        // account Ids are sorted to avoid the deadlock
        // Atomically lock all items
        val itemsLocked = lockAccounts(accountIds)

        val items = itemsLocked.map { it.account.account.id to it.account.balance }.toMap()

        val balancesToUpdate = balanceUpdates(items)

        val updatedBalances = updateBalances(balancesToUpdate, items)

        // Atomically unlock all items
        unlockAccounts(itemsLocked)

        return updatedBalances.map { it.account.id to it.balance }.toMap()
    }

    private suspend fun unlockAccounts(itemsLocked: List<AccountWithMutex>) {
        mutex.withLock {
            itemsLocked.sortedBy { it.account.account.id }.reversed().forEach { it.mutex.unlock(null) }
        }
    }

    private suspend fun lockAccounts(accountsIds: List<Int>): List<AccountWithMutex> {
        return mutex.withLock {
            accountsIds.sorted().map { id ->
                val acc = accounts[id]
                try {
                    acc?.mutex?.lock(null)
                    acc
                } catch (_: Exception) {
                    null
                }
            }
        }.filterNotNull()
    }

    private fun updateBalances(balancesToUpdate: Map<Int, AccountBalance>, items: Map<Int, AccountBalance>): List<AccountWithBalance> {
        return if (balancesToUpdate == items) {
            // Nothing to update
            listOf()
        } else {
            balancesToUpdate.map { (id, newBalance) ->
                try {
                    accounts.computeIfPresent(id) { _, accWithMutex ->
                        accWithMutex.copy(account = accWithMutex.account.copy(balance = newBalance))
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }.filterNotNull().map { it.account }
    }

}