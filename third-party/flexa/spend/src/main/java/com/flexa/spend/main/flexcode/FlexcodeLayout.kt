package com.flexa.spend.main.flexcode

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.flexa.core.theme.FlexaTheme
import com.flexa.spend.R
import com.flexa.spend.containsLetters
import com.flexa.spend.shiftHue
import kotlin.math.roundToInt


@Composable
fun FlexcodeLayout(
    modifier: Modifier = Modifier,
    code: String,
    color: Color = Color.Magenta
) {
    val density = LocalDensity.current
    val complexCode by remember { mutableStateOf(code.containsLetters()) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val rootCorner by remember { derivedStateOf { rootSize.width * 0.24f } }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(
                RoundedCornerShape(
                    topStart = with(density) {
                        (rootSize.width * 0.10f)
                            .roundToInt()
                            .toDp()
                    },
                    bottomStart = with(density) {
                        (rootSize.width * 0.10f)
                            .roundToInt()
                            .toDp()
                    },
                    topEnd = with(density) { rootCorner.toDp() },
                    bottomEnd = with(density) {
                        rootCorner.roundToInt().toDp()
                    }
                )
            )
            .background(Color.Black)
            .onSizeChanged { if (rootSize != it) rootSize = it }
    ) {
        val height128 by remember {
            derivedStateOf { with(density) { (rootSize.width * 0.2f).roundToInt().toDp() } }
        }
        val paddingStart128 by remember { derivedStateOf { rootSize.width * 0.206f } }
        val paddingEnd128 by remember { derivedStateOf { rootSize.width * 0.14f } }
        Code128(
            modifier = Modifier
                .fillMaxWidth()
                .height(height128)
                .padding(
                    start = with(density) {
                        paddingStart128
                            .roundToInt()
                            .toDp()
                    },
                    end = with(density) {
                        paddingEnd128
                            .roundToInt()
                            .toDp()
                    }
                )
                .background(Color.White)
                .align(Alignment.TopEnd),
            code = code
        )
        Code128(
            modifier = Modifier
                .height(height128)
                .fillMaxWidth()
                .padding(
                    start = with(density) {
                        paddingStart128
                            .roundToInt()
                            .toDp()
                    },
                    end = with(density) {
                        paddingEnd128
                            .roundToInt()
                            .toDp()
                    }
                )
                .background(Color.White)
                .align(Alignment.BottomEnd)
                .rotate(180F),
            code = code
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(with(density) {
                    (rootSize.width * 0.09f)
                        .roundToInt()
                        .toDp()
                })
        ) {
            drawRoundRect(
                color = Color.White,
                cornerRadius = CornerRadius(
                    x = rootCorner * 0.5f,
                    y = rootCorner * 0.5f
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(with(density) {
                    (rootSize.width * 0.074f)
                        .roundToInt()
                        .toDp()
                })
                .clip(
                    RoundedCornerShape(
                        topEnd = with(density) {
                            (rootCorner * 0.64f)
                                .roundToInt()
                                .toDp()
                        },
                        bottomEnd = with(density) {
                            (rootCorner * 0.64f)
                                .roundToInt()
                                .toDp()
                        }
                    )
                )
                .border(
                    width = with(density) {
                        (rootSize.width * 0.024f)
                            .roundToInt()
                            .toDp()
                    },
                    color = Color.Black,
                    shape = RoundedCornerShape(
                        topEnd = with(density) {
                            (rootCorner * 0.64f)
                                .roundToInt()
                                .toDp()
                        },
                        bottomEnd = with(density) {
                            (rootCorner * 0.64f)
                                .roundToInt()
                                .toDp()
                        }
                    )
                )
        ) {
            var parentWidth by remember { mutableIntStateOf(0) }
            var parentHeight by remember { mutableIntStateOf(0) }
            val offset by remember { derivedStateOf { rootSize.width * -0.008F } }
            val pdfBoxPadding by remember { derivedStateOf { rootSize.width * 0.072f } }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .padding(
                        end = with(density) {
                            pdfBoxPadding
                                .roundToInt()
                                .toDp()
                        },
                        top = with(density) {
                            pdfBoxPadding
                                .roundToInt()
                                .toDp()
                        },
                        bottom = with(density) {
                            pdfBoxPadding
                                .roundToInt()
                                .toDp()
                        }
                    )
                    .clip(
                        RoundedCornerShape(
                            topEnd = with(density) {
                                (rootCorner * 0.35f)
                                    .roundToInt()
                                    .toDp()
                            },
                            bottomEnd = with(density) {
                                (rootCorner * 0.35f)
                                    .roundToInt()
                                    .toDp()
                            }
                        )
                    )
                    .onGloballyPositioned {
                        parentWidth = it.size.width
                        parentHeight = it.size.height
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                val pdfOffset by remember {
                    derivedStateOf {
                        if (complexCode) rootSize.width * -0.039F else 0F
                    }
                }
                PDF417(
                    modifier = Modifier
                        .aspectRatio(if (complexCode) 1.12F else 1.14F)
                        .fillMaxHeight()
                        .rotate(180F)
                        .offset {
                            IntOffset(x = (offset - pdfOffset).toInt(), y = 0)
                        },
                    code = code,
                    rows = if (complexCode) 23 else 17,
                    columns = if (complexCode) 3 else 1
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(density) {
                            (pdfBoxPadding + pdfBoxPadding * .5f)
                                .roundToInt()
                                .toDp()
                        })
                        .align(Alignment.CenterEnd)
                ) { drawRect(color = Color.Black) }
            }
            val imgWidth by remember {
                derivedStateOf {
                    with(density) {
                        (rootSize.width * (if (complexCode) 0.438F else 0.401F)).roundToInt()
                            .toDp()
                    }
                }
            }
            val imgOffset by remember {
                derivedStateOf {
                    if (complexCode)
                        ((rootSize.width * 0.141f) - offset).toInt()
                    else
                        ((rootSize.width * 0.137f) - offset).toInt()
                }
            }
            val imgHeight by remember {
                derivedStateOf { with(density) { (rootSize.height * 0.056f).roundToInt().toDp() } }
            }
            val multiplier by remember { mutableFloatStateOf(if (complexCode) 0.028f else 0.025f) }
            Image(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(imgWidth)
                    .height(imgHeight)
                    .offset {
                        IntOffset(
                            x = imgOffset,
                            y = (rootSize.height * multiplier).roundToInt()
                        )
                    }
                    .animateContentSize(),
                painter = painterResource(R.drawable.c1),
                contentDescription = null,
                contentScale = ContentScale.FillBounds
            )
            Image(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(imgWidth)
                    .height(imgHeight)
                    .offset {
                        IntOffset(
                            x = imgOffset,
                            y = (rootSize.height * (-multiplier)).roundToInt()
                        )
                    }
                    .animateContentSize(),
                painter = painterResource(R.drawable.c2),
                contentDescription = null,
                contentScale = ContentScale.FillBounds
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) {
                    (rootSize.width * 0.2f)
                        .roundToInt()
                        .toDp()
                })
        ) {
            drawRect(color = Color.Black)
        }
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) {
                    (rootSize.width * 0.02f)
                        .roundToInt()
                        .toDp()
                })
                .offset { IntOffset(x = (rootSize.width * 0.189f).toInt(), y = 0) }
        ) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.shiftHue(10f),
                        color,
                        color.shiftHue(-10f),
                    ),
                )
            )
        }
        Text(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset {
                    IntOffset(
                        x = (rootSize.width * 0.02f).toInt(),
                        y = (rootSize.height * -0.14f).toInt()
                    )
                }
                .rotate(-90F),
            text = "Flexa",
            style = TextStyle(
                fontSize = with(density) { (rootSize.height * 0.10f).toSp() },
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
        )
    }
}

@Preview(
    showBackground = true, showSystemUi = false, backgroundColor = 0xFFCCCCCC,
    device = "spec:parent=pixel_5,orientation=portrait"
)
@Preview(
    showBackground = true, showSystemUi = false,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    device = "id:4in WVGA (Nexus S)"
)
@Preview(
    showBackground = true, showSystemUi = false,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    device = "spec:parent=7in WSVGA (Tablet)"
)
@Composable
private fun FlexcodeLayoutPreview() {
    FlexaTheme {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .padding(top = 100.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var sliderPosition by remember { mutableFloatStateOf(0.9f) }
            val d = LocalDensity.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.14f),
                contentAlignment = Alignment.Center,
            ) {
                FlexcodeLayout(
                    modifier = Modifier
                        .width(with(d) {
                            (sliderPosition * 1000F)
                                .roundToInt()
                                .toDp()
                        })
                        .aspectRatio(1.1f),
                    code = "123456789012",
                    color = Color.Magenta
                )
            }
            Spacer(modifier = Modifier.height(60.dp))
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it }
            )

        }
    }
}
