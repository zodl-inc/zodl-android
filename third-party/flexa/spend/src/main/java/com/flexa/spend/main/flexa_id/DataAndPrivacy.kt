package com.flexa.spend.main.flexa_id

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flexa.core.theme.FlexaTheme
import com.flexa.spend.BuildConfig
import com.flexa.spend.R
import com.flexa.spend.domain.FakeInteractor
import com.flexa.spend.openEmail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataAndPrivacy(
    modifier: Modifier = Modifier,
    viewModel: FlexaIDViewModel,
    toDeleteAccount: () -> Unit,
    toLearnMore: () -> Unit,
    toBack: () -> Unit,
) {
    val scrollBehavior =
        TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val palette = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            TopBarTheme {
                LargeTopAppBar(
                    title = {
                        Text(text = stringResource(R.string.data_and_privacy))
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { toBack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        val scrollState = rememberScrollState()
        val deleteAccountInitiated by viewModel.deleteAccount.collectAsStateWithLifecycle()
        val context = LocalContext.current
        Column(
            modifier = modifier
                .padding(padding)
                .verticalScroll(scrollState),
        ) {
            val iconsSize by remember { mutableStateOf(22.dp) }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                modifier = Modifier.padding(start = 16.dp, end = 70.dp),
                text = stringResource(R.string.data_and_privacy_copy),
                fontSize = 13.sp, lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                modifier = Modifier.padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.tertiary.copy(.3F),
                    contentColor = palette.onSurface
                ),
                onClick = { toLearnMore() }
            ) {
                Text(
                    text = stringResource(R.string.learn_more),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                ),
                leadingContent = {
                    Icon(
                        modifier = Modifier.size(iconsSize),
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                    )
                },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.account_email),
                        fontSize = 18.sp
                    )
                },
                supportingContent = {
                    val email by viewModel.email.collectAsStateWithLifecycle()
                    Text(text = email, fontSize = 12.sp)
                }
            )
            androidx.compose.material3.ListItem(
                modifier = Modifier.clickable {
                    if (deleteAccountInitiated) {
                        context.openEmail()
                    } else {
                        toDeleteAccount()
                    }
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                ),
                leadingContent = {
                    if (deleteAccountInitiated) {
                        Icon(
                            modifier = Modifier.size(iconsSize),
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                        )
                    } else {
                        Icon(
                            modifier = Modifier.size(iconsSize),
                            imageVector = Icons.Outlined.PersonRemove,
                            contentDescription = null,
                        )
                    }
                },
                headlineContent = {
                    if (deleteAccountInitiated) {
                        Text(
                            text = stringResource(R.string.account_deletion_pending),
                            fontSize = 18.sp,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.delete_your_flexa_account),
                            fontSize = 18.sp,
                        )
                    }
                },
                supportingContent = {
                    if (deleteAccountInitiated) {
                        Text(
                            text = stringResource(R.string.delete_account_pending_copy),
                            fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                }
            )
            HorizontalDivider()
            Text(
                modifier = Modifier.padding(16.dp),
                text = stringResource(R.string.sdk_version, BuildConfig.SPEND_VERSION),
                fontSize = 12.5.sp,
                color = palette.outline
            )
        }
    }
}

private val customTypography = androidx.compose.material3.Typography(
    titleLarge = TextStyle(
        fontSize = 22.sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 32.sp,
    ),
)

@Composable
private fun TopBarTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        typography = customTypography,
        content = content
    )
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun DataAndPrivacyPreview() {
    FlexaTheme {
        DataAndPrivacy(
            modifier = Modifier.fillMaxSize(),
            viewModel = FlexaIDViewModel(FakeInteractor()),
            toDeleteAccount = {},
            toLearnMore = {},
            toBack = {},
        )
    }
}