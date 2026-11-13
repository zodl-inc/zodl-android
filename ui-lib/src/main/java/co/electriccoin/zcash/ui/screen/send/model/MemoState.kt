package co.electriccoin.zcash.ui.screen.send.model

import androidx.compose.runtime.saveable.mapSaver
import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.common.model.ProposalMemoPolicy
import co.electriccoin.zcash.ui.common.model.isTransparentOrTex

sealed class MemoState(
    open val text: String,
    open val byteSize: Int
) {
    /**
     * The memo text to send to a recipient of [recipientType]. A transparent or TEX recipient gets none, even when
     * text was typed while the recipient was still a shielded one and the memo field is now hidden.
     */
    fun textToSend(recipientType: AddressType?): String =
        ProposalMemoPolicy.memoFor(recipientType, text).orEmpty()

    /** Whether this memo lets the form be sent: it matters only for a recipient that can receive one. */
    fun allowsSendTo(recipientType: AddressType?): Boolean = recipientType.isTransparentOrTex || this is Correct

    data class Correct(
        override val text: String,
        override val byteSize: Int
    ) : MemoState(text, byteSize)

    data class TooLong(
        override val text: String,
        override val byteSize: Int
    ) : MemoState(text, byteSize)

    companion object {
        fun new(memo: String): MemoState {
            val bytesCount = Memo.countLength(memo)
            return if (bytesCount > Memo.MAX_MEMO_LENGTH_BYTES) {
                TooLong(memo, bytesCount)
            } else {
                Correct(memo, bytesCount)
            }
        }

        private const val TYPE_CORRECT = "correct" // $NON-NLS
        private const val TYPE_TOO_LONG = "too_long" // $NON-NLS
        private const val KEY_TYPE = "type" // $NON-NLS
        private const val KEY_TEXT = "text" // $NON-NLS
        private const val KEY_LENGTH = "length" // $NON-NLS

        internal val Saver
            get() =
                run {
                    mapSaver<MemoState>(
                        save = { it.toSaverMap() },
                        restore = {
                            if (it.isEmpty()) {
                                null
                            } else {
                                val text = (it[KEY_TEXT] as String)
                                val length = (it[KEY_LENGTH] as Int)
                                val type = (it[KEY_TYPE] as String)
                                when (type) {
                                    TYPE_CORRECT -> Correct(text, length)
                                    TYPE_TOO_LONG -> TooLong(text, length)
                                    else -> null
                                }
                            }
                        }
                    )
                }

        private fun MemoState.toSaverMap(): HashMap<String, Any> {
            val saverMap = HashMap<String, Any>()
            when (this) {
                is Correct -> saverMap[KEY_TYPE] = TYPE_CORRECT
                is TooLong -> saverMap[KEY_TYPE] = TYPE_TOO_LONG
            }
            saverMap[KEY_TEXT] = this.text
            saverMap[KEY_LENGTH] = this.byteSize

            return saverMap
        }
    }
}
