package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.GetVersionInfoProvider
import co.electriccoin.zcash.ui.common.repository.ReceiveTransaction
import co.electriccoin.zcash.ui.common.repository.SendTransaction
import co.electriccoin.zcash.ui.common.repository.ShieldTransaction
import co.electriccoin.zcash.ui.common.repository.TransactionRepository
import co.electriccoin.zcash.ui.design.util.TickerLocation.HIDDEN
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER
import co.electriccoin.zcash.ui.util.FileShareUtil
import co.electriccoin.zcash.ui.util.FileShareUtil.ZASHI_INTERNAL_DATA_MIME_TYPE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Year
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ExportTaxUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountDataSource: AccountDataSource,
    private val versionInfoProvider: GetVersionInfoProvider,
    private val context: Context,
    private val navigationRouter: NavigationRouter,
) {
    suspend operator fun invoke() =
        withContext(Dispatchers.IO) {
            val previousYear =
                Year
                    .now()
                    .minusYears(1)
                    .let {
                        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy")
                        it.format(formatter)
                    }

            val outputFile =
                File(
                    context.cacheDir,
                    when (accountDataSource.getSelectedAccount()) {
                        is KeystoneAccount -> "Keystone_Transaction_History_$previousYear.csv"
                        is ZashiAccount -> "Zodl_Transaction_History_$previousYear.csv"
                    }
                )

            writeCsvToFile(
                outputFile = outputFile,
                data = getCsvEntries()
            )

            runCatching {
                val intent =
                    FileShareUtil.newShareContentIntent(
                        context = context,
                        file = outputFile,
                        shareText = context.getString(R.string.export_data_share_text),
                        sharePickerText = context.getString(R.string.export_data_share_text),
                        versionInfo = versionInfoProvider(),
                        fileType = ZASHI_INTERNAL_DATA_MIME_TYPE
                    )

                context.startActivity(intent)
            }

            navigationRouter.back()
        }

    private fun writeCsvToFile(
        outputFile: File,
        data: List<CsvEntry>
    ) {
        outputFile.outputStream().bufferedWriter().use { writer ->
            writer.write(
                listOf(
                    context.getString(R.string.tax_export_date),
                    context.getString(R.string.tax_export_received_quantity),
                    context.getString(R.string.tax_export_received_currency),
                    context.getString(R.string.tax_export_sent_quantity),
                    context.getString(R.string.tax_export_sent_currency),
                    context.getString(R.string.tax_export_fee_amount),
                    context.getString(R.string.tax_export_fee_currency),
                    context.getString(R.string.tax_export_tag)
                ).joinToString(separator = CSV_SEPARATOR)
            )
            writer.newLine()

            data.forEach {
                writer.write(
                    listOf(
                        it.date,
                        it.receivedQuantity,
                        it.receivedCurrency,
                        it.sentQuantity,
                        it.sentCurrency,
                        it.feeAmount,
                        it.feeCurrency,
                        it.tag,
                    ).joinToString(separator = CSV_SEPARATOR)
                )
                writer.newLine()
            }

            writer.flush()
        }
    }

    private suspend fun getCsvEntries() =
        transactionRepository
            .getTransactions()
            .mapNotNull { transaction ->
                val previousYear = Year.now().minusYears(1)

                val date = transaction.timestamp?.atZone(ZoneId.of("UTC")) ?: return@mapNotNull null
                if (date.year != previousYear.value) return@mapNotNull null

                val dateString =
                    date.let {
                        val formatter =
                            DateTimeFormatter
                                .ofPattern("MM/dd/yyyy HH:mm:ss")
                                .withZone(ZoneOffset.UTC)

                        formatter.format(date)
                    } ?: return@mapNotNull null

                when (transaction) {
                    is SendTransaction.Success,
                    is SendTransaction.Pending -> {
                        val fee = transaction.fee

                        val sentQuantity =
                            if (fee != null) {
                                stringRes(transaction.amount - fee, HIDDEN)
                            } else {
                                stringRes(transaction.amount, HIDDEN)
                            }

                        CsvEntry(
                            date = dateString,
                            receivedQuantity = "",
                            receivedCurrency = "",
                            sentQuantity = sentQuantity.getString(context),
                            sentCurrency = CURRENCY_TICKER,
                            feeAmount = stringRes(fee ?: Zatoshi(0), HIDDEN).getString(context),
                            feeCurrency = CURRENCY_TICKER,
                            tag = ""
                        )
                    }

                    is SendTransaction.Failed -> null
                    is ReceiveTransaction.Success,
                    is ReceiveTransaction.Pending -> {
                        CsvEntry(
                            date = dateString,
                            receivedQuantity = stringRes(transaction.amount, HIDDEN).getString(context),
                            receivedCurrency = CURRENCY_TICKER,
                            sentQuantity = "",
                            sentCurrency = "",
                            feeAmount = "",
                            feeCurrency = "",
                            tag = ""
                        )
                    }

                    is ReceiveTransaction.Failed -> null
                    is ShieldTransaction.Success,
                    is ShieldTransaction.Pending,
                    is ShieldTransaction.Failed -> null
                }
            }
}

private data class CsvEntry(
    val date: String,
    val receivedQuantity: String,
    val receivedCurrency: String,
    val sentQuantity: String,
    val sentCurrency: String,
    val feeAmount: String,
    val feeCurrency: String,
    val tag: String,
)

private const val CSV_SEPARATOR = ","
