package co.electriccoin.zcash.ui.screen.about.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.SettingsListItemLeadingIcon
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.ZashiVersion
import co.electriccoin.zcash.ui.design.component.listitem.ListItemState
import co.electriccoin.zcash.ui.design.component.listitem.ZashiListItem
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.scaffoldScrollPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.fixture.ConfigInfoFixture
import co.electriccoin.zcash.ui.fixture.VersionInfoFixture
import co.electriccoin.zcash.ui.screen.support.model.ConfigInfo
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER

@Composable
fun About(
    onBack: () -> Unit,
    configInfo: ConfigInfo,
    onPrivacyPolicy: () -> Unit,
    onTermsOfUse: () -> Unit,
    versionInfo: VersionInfo,
) {
    Scaffold(
        topBar = {
            AboutTopAppBar(
                onBack = onBack,
                versionInfo = versionInfo,
                configInfo = configInfo,
            )
        },
    ) { paddingValues ->
        AboutMainContent(
            versionInfo = versionInfo,
            onPrivacyPolicy = onPrivacyPolicy,
            onTermsOfUse = onTermsOfUse,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .verticalScroll(
                        rememberScrollState()
                    ).scaffoldScrollPadding(paddingValues)
        )
    }
}

@Composable
private fun AboutTopAppBar(
    onBack: () -> Unit,
    versionInfo: VersionInfo,
    configInfo: ConfigInfo
) {
    ZashiSmallTopAppBar(
        title = stringResource(id = R.string.about_title),
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = onBack)
        },
        regularActions = {
            if (versionInfo.isDebuggable && !versionInfo.isRunningUnderTestService) {
                DebugMenu(versionInfo, configInfo)
            }
        },
    )
}

@Composable
private fun DebugMenu(
    versionInfo: VersionInfo,
    configInfo: ConfigInfo
) {
    Column(
        modifier = Modifier.testTag(AboutTag.DEBUG_MENU_TAG)
    ) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.about_overflow_menu_content_description)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            stringResource(
                                id = R.string.about_debug_menu_app_name,
                                stringResource(id = R.string.app_name)
                            )
                        )
                        Text(stringResource(R.string.about_debug_menu_build, versionInfo.gitSha))
                        Text(configInfo.toSupportString())
                    }
                },
                onClick = {
                    expanded = false
                }
            )
        }
    }
}

@Composable
fun AboutMainContent(
    onPrivacyPolicy: () -> Unit,
    onTermsOfUse: () -> Unit,
    versionInfo: VersionInfo,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            modifier = Modifier.padding(horizontal = ZashiDimensions.Spacing.spacing3xl),
            text = stringResource(id = R.string.about_subtitle),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(12.dp))

        Text(
            modifier = Modifier.padding(horizontal = ZashiDimensions.Spacing.spacing3xl),
            text = stringResource(id = R.string.about_description, CURRENCY_TICKER),
            color = ZashiColors.Text.textPrimary,
            style = ZashiTypography.textSm
        )

        Spacer(Modifier.height(32.dp))

        ZashiListItem(
            modifier = Modifier.padding(horizontal = 4.dp),
            state =
                ListItemState(
                    bigIcon = imageRes(R.drawable.ic_settings_info),
                    title = stringRes(R.string.about_button_privacy_policy),
                    onClick = onPrivacyPolicy
                ),
            leading = { modifier ->
                SettingsListItemLeadingIcon(
                    modifier = modifier,
                    drawableRes = R.drawable.ic_settings_info,
                    contentDescription = stringResource(R.string.about_button_privacy_policy)
                )
            }
        )

        ZashiHorizontalDivider()

        ZashiListItem(
            modifier = Modifier.padding(horizontal = 4.dp),
            state =
                ListItemState(
                    bigIcon = imageRes(R.drawable.ic_terms_of_use),
                    title = stringRes(stringResource(R.string.terms_of_use)),
                    onClick = onTermsOfUse
                )
        )

        Spacer(Modifier.weight(1f))

        ZashiVersion(
            modifier = Modifier.fillMaxWidth(),
            version = stringRes(R.string.settings_version, versionInfo.versionName)
        )
    }
}

@PreviewScreens
@Composable
private fun AboutPreview() =
    ZcashTheme {
        About(
            onBack = {},
            configInfo = ConfigInfoFixture.new(),
            onPrivacyPolicy = {},
            versionInfo = VersionInfoFixture.new(),
            onTermsOfUse = {}
        )
    }
