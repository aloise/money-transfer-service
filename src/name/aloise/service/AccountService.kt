package name.aloise.service

import name.aloise.repository.Account
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

interface AccountService {

    data class CreateAccount(val name: String)

    suspend fun create(newAccount: CreateAccount): Account
    suspend fun get(accountId: Int): Account?
    suspend fun remove(accountId: Int): Boolean
}

class InMemoryAccountService : AccountService {
    private val accounts = ConcurrentHashMap<Int, Account>()
    private val counter = AtomicInteger(0)

    override suspend fun create(newAccount: AccountService.CreateAccount): Account {
        val nextId = counter.incrementAndGet()
        val accountWithId = Account(nextId, newAccount.name)
        accounts[nextId] = accountWithId
        return accountWithId
    }

    override suspend fun get(accountId: Int): Account? = accounts[accountId]
    override suspend fun remove(accountId: Int): Boolean = accounts.remove(accountId) != null
}