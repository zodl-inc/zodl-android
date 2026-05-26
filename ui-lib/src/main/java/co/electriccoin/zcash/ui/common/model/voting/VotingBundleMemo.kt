package co.electriccoin.zcash.ui.common.model.voting

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

private const val BALLOT_DIVISOR_ZATOSHI = 12_500_000L
private const val BUNDLE_NOTE_LIMIT = 5
private const val ZATOSHI_PER_ZEC = 100_000_000L

internal fun votingBundleRawWeights(notesJson: String): List<Long> {
    val notes = notesJson.toBundleNotes()
    if (notes.isEmpty()) {
        return emptyList()
    }

    val sortedNotes =
        notes.sortedWith(
            compareByDescending<VotingBundleNote> { it.value }
                .thenBy { it.position }
        )

    val bundles = mutableListOf<MutableList<VotingBundleNote>>()
    for (note in sortedNotes) {
        if (bundles.isEmpty() || bundles.last().size >= BUNDLE_NOTE_LIMIT) {
            bundles += mutableListOf<VotingBundleNote>()
        }
        bundles.last() += note
    }

    return bundles
        .map { bundle ->
            val positionSortedBundle = bundle.sortedBy { it.position }
            VotingRawBundle(
                notes = positionSortedBundle,
                total = positionSortedBundle.sumOf { it.value }
            )
        }.filter { bundle ->
            bundle.total >= BALLOT_DIVISOR_ZATOSHI
        }.sortedWith(
            compareByDescending<VotingRawBundle> { it.total }
                .thenBy { it.notes.firstOrNull()?.position ?: Long.MAX_VALUE }
        ).map { bundle ->
            bundle.total
        }
}

internal fun Long.toVotingRawZecLabel(): String {
    val whole = this / ZATOSHI_PER_ZEC
    val fractional = (this % ZATOSHI_PER_ZEC).toString().padStart(8, '0')
    return "$whole.$fractional ZEC"
}

private fun String.toBundleNotes() =
    buildList {
        val notes = Json.parseToJsonElement(this@toBundleNotes).jsonArray
        for (noteElement in notes) {
            val note = noteElement.jsonObject
            add(
                VotingBundleNote(
                    value = note.getValue("value").jsonPrimitive.long,
                    position = note.getValue("position").jsonPrimitive.long
                )
            )
        }
    }

private data class VotingBundleNote(
    val value: Long,
    val position: Long
)

private data class VotingRawBundle(
    val notes: List<VotingBundleNote>,
    val total: Long
)
