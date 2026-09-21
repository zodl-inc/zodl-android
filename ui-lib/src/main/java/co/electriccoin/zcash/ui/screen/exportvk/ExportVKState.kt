package co.electriccoin.zcash.ui.screen.exportvk

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.VKType
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.styledStringResource

internal data class ExportVKState(
    val logo: ImageResource,
    val description: StyledStringResource,
    val options: List<VKOptionState>,
    val continueButton: ButtonState,
    val onBack: () -> Unit,
) {
    companion object {
        val preview = preview(selected = null)

        val previewSelected = preview(selected = VKType.FULL)

        val previewKeystone =
            preview(
                selected = VKType.INCOMING,
                logo = R.drawable.ic_item_keystone,
                walletName = "Keystone"
            )

        private fun preview(
            selected: VKType?,
            logo: Int = R.drawable.ic_item_zashi,
            walletName: String = "Zodl",
        ) = ExportVKState(
            logo = imageRes(logo),
            description = styledStringResource(R.string.exportViewingKey_description, stringRes(walletName)),
            options =
                listOf(
                    VKOptionState(
                        type = VKType.INCOMING,
                        title = stringRes("Incoming Viewing Key"),
                        subtitle =
                            stringRes(
                                "Lets others see incoming transactions only. " +
                                    "Safe for accountants and payment verification."
                            ),
                        isChecked = selected == VKType.INCOMING,
                        onClick = {}
                    ),
                    VKOptionState(
                        type = VKType.FULL,
                        title = stringRes("Full Viewing Key"),
                        subtitle =
                            stringRes(
                                "Full visibility into all wallet activity. " +
                                    "Use for watch-only wallets, auditing, or legal compliance."
                            ),
                        isChecked = selected == VKType.FULL,
                        onClick = {}
                    ),
                ),
            continueButton =
                ButtonState(
                    text = stringRes("Continue"),
                    isEnabled = selected != null,
                    onClick = {}
                ),
            onBack = {}
        )
    }
}

internal data class VKOptionState(
    val type: VKType,
    val title: StringResource,
    val subtitle: StringResource,
    val isChecked: Boolean,
    val onClick: () -> Unit,
)
