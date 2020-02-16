package name.aloise.repository

data class Account(val id: Int, val name: String)
data class AccountBalance(val centAmount: Int, val transactionsIds: List<Int> = listOf()) {
    val lastTransactionId: Int? = if (transactionsIds.isNotEmpty()) transactionsIds.last() else null

    fun deposit(amt: Int, trxId: Int) = AccountBalance(centAmount + amt, transactionsIds + trxId)
    fun withdraw(amt: Int, trxId: Int): AccountBalance? =
        if (centAmount >= amt) AccountBalance(centAmount - amt, transactionsIds + trxId) else null

    fun rollback(amt: Int): AccountBalance =
        AccountBalance(amt + centAmount, transactionsIds.dropLast(1))
}
