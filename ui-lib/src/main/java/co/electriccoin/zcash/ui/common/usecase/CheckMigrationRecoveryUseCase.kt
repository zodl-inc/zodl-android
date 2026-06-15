package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import cash.z.ecc.android.sdk.OrchardMigrationSdk
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.work.MigrationScheduler
import kotlin.time.Duration.Companion.seconds

class CheckMigrationRecoveryUseCase(
    private val sdk: OrchardMigrationSdk,
    private val context: Context,
) {
    suspend operator fun invoke() {
        if (sdk.hasOverdueTransfers()) {
            Twig.debug { "MigrationRecovery: overdue transfer detected — scheduling Worker immediately." }
            MigrationScheduler(context).schedule(0.seconds)
        }
    }
}
