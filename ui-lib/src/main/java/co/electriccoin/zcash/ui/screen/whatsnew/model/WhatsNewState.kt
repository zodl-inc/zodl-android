package co.electriccoin.zcash.ui.screen.whatsnew.model

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.Changelog
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.datetime.LocalDate

data class WhatsNewState(
    val titleVersion: StringResource,
    val bottomVersion: StringResource,
    val date: LocalDate,
    val onVersionLongClick: () -> Unit,
    val sections: List<WhatsNewSectionState>
) {
    companion object {
        fun new(
            changelog: Changelog,
            version: String,
            onVersionLongClick: () -> Unit
        ) = WhatsNewState(
            titleVersion = stringRes(R.string.whatsNew_version, changelog.version),
            bottomVersion = stringRes(R.string.settings_version, version),
            date = changelog.date,
            sections =
                listOfNotNull(changelog.added, changelog.changed, changelog.fixed, changelog.removed)
                    .map {
                        WhatsNewSectionState(
                            stringRes(value = it.title),
                            stringRes(it.content)
                        )
                    },
            onVersionLongClick = onVersionLongClick
        )
    }
}

data class WhatsNewSectionState(
    val title: StringResource,
    val content: StringResource
)
