package com.flexa.spend.main.keypad

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flexa.core.shared.Promotion
import com.flexa.core.shared.SelectedAsset
import com.flexa.core.theme.FlexaTheme
import com.flexa.spend.MockFactory
import com.flexa.spend.R
import com.flexa.spend.domain.FakeInteractor
import com.flexa.spend.getAmount
import com.flexa.spend.getAssetAmount
import com.flexa.spend.getByAssetId
import com.flexa.spend.getCurrencySign
import com.flexa.spend.getDiscount
import com.flexa.spend.getFlexaBalance
import com.flexa.spend.getPromotion
import com.flexa.spend.main.assets.AccountBalance
import com.flexa.spend.main.assets.AccountCoverageCard
import com.flexa.spend.main.assets.AssetInfoFooter
import com.flexa.spend.main.assets.AssetsViewModel
import com.flexa.spend.positive
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AmountDetailsScreen(
    modifier: Modifier = Modifier,
    assetBundle: SelectedAsset,
    amount: String,
    promotions: List<Promotion>? = null,
    viewModel: AmountDetailViewModel,
    assetsViewModel: AssetsViewModel,
    toLearnMore: () -> Unit,
) {
    val color = BottomSheetDefaults.ContainerColor
    val account by assetsViewModel.account.collectAsStateWithLifecycle()
    val flexaBalance by remember { derivedStateOf { account.getFlexaBalance() } }
    val residualAmount by remember {
        derivedStateOf { amount.getAmount().subtract(flexaBalance).setScale(2) }
    }
    val showFlexaBalanceCoverage by remember {
        derivedStateOf { residualAmount <= BigDecimal.ZERO }
    }
    AnimatedContent(
        targetState = showFlexaBalanceCoverage, label = ""
    ) { balanceCoverage ->
        if (balanceCoverage) {
            AccountCoverageCard(assetsViewModel)
        } else {
            Column(modifier = modifier.background(color)) {
                val asset by assetsViewModel.selectedAssetBundle.collectAsStateWithLifecycle()
                val exchangeRates by viewModel.exchangeRates.collectAsStateWithLifecycle()
                val exchangeRate by remember {
                    derivedStateOf {
                        exchangeRates.getByAssetId(asset?.asset?.assetId ?: "")
                    }
                }
                val palette = MaterialTheme.colorScheme
                val exchangeRateProgress by remember {
                    derivedStateOf { exchangeRate == null }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        viewModel.percentJob?.cancel()
                    }
                }
                Text(
                    modifier = Modifier
                        .padding(start = 16.dp),
                    text = assetBundle.asset.assetData?.displayName ?: "",
                    style = TextStyle(
                        color = palette.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                var height by remember { mutableStateOf(200.dp) }
                val density = LocalDensity.current
                Column(
                    modifier = Modifier.onGloballyPositioned {
                        height = with(density) { it.size.height.toDp() }
                    }
                ) {
                    val promotion by remember {
                        derivedStateOf {
                            asset?.asset?.livemode?.run { promotions.getPromotion(this) }
                        }
                    }
                    val hasDiscount by remember {
                        derivedStateOf { promotion?.positive(amount) ?: false }
                    }
                    AnimatedVisibility(flexaBalance > BigDecimal.ZERO) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    AccountBalance(assetsViewModel, amount)
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = color),
                        leadingContent = {
                            Icon(
                                modifier = Modifier.size(24.dp),
                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                            )
                        },
                        overlineContent = {
                            AnimatedVisibility(hasDiscount) {
                                val text by remember {
                                    derivedStateOf {
                                        (exchangeRate?.getCurrencySign() ?: "") +
                                                amount.getAmount().subtract(flexaBalance)
                                                    .setScale(2).toPlainString()
                                    }
                                }
                                Text(
                                    text = text,
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.W500,
                                        color = palette.outline,
                                        textDecoration = TextDecoration.LineThrough
                                    )
                                )
                            }
                        },
                        headlineContent = {
                            val text by remember {
                                derivedStateOf {
                                    (exchangeRate?.getCurrencySign() ?: "") +
                                            residualAmount.subtract(
                                                promotion?.getDiscount(amount) ?: BigDecimal.ZERO
                                            ).setScale(2).toPlainString()
                                }
                            }
                            Text(
                                text = text,
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.W400,
                                    color = palette.onBackground
                                )
                            )
                        },
                        supportingContent = {
                            val amountWithDiscount by remember {
                                derivedStateOf {
                                    residualAmount.subtract(
                                        promotion?.getDiscount(amount) ?: BigDecimal.ZERO
                                    ).setScale(2).toPlainString()
                                }
                            }
                            val text by remember {
                                derivedStateOf {
                                    "${exchangeRate?.getAssetAmount(amountWithDiscount)} ${asset?.asset?.assetData?.displayName}"
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
                        colors = ListItemDefaults.colors(containerColor = color),
                        leadingContent = {
                            Icon(
                                modifier = Modifier.size(24.dp),
                                imageVector = Icons.Outlined.SwapHoriz,
                                contentDescription = null
                            )
                        },
                        headlineContent = {
                            val text by remember {
                                derivedStateOf {
                                    "1 ${asset?.asset?.assetData?.symbol ?: ""} = ${exchangeRate?.getCurrencySign() ?: ""}${exchangeRate?.price ?: ""}"
                                }
                            }
                            AnimatedContent(
                                targetState = exchangeRate?.price?.getAmount() ?: BigDecimal.ZERO,
                                transitionSpec = {
                                    if (targetState < initialState) {
                                        (slideInVertically { width -> width } +
                                                fadeIn()).togetherWith(slideOutVertically()
                                        { width -> -width } + fadeOut())
                                    } else {
                                        (slideInVertically { width -> -width } +
                                                fadeIn()).togetherWith(slideOutVertically()
                                        { width -> width } + fadeOut())
                                    }.using(SizeTransform(clip = false))
                                }, label = ""
                            ) { label ->
                                label
                                Text(
                                    text = if (!exchangeRateProgress) text
                                    else "${stringResource(R.string.updating)}...",
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

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = palette.outline.copy(alpha = .5F)
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
private fun AmountDetailsPreview() {
    FlexaTheme {
        AmountDetailsScreen(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(color = MaterialTheme.colorScheme.background),
            assetBundle = MockFactory.getMockSelectedAsset(),
            viewModel = AmountDetailViewModel(FakeInteractor()).apply {
                exchangeRates.value = MockFactory.getExchangeRates()
            },
            assetsViewModel = AssetsViewModel(FakeInteractor()),
            amount = "28.74",
            promotions = listOf(
                Promotion(
                    id = "",
                    amountOff = "20",
                    percentOff = "90",
                    livemode = false,
                    restrictions = Promotion.Restrictions(
                        minimumAmount = "5",
                        maximumDiscount = "2"
                    )
                )
            ),
            toLearnMore = {},
        )
    }
}