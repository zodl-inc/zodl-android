package co.electriccoin.zcash.ui.screen.send.model

import androidx.compose.runtime.saveable.mapSaver

sealed class SendStage {
    data object Form : SendStage()

    data object Proposing : SendStage()

    /** The transaction needs more notes than one transaction can spend on this device; nothing was created. */
    data object TooManyNotes : SendStage()

    data class SendFailure(
        val error: String
    ) : SendStage()

    companion object {
        private const val TYPE_FORM = "form" // $NON-NLS
        private const val TYPE_PROPOSING = "proposing" // $NON-NLS
        private const val TYPE_FAILURE = "failure" // $NON-NLS
        private const val TYPE_TOO_MANY_NOTES = "too_many_notes" // $NON-NLS
        private const val KEY_TYPE = "type" // $NON-NLS
        private const val KEY_ERROR = "error" // $NON-NLS

        internal val Saver
            get() =
                run {
                    mapSaver<SendStage>(
                        save = { it.toSaverMap() },
                        restore = {
                            if (it.isEmpty()) {
                                null
                            } else {
                                val sendStageString = (it[KEY_TYPE] as String)
                                when (sendStageString) {
                                    TYPE_FORM -> Form
                                    TYPE_PROPOSING -> Proposing
                                    TYPE_FAILURE -> SendFailure((it[KEY_ERROR] as String))
                                    TYPE_TOO_MANY_NOTES -> TooManyNotes
                                    else -> null
                                }
                            }
                        }
                    )
                }

        private fun SendStage.toSaverMap(): HashMap<String, String> {
            val saverMap = HashMap<String, String>()
            when (this) {
                Form -> {
                    saverMap[KEY_TYPE] = TYPE_FORM
                }

                Proposing -> {
                    saverMap[KEY_TYPE] = TYPE_PROPOSING
                }

                TooManyNotes -> {
                    saverMap[KEY_TYPE] = TYPE_TOO_MANY_NOTES
                }

                is SendFailure -> {
                    saverMap[KEY_TYPE] = TYPE_FAILURE
                    saverMap[KEY_ERROR] = this.error
                }
            }

            return saverMap
        }
    }
}
