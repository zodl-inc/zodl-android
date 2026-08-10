package co.electriccoin.zcash.ui.common.model.voting

data class TxEventAttribute(
    val key: String,
    val value: String
)

data class TxEvent(
    val type: String,
    val attributes: List<TxEventAttribute>
) {
    fun attribute(key: String): String? =
        attributes.firstOrNull { attribute -> attribute.key == key }?.value
}

data class TxConfirmation(
    val height: Long,
    val code: Int,
    val log: String = "",
    val events: List<TxEvent> = emptyList()
) {
    fun event(type: String): TxEvent? =
        events.firstOrNull { event -> event.type == type }
}

data class TxResult(
    val txHash: String,
    val code: Int,
    val log: String = ""
)
