package co.electriccoin.zcash.ui.screen.exportvk.detail

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.SegmentedControlItem
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

internal data class VKDetailState(
    val title: StringResource,
    val subtitle: StringResource,
    val tabs: List<SegmentedControlItem>,
    val content: VKContentState,
    val disclaimer: StringResource,
    val secondaryButton: ButtonState,
    val primaryButton: ButtonState,
    val onBack: () -> Unit,
) {
    companion object {
        val preview =
            preview(
                content =
                    VKContentState.Qr(
                        data = PREVIEW_KEY,
                        contentDescription = stringRes("Hidden viewing key"),
                        hiddenLabel = stringRes("Reveal key"),
                        isRevealed = false
                    )
            )

        val previewRevealedQr =
            preview(
                content =
                    VKContentState.Qr(
                        data = PREVIEW_KEY,
                        contentDescription = stringRes("Hidden viewing key"),
                        hiddenLabel = stringRes("Reveal key"),
                        isRevealed = true
                    )
            )

        val previewHiddenKey =
            preview(
                content =
                    VKContentState.Key(
                        data = PREVIEW_KEY,
                        contentDescription = stringRes("Hidden viewing key"),
                        hiddenLabel = stringRes("Reveal key"),
                        isRevealed = false
                    ),
                selectedTab = 1
            )

        val previewRevealedKey =
            preview(
                content =
                    VKContentState.Key(
                        data = PREVIEW_KEY,
                        contentDescription = stringRes("Hidden viewing key"),
                        hiddenLabel = stringRes("Reveal key"),
                        isRevealed = true
                    ),
                selectedTab = 1
            )

        private fun preview(
            content: VKContentState,
            selectedTab: Int = 0,
        ) = VKDetailState(
            title = stringRes("Your Full Viewing Key"),
            subtitle = stringRes("Full visibility. Handle with care."),
            tabs =
                listOf(
                    SegmentedControlItem(text = stringRes("QR Code"), isSelected = selectedTab == 0, onClick = {}),
                    SegmentedControlItem(text = stringRes("Key String"), isSelected = selectedTab == 1, onClick = {}),
                ),
            content = content,
            disclaimer = stringRes("Only scan in a trusted, private environment"),
            secondaryButton =
                ButtonState(
                    text = stringRes("Share"),
                    icon = R.drawable.ic_share,
                    isEnabled = content.isRevealed
                ),
            primaryButton =
                ButtonState(
                    text = stringRes(if (content.isRevealed) "Hide key" else "Reveal key"),
                    icon = if (content.isRevealed) R.drawable.ic_seed_hide else R.drawable.ic_seed_show
                ),
            onBack = {}
        )
    }
}

internal sealed interface VKContentState {
    val data: String
    val contentDescription: StringResource
    val hiddenLabel: StringResource
    val isRevealed: Boolean

    data class Qr(
        override val data: String,
        override val contentDescription: StringResource,
        override val hiddenLabel: StringResource,
        override val isRevealed: Boolean
    ) : VKContentState

    data class Key(
        override val data: String,
        override val contentDescription: StringResource,
        override val hiddenLabel: StringResource,
        override val isRevealed: Boolean,
    ) : VKContentState
}

private const val PREVIEW_KEY =
    "uview1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq" +
        "qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"
