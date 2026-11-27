package com.flexa.spend.main.flexa_id

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flexa.core.getJoinedYear
import com.flexa.core.getLimitsPercentage
import com.flexa.core.getResetDay
import com.flexa.core.getResetHour
import com.flexa.core.hasLimits
import com.flexa.core.shared.ErrorDialog
import com.flexa.core.theme.FlexaTheme
import com.flexa.core.view.AutoSizeText
import com.flexa.spend.R
import com.flexa.spend.domain.FakeInteractor
import com.flexa.spend.getAppName
import com.flexa.spend.main.main_screen.SpendViewModel
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAccount(
    modifier: Modifier = Modifier,
    viewModel: FlexaIDViewModel,
    spendViewModel: SpendViewModel,
    toBack: () -> Unit,
    toDataAndPrivacy: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior =
        TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val palette = MaterialTheme.colorScheme
    val signOut by viewModel.signOut.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        spendViewModel.getAccount()
    }

    LaunchedEffect(signOut) {
        if (signOut) onSignOut()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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

        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        val scrollState = rememberScrollState()
        val signOutDialog = remember { mutableStateOf(false) }
        val account by spendViewModel.account.collectAsStateWithLifecycle()
        Column(
            modifier = modifier
                .padding(padding)
                .verticalScroll(scrollState),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = min(1f, (1 - (scrollState.value * 0.001F)).coerceAtLeast(.8F))
                        scaleY = min(1f, (1 - scrollState.value * 0.001F).coerceAtLeast(.8F))
                        translationY = scrollState.value * 0.9f
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(palette.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (account?.name?.isNotBlank() == true)
                            (account?.name ?: "").split(" ")
                                .map { it.first() }
                                .joinToString("") else "",
                        color = palette.inverseOnSurface, fontSize = 48.sp
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                AutoSizeText(
                    modifier = Modifier
                        .padding(horizontal = 16.dp),
                    text = account?.name ?: "",
                    textStyle = TextStyle(fontSize = 32.sp, textAlign = TextAlign.Center),
                )
                Spacer(modifier = Modifier.height(10.dp))
                androidx.compose.animation.AnimatedVisibility(account?.getJoinedYear() != null) {
                    val text by remember {
                        derivedStateOf {
                            context.getString(R.string.joined_in, account?.getJoinedYear() ?: "")
                        }
                    }
                    Text(
                        text = text,
                        color = palette.outline,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
            Column(
                modifier = Modifier.background(palette.background),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(account?.hasLimits() == true) {
                    val limit by remember {
                        derivedStateOf { account?.limits?.firstOrNull() }
                    }
                    Card(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.onPrimary),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.your_flexa_account),
                                color = palette.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Row {
                                Icon(
                                    modifier = Modifier.size(28.dp),
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = limit?.name ?: "",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                progress = { account?.getLimitsPercentage() ?: 100f },
                                strokeCap = StrokeCap.Round,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = limit?.description ?: "", fontSize = 12.sp)
                                Text(text = limit?.label ?: "", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    modifier = Modifier.size(28.dp),
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val text by remember {
                                    derivedStateOf {
                                        val day = account?.getResetDay() ?: ""
                                        val hour = account?.getResetHour() ?: ""
                                        context.getString(R.string.reset_time, day, hour)
                                    }
                                }
                                Text(
                                    text = text,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.ListItem(
                    modifier = Modifier
                        .height(60.dp)
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(!progress) { toDataAndPrivacy() },
                    colors = ListItemDefaults.colors(
                        containerColor = palette.onPrimary
                    ),
                    leadingContent = {
                        Icon(
                            modifier = Modifier
                                .offset(x = (-4).dp)
                                .clip(CircleShape)
                                .background(palette.primary)
                                .size(38.dp)
                                .padding(5.dp),
                            imageVector = Icons.Rounded.Lock,
                            tint = palette.onPrimary,
                            contentDescription = null,
                        )
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.data_and_privacy),
                            fontSize = 18.sp
                        )
                    },
                    trailingContent = {
                        Icon(
                            modifier = Modifier
                                .offset(x = 16.dp)
                                .size(30.dp),
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                        )

                    },
                )
                Spacer(modifier = Modifier.height(40.dp))
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.tertiary.copy(.3F),
                        contentColor = palette.onSurface
                    ),
                    enabled = !progress,
                    onClick = { signOutDialog.value = true }
                ) {
                    Icon(
                        modifier = Modifier
                            .size(24.dp),
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AnimatedContent(
                        targetState = progress,
                        transitionSpec = {
                            (slideInHorizontally() + fadeIn()).togetherWith(
                                slideOutHorizontally() + fadeOut()
                            )
                        }, label = "button"
                    ) { prg ->
                        if (prg) {
                            Text(
                                text = stringResource(R.string.processing),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.sign_out_of_flexa),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(60.dp))
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
        if (signOutDialog.value) {
            AlertDialog(
                onDismissRequest = { signOutDialog.value = false },
                title = {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "${stringResource(R.string.sign_out_of_flexa)}?",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.primary
                        )
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.sign_out_copy, context.getAppName()),
                        style = TextStyle(
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp
                        )
                    )
                },
                dismissButton = {
                    TextButton(onClick = { signOutDialog.value = false }) {
                        Text(text = stringResource(android.R.string.cancel))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deleteToken()
                        signOutDialog.value = false
                    }) {
                        Text(text = stringResource(R.string.sign_out))
                    }
                }
            )
        }
    }
    ErrorDialog(
        errorHandler = viewModel.errorHandler,
        onDismissed = { }
    )
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun FlexaAccountPreview() {
    FlexaTheme {
        ManageAccount(
            modifier = Modifier.fillMaxSize(),
            viewModel = FlexaIDViewModel(FakeInteractor()),
            spendViewModel = SpendViewModel(FakeInteractor()),
            toBack = {},
            toDataAndPrivacy = {},
            onSignOut = {}
        )
    }
}