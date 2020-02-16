package name.aloise.service

interface TransactionService {
    suspend fun create(fromAccountId: Int, toAccountId: Int, centAmount: Int)
}