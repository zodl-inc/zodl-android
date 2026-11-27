package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.fixture.AccountFixture
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class FindAccountByKeyIdTest {
    private fun account(uuid: UUID): WalletAccount =
        mockk(relaxed = true) {
            every { sdkAccount } returns AccountFixture.new(accountUuid = uuid)
        }

    @Test
    fun findsAccountWhoseStorageKeyIdMatches() {
        val a = account(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"))
        val b = account(UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100"))
        val keyA = a.sdkAccount.accountUuid.toStorageKeyId()
        assertSame(a, listOf(a, b).findByAccountKeyId(keyA))
    }

    @Test
    fun returnsNullWhenNoAccountMatches() {
        val a = account(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"))
        assertNull(listOf(a).findByAccountKeyId("no-such-key"))
    }
}
