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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flexa.core.theme.FlexaTheme
import com.flexa.spend.R
import com.flexa.spend.domain.FakeInteractor
import java.math.BigDecimal

@Composable
internal fun SessionFeeItem(
    viewModel: AssetsViewModel
) {
    val previewMode = LocalInspectionMode.current
    val palette = MaterialTheme.colorScheme
    val sessionFee by if (!previewMode)
        viewModel.sessionFee.collectAsStateWithLifecycle()
    else remember {
        mutableStateOf(
            SessionFee(
                equivalent = BigDecimal("5.01"),
                equivalentLabel = "\$5.01",
                amount = BigDecimal("0.0003"),
                amountPriceLabel = "8-9 gwei",
                amountLabel = "0.0003 ETH"
            )
        )
    }
    val feeVisibility by remember {
        derivedStateOf {
            sessionFee?.amount?.let { it > BigDecimal.ZERO } ?: false
        }
    }
    AnimatedVisibility(feeVisibility) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Outlined.Language,
                    contentDescription = null
                )
            },
            overlineContent = {
                Text(text = stringResource(R.string.network_fee))
            },
            headlineContent = {
                AnimatedContent(
                    targetState = sessionFee?.equivalent ?: BigDecimal.ZERO,
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
                ) { targetState ->
                    targetState
                    Text(
                        text = sessionFee?.equivalentLabel ?: "",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.W400,
                            color = palette.onBackground
                        )
                    )
                }
            },
            supportingContent = {
                AnimatedContent(
                    targetState = sessionFee?.amount ?: BigDecimal.ZERO,
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
                ) { targetState ->
                    targetState
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sessionFee?.amountLabel ?: "",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W500,
                                color = palette.outline
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        sessionFee?.amountPriceLabel?.let { amountPriceLabel ->
                            if (amountPriceLabel.isNotBlank()) {
                                Text(
                                    modifier = Modifier
                                        .border(
                                            width = 1.dp,
                                            brush = SolidColor(palette.outline),
                                            MaterialTheme.shapes.small
                                        )
                                        .padding(vertical = 2.dp, horizontal = 8.dp),
                                    text = amountPriceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.outline
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun SessionFeeItemPreview() {
    FlexaTheme {
        Surface {
            SessionFeeItem(AssetsViewModel(FakeInteractor()))
        }
    }
}