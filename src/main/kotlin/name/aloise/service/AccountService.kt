package name.aloise.service

import name.aloise.repository.Account
import name.aloise.repository.AccountBalance
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class CreateAccount(val name: String, val centAmount: Int = 0)

interface AccountService {

    suspend fun create(newAccount: CreateAccount): Pair<Account, AccountBalance>
    suspend fun get(accountId: Int): Pair<Account, AccountBalance>?
    suspend fun remove(accountId: Int): Boolean
    suspend fun updateBalance(accountId: Int, balanceUpdateFn: (AccountBalance) -> AccountBalance): Boolean
}

class InMemoryAccountService : AccountService {
    private val accounts = ConcurrentHashMap<Int, Account>()
    private val balances = ConcurrentHashMap<Int, AccountBalance>()
    private val counter = AtomicInteger(0)

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

    override suspend fun remove(accountId: Int): Boolean =
        balances.remove(accountId) != null || accounts.remove(accountId) != null

    override suspend fun updateBalance(accountId: Int, balanceUpdateFn: (AccountBalance) -> AccountBalance): Boolean =
        balances.computeIfPresent(accountId) { _, balance -> balanceUpdateFn(balance) } != null

}