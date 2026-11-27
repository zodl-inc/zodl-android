package com.flexa.identity.verify_email

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexa.R
import com.flexa.core.theme.FlexaTheme
import com.flexa.core.view.FlexaLogo
import com.flexa.identity.main.SensorData
import com.flexa.identity.main.SensorDataManager
import com.flexa.identity.main.UserData
import com.flexa.identity.main.UserViewModel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VerifyEmail(
    modifier: Modifier = Modifier,
    userViewModel: UserViewModel,
    toBack: () -> Unit = {},
    toContinue: () -> Unit = {},
) {
    val palette = MaterialTheme.colorScheme
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val shadowHeight by remember { mutableStateOf(10.dp) }
    var cardPosition by remember { mutableFloatStateOf(0F) }
    var bottomBarPosition by remember { mutableFloatStateOf(0F) }


    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { toBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = palette.onBackground
                        )
                    }
                },
                title = {},
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            Column {
                val configuration = LocalConfiguration.current
                val density = LocalDensity.current
                val dividerHeight by remember {
                    val h = (configuration.screenHeightDp / 3).dp
                    derivedStateOf {
                        with(density) {
                            ((bottomBarPosition - cardPosition)
                                .coerceAtLeast(0F).toDp() - 16.dp)
                                .coerceAtMost(h)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .height(shadowHeight)
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(.05f)
                                )
                            )
                        )
                )
                HorizontalDivider()
                Spacer(modifier = Modifier.height(dividerHeight))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)
                        .onGloballyPositioned {
                            bottomBarPosition = it.positionInRoot().y
                        },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { toContinue() }
                    ) { Text(text = stringResource(R.string.open_mail)) }
                }
            }
        },
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = modifier
                .padding(padding)
                .offset { IntOffset(0, shadowHeight.roundToPx()) }
                .verticalScroll(scrollState),
        ) {
            val context = LocalContext.current
            val previewMode = LocalInspectionMode.current
            val scope = rememberCoroutineScope()
            var data by remember { mutableStateOf<SensorData?>(null) }
            val depthMultiplier = 10
            val roll by remember { derivedStateOf { (data?.roll ?: 0f) * depthMultiplier } }
            val pitch by remember { derivedStateOf { (data?.pitch ?: 0f) * depthMultiplier } }
            if (!previewMode) {
                DisposableEffect(Unit) {
                    val dataManager = SensorDataManager(context)
                    dataManager.init()

                    val job = scope.launch {
                        dataManager.data.receiveAsFlow().collect { data = it }
                    }

                    onDispose {
                        dataManager.cancel()
                        job.cancel()
                    }
                }
            }

            Spacer(modifier = Modifier.height(56.dp - shadowHeight))
            Icon(
                modifier = Modifier
                    .size(60.dp)
                    .offset(x = 22.dp),
                imageVector = Icons.Outlined.MarkEmailUnread,
                tint = palette.primary,
                contentDescription = null,
            )
            Text(
                modifier = Modifier.padding(horizontal = 22.dp),
                text = stringResource(R.string.verify_your_email_address),
                fontSize = 32.sp,
                lineHeight = 36.sp
            )
            Spacer(modifier = Modifier.height(50.dp))
            Text(
                modifier = Modifier
                    .background(palette.background)
                    .padding(horizontal = 22.dp),
                text = stringResource(R.string.verify_email_description),
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(54.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .offset {
                        IntOffset(
                            x = (roll * .5).dp.roundToPx(),
                            y = -(pitch * .5).dp.roundToPx()
                        )
                    },
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = palette.onSecondary
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column {
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        modifier = Modifier.padding(horizontal = 22.dp),
                        text = stringResource(id = R.string.verify_your_email_address),
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.onSecondaryContainer
                        )
                    )
                    Spacer(modifier = Modifier.height(30.dp))
                    ListItem(
                        modifier = Modifier
                            .onGloballyPositioned {
                                cardPosition = it.positionInRoot().y + it.size.height
                            }
                            .padding(horizontal = 4.dp),
                        colors = ListItemDefaults.colors(
                            containerColor = palette.onSecondary
                        ),
                        leadingContent = {
                            FlexaLogo(modifier = Modifier.size(36.dp), shape = CircleShape)
                        },
                        headlineContent = {
                            val annotatedString = buildAnnotatedString {
                                withStyle(
                                    style = SpanStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(stringResource(id = R.string.flexa))
                                }
                                append(" ")
                                withStyle(
                                    style = SpanStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = palette.outline
                                    )
                                ) {
                                    append(stringResource(id = R.string.just_now))
                                }
                            }
                            Text(text = annotatedString)
                        },
                        supportingContent = {
                            val email by remember { mutableStateOf(userViewModel.userData.value.email) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${stringResource(id = R.string.to)} $email",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        color = palette.outline
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = palette.outline
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Preview(device = "id:pixel_5")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun VerifyEmailPreview() {
    FlexaTheme {
        Surface {
            VerifyEmail(
                userViewModel = UserViewModel().apply {
                    userData.value = UserData(
                        email = "flexa.network@flexa.co"
                    )
                }
            )
        }
    }
}
