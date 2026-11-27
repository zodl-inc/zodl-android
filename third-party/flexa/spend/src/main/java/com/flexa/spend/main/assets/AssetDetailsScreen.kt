package com.flexa.spend.main.assets

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexa.core.entity.FeeBundle
import com.flexa.core.shared.SelectedAsset
import com.flexa.core.theme.FlexaTheme
import com.flexa.spend.MockFactory
import com.flexa.spend.R
import com.flexa.spend.domain.FakeInteractor
import com.flexa.spend.getAmount
import com.flexa.spend.getDigitWithPrecision
import com.flexa.spend.hasBalanceRestrictions
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssetDetailsScreen(
    modifier: Modifier = Modifier,
    assetsViewModel: AssetsViewModel,
    assetBundle: SelectedAsset,
    color: Color = BottomSheetDefaults.ContainerColor,
    toLearnMore: () -> Unit
) {

    val asset by remember {
        derivedStateOf {
            assetsViewModel.assets.firstOrNull {
                it.accountId == assetBundle.accountId && it.asset.assetId == assetBundle.asset.assetId
            }
        }
    }

    Column(
        modifier = modifier.background(color)
    ) {
        val palette = MaterialTheme.colorScheme
        Column(
            modifier = Modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val balanceRestricted by remember {
                derivedStateOf { asset?.asset?.hasBalanceRestrictions() == true }
            }

            AnimatedVisibility(balanceRestricted, label = "") {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            imageVector = Icons.Rounded.HourglassTop,
                            contentDescription = null
                        )
                    },
                    headlineContent = {
                        Text(
                            text = "${stringResource(R.string.balance)} ${stringResource(R.string.updating)}...",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.W400,
                                color = palette.onBackground
                            )
                        )
                    },
                    supportingContent = {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(style = SpanStyle(fontSize = MaterialTheme.typography.bodyMedium.fontSize)) {
                                    append(stringResource(R.string.balance_restrictions_copy1))
                                    append(" ")
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(asset?.asset?.balanceBundle?.availableLabel ?: "")
                                    }
                                    append(" ")
                                    append(stringResource(R.string.balance_restrictions_copy2))
                                }
                            },
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W400,
                                color = palette.outline
                            )
                        )
                    }
                )
            }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null
                    )
                },
                headlineContent = {
                    AnimatedContent(
                        targetState = asset?.asset?.balanceBundle?.total ?: BigDecimal.ZERO,
                        transitionSpec = {
                            if (targetState.compareTo(initialState) == -1) {
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
                        state
                        Text(
                            text = asset?.asset?.balanceBundle?.totalLabel ?: "",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.W400,
                                color = palette.onBackground
                            )
                        )
                    }
                },
                supportingContent = {
                    val text by remember {
                        derivedStateOf {
                            "${
                                asset?.asset?.balance?.getDigitWithPrecision(
                                    asset?.asset?.exchangeRate?.precision ?: 0
                                ) ?: ""
                            } ${asset?.asset?.assetData?.symbol ?: ""}"
                        }
                    }
                    Text(
                        text = text,
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W400,
                            color = palette.outline
                        )
                    )
                }
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = Icons.Outlined.SwapHoriz,
                        contentDescription = null
                    )
                },
                headlineContent = {
                    AnimatedContent(
                        targetState = asset?.asset?.exchangeRate?.price?.getAmount()
                            ?: BigDecimal.ZERO,
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
                        state
                        Text(
                            text = if (asset?.asset?.exchangeRate == null) {
                                "${stringResource(R.string.updating)}..."
                            } else {
                                "1 ${asset?.asset?.assetData?.symbol} = ${asset?.asset?.exchangeRate?.label}"
                            },
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.W400,
                                color = palette.onBackground
                            )
                        )
                    }
                }
            )
            SessionFeeItem(assetsViewModel)
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
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

@Composable
fun AssetInfoFooter(
    modifier: Modifier = Modifier,
    toLearnMore: () -> Unit
) {
    val bestExchangeDescription = stringResource(id = R.string.best_exchange_rate)
    val palette = MaterialTheme.colorScheme
    Row(modifier = modifier) {
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            modifier = Modifier
                .size(14.dp)
                .offset(y = 2.5.dp),
            imageVector = Icons.Outlined.Info,
            tint = palette.outline,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(12.dp))
        ClickableText(
            text = buildAnnotatedString {
                append(
                    AnnotatedString(
                        text = bestExchangeDescription
                    )
                )
                append(AnnotatedString(" "))
                append(
                    AnnotatedString(
                        stringResource(id = R.string.learn_more),
                        spanStyle = SpanStyle(textDecoration = TextDecoration.Underline)
                    )
                )
            },
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.W400,
                color = palette.outline
            ),
            onClick = { position ->
                if (position > bestExchangeDescription.length) {
                    toLearnMore.invoke()
                }
            }
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
fun AssetDetailContentPreview() {
    FlexaTheme {
        AssetDetailsScreen(
            assetBundle = MockFactory.getMockSelectedAsset().copy(
                asset = MockFactory.getMockSelectedAsset().asset.copy(
                    exchangeRate = MockFactory.getExchangeRate(),
                    balanceBundle = MockFactory.getBalanceBundle(),
                    feeBundle = FeeBundle(label = "$1.50")
                )
            ),
            assetsViewModel = AssetsViewModel(
                FakeInteractor(),
                MutableStateFlow(MockFactory.getMockSelectedAsset())
            ),
            toLearnMore = {}
        )
    }
}
