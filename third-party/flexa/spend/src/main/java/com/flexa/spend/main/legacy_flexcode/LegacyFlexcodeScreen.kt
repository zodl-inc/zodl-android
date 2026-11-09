package com.flexa.spend.main.legacy_flexcode

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flexa.core.shared.Brand
import com.flexa.core.shared.Promotion
import com.flexa.core.theme.FlexaTheme
import com.flexa.core.view.FlexaLogo
import com.flexa.spend.MockFactory
import com.flexa.spend.containsAuthorization
import com.flexa.spend.containsLetters
import com.flexa.spend.domain.FakeInteractor
import com.flexa.spend.getAmount
import com.flexa.spend.getAmountLabel
import com.flexa.spend.main.flexcode.FlexcodeLayout
import com.flexa.spend.main.main_screen.SpendViewModel
import com.flexa.spend.main.main_screen.ZERO
import com.flexa.spend.main.ui_utils.MarkdownText
import com.flexa.spend.main.ui_utils.SpendAsyncImage
import com.flexa.spend.main.ui_utils.rememberSelectedAsset
import com.flexa.spend.shiftHue
import com.flexa.spend.toColor
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun LegacyFlexcode(
    modifier: Modifier = Modifier,
    viewModel: SpendViewModel,
    toBack: (commerceSessionId: String?, containsAuthorization: Boolean) -> Unit,
) {
    val previewMode = LocalInspectionMode.current
    val commerceSession by if (!previewMode) viewModel.commerceSession.collectAsStateWithLifecycle()
    else MutableStateFlow(MockFactory.getMockCommerceSessionCompleted()).collectAsState()
    val brand = remember { mutableStateOf<Brand?>(null) }
    val containsAuthorization by remember {
        derivedStateOf { commerceSession?.containsAuthorization() == true }
    }

    BackHandler {
        toBack.invoke(commerceSession?.data?.id, containsAuthorization)
    }

    LaunchedEffect(commerceSession) {
        commerceSession?.let {
            brand.value = it.data?.brand
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(// Toolbar
            modifier = Modifier
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color by remember {
                    derivedStateOf {
                        brand.value?.color?.toColor() ?: Color.Magenta
                    }
                }
                FlexaLogo(
                    modifier = Modifier.size(22.dp),
                    shape = RoundedCornerShape(3.dp),
                    colors = listOf(Color.White, color, color.shiftHue(10f))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Flexa",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            IconButton(onClick = { toBack(commerceSession?.data?.id, containsAuthorization) }) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = null)
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        if (!previewMode) {
            SpendAsyncImage(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .size(54.dp),
                imageUrl = brand.value?.logoUrl,
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color = MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "F",
                    style = MaterialTheme.typography.headlineLarge.copy(MaterialTheme.colorScheme.onPrimary)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp),
            text = brand.value?.name?.let { "Pay $it" } ?: "",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.outline)
        )
        Spacer(modifier = Modifier.height(8.dp))
        AnimatedContent(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            targetState = commerceSession.getAmount(),
            transitionSpec = {
                if (commerceSession.getAmount() > initialState) {
                    (slideInVertically { width -> width } +
                            fadeIn()).togetherWith(
                        slideOutVertically()
                        { width -> -width } + fadeOut())
                } else {
                    (slideInVertically { width -> -width } +
                            fadeIn()).togetherWith(
                        slideOutVertically()
                        { width -> width } + fadeOut())
                }.using(SizeTransform(clip = false))
            }, label = "price_animation"
        ) { state ->
            state
            Text(
                text = commerceSession.getAmountLabel(),
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.W500)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .padding(horizontal = 32.dp)
        ) {
            val code by remember {
                derivedStateOf {
                    commerceSession?.data?.authorization?.number?.ifBlank { ZERO } ?: ZERO
                }
            }
            val complexCode by remember { mutableStateOf(code.containsLetters()) }
            AnimatedContent(
                containsAuthorization, label = "",
                transitionSpec = {
                    scaleIn(initialScale = 1.2F) + fadeIn() togetherWith scaleOut(targetScale = .8F) + fadeOut()
                }
            ) { targetState ->
                if (targetState) {
                    val asset by rememberSelectedAsset()
                    FlexcodeLayout(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(if (complexCode) 1.14f else 1.1f),
                        code = code,
                        color = asset?.asset?.assetData?.color?.toColor() ?: Color.Magenta
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.14f),
                        contentAlignment = Alignment.Center,
                    ) {
                        FlexcodeLayout(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(if (complexCode) 1.14f else 1.1f)
                                .alpha(.02F),
                            code = code,
                            color = MaterialTheme.colorScheme.primary
                        )
                        CircularProgressIndicator()
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(26.dp))
        androidx.compose.animation.AnimatedVisibility(
            !commerceSession?.data?.authorization?.instructions.isNullOrEmpty()
        ) {
            MarkdownText(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                text = commerceSession?.data?.authorization?.instructions ?: "",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            !commerceSession?.data?.authorization?.details.isNullOrEmpty()
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, top = 10.dp),
                text = commerceSession?.data?.authorization?.details ?: "",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Preview(backgroundColor = 0xFFEFEFEF, showBackground = true)
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
fun LegacyFlexcodePreview() {
    FlexaTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column {
                LegacyFlexcode(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding(),
                    viewModel = SpendViewModel(FakeInteractor()).apply {
                        setBrand(
                            MockFactory.getBrand().run {
                                copy(
                                    color = "#8043FF",
                                    promotions = listOf(
                                        Promotion(
                                            id = "",
                                            amountOff = "10",
                                            percentOff = "50",
                                            livemode = true,
                                            label = "Promotion label",
                                            restrictions = Promotion.Restrictions(
                                                maximumDiscount = "15", minimumAmount = "5",
                                            )
                                        )
                                    )
                                )
                            }
                        )
                    },
                    toBack = { _, _ -> },
                )
            }
        }
    }
}
