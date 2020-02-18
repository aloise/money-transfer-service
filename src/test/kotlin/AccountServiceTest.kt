import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import name.aloise.service.AccountService
import name.aloise.service.CreateAccount
import name.aloise.service.InMemoryAccountService
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
class AccountServiceTest {

    private fun getService(): AccountService = InMemoryAccountService()

    @Test
    fun testCreateAccount() = runBlockingTest {
        val service = getService()
        val accountName = "test" + Random.Default.nextInt()
        val result = service.create(CreateAccount(accountName, 50))
        assertEquals(result.account.name, accountName)
    }

    @Test
    fun testGetAccount() = runBlockingTest {
        val service = getService()
        val accountName = "test" + Random.Default.nextInt()
        val result = service.create(CreateAccount(accountName, 50))
        val getResult = service.get(result.account.id)
        assertEquals(result, getResult)
    }

    @Test
    fun testRemoveAccount() = runBlockingTest {
        val service = getService()
        val accountName = "test" + Random.Default.nextInt()
        val result = service.create(CreateAccount(accountName, 50))
        val removeResult = service.remove(result.account.id)
        assert(removeResult)
        val getResult = service.get(result.account.id)
        assertNull(getResult)
    }
}
