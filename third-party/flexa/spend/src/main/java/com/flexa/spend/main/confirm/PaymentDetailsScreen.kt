package com.flexa.spend.main.confirm

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flexa.core.entity.CommerceSession
import com.flexa.core.theme.FlexaTheme
import com.flexa.spend.MockFactory
import com.flexa.spend.R
import com.flexa.spend.coveredByFlexaAccount
import com.flexa.spend.domain.FakeInteractor
import com.flexa.spend.label
import com.flexa.spend.main.assets.AccountCoverageCard
import com.flexa.spend.main.assets.AssetInfoFooter
import com.flexa.spend.main.assets.AssetsViewModel
import com.flexa.spend.main.assets.SessionFeeItem
import com.flexa.spend.requiresApproval
import com.flexa.spend.transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentDetailsScreen(
    modifier: Modifier = Modifier,
    sessionFlow: StateFlow<CommerceSession?>,
    assetsViewModel: AssetsViewModel,
    color: Color = BottomSheetDefaults.ContainerColor,
    toBack: () -> Unit,
    toLearnMore: () -> Unit,
) {

    val session by sessionFlow.collectAsStateWithLifecycle()
    val requiresApproval by remember {
        derivedStateOf { session.requiresApproval() }
    }

    AnimatedContent(
        targetState = requiresApproval, label = ""
    ) { covered ->
        if (covered) {
            AccountCoverageCard(assetsViewModel)
        } else {
            Column(
                modifier = modifier.background(color),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val palette = MaterialTheme.colorScheme
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { toBack() }) {
                        Icon(
                            modifier = Modifier.size(30.dp),
                            imageVector = Icons.Default.KeyboardArrowDown,
                            tint = palette.onSurfaceVariant,
                            contentDescription = null
                        )
                    }
                    Text(
                        modifier = Modifier
                            .padding(start = 14.dp)
                            .weight(1F, true),
                        text = stringResource(id = R.string.details),
                        style = TextStyle(
                            color = palette.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W400
                        )
                    )
                }
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = color),
                    leadingContent = {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null
                        )
                    },
                    headlineContent = {
                        Text(
                            text = session?.label() ?: "",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.W400,
                                color = palette.onBackground
                            )
                        )
                    },
                    supportingContent = {
                        val labelVisible by remember {
                            derivedStateOf { session?.transaction()?.label?.isNotBlank() == true }
                        }
                        androidx.compose.animation.AnimatedVisibility(labelVisible) {
                            Text(
                                text = session?.transaction()?.label ?: "",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.W400,
                                    color = palette.outline
                                )
                            )
                        }
                    }
                )
                val previewMode = LocalInspectionMode.current
                val showRate by remember {
                    derivedStateOf { if (!previewMode) !session.coveredByFlexaAccount() else true }
                }
                AnimatedVisibility(showRate) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = color),
                        leadingContent = {
                            Icon(
                                modifier = Modifier.size(24.dp),
                                imageVector = Icons.Outlined.SwapHoriz,
                                contentDescription = null
                            )
                        },
                        headlineContent = {
                            AnimatedContent(
                                targetState = session?.data?.rate?.label ?: "",
                                transitionSpec = {
                                    if (targetState < initialState) {
                                        slideInVertically { width -> width } +
                                                fadeIn() togetherWith slideOutVertically()
                                        { width -> -width } + fadeOut()
                                    } else {
                                        slideInVertically { width -> -width } +
                                                fadeIn() togetherWith slideOutVertically()
                                        { width -> width } + fadeOut()
                                    }.using(SizeTransform(clip = false))
                                }, label = ""
                            ) { state ->
                                Text(
                                    text = state,
                                    style = TextStyle(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.W400,
                                        color = palette.onBackground
                                    )
                                )
                            }
                        }
                    )
                }
                SessionFeeItem(assetsViewModel)
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = palette.outline.copy(alpha = .5F),
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                AssetInfoFooter(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 32.dp
                    )
                ) { toLearnMore() }
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
fun ConfirmDetailContentPreview() {
    FlexaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(color = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            PaymentDetailsScreen(
                sessionFlow = MutableStateFlow(MockFactory.getCommerceSession()),
                assetsViewModel = AssetsViewModel(FakeInteractor()),
                toBack = {},
                toLearnMore = {}
            )
        }
    }
}
