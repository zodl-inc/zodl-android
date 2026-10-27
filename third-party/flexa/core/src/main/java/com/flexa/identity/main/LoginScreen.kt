package com.flexa.identity.main

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flexa.R
import com.flexa.core.Flexa
import com.flexa.core.shared.ErrorDialog
import com.flexa.core.theme.FlexaTheme
import com.flexa.core.view.FlexaLogo
import com.flexa.identity.AppImage
import com.flexa.identity.autofill
import com.flexa.identity.create_id.KeyboardHandler
import com.flexa.identity.domain.FakeInteractor
import com.flexa.identity.utils.RegExHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
    userVM: UserViewModel = viewModel(),
    toBack: () -> Unit,
    toContinue: () -> Unit,
    toSignIn: () -> Unit,
    toUrl: (@ParameterName("url") String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = MaterialTheme.colorScheme
    val previewMode = LocalInspectionMode.current
    val items by if (!previewMode) {
        viewModel.icons.collectAsStateWithLifecycle()
    } else {
        MutableSharedFlow<List<String>>()
            .collectAsStateWithLifecycle(listOf(""))
    }
    val alpha: Float by animateFloatAsState(
        targetValue = if (items.isEmpty()) 0f else 0f,
        animationSpec = tween(durationMillis = 1000), label = "alpha",
    )

    val scaleTop: Float by animateFloatAsState(
        targetValue = if (items.isEmpty()) 1.2f else 1f,
        animationSpec = tween(durationMillis = 1000), label = "scaleTop",
    )
    val scaleBottom: Float by animateFloatAsState(
        targetValue = if (items.isEmpty()) 1.5f else 1f,
        animationSpec = tween(durationMillis = 1000), label = "scaleBottom",
    )
    val appName by remember {
        if (!previewMode) {
            val context = Flexa.requiredContext
            mutableStateOf(
                try {
                    val packageInfo = when {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                            Flexa.requiredContext.packageManager
                                .getPackageInfo(context.packageName, 0)

                        else -> context.packageManager
                            .getPackageInfo(
                                context.packageName,
                                PackageManager.PackageInfoFlags.of(0)
                            )
                    }
                    packageInfo.applicationInfo
                        ?.loadLabel(context.packageManager)
                        ?.toString().orEmpty().ifBlank { "Unknown App" }
                } catch (e: Exception) {
                    "Unknown App"
                }
            )
        } else {
            mutableStateOf("Flexa")
        }
    }

    BackHandler { toBack.invoke() }

    LaunchedEffect(state) {
        when (state) {
            is MainViewModel.State.Success -> {
                toContinue()
                viewModel.setState(MainViewModel.State.General)
            }

            is MainViewModel.State.Error -> {
                toSignIn()
                viewModel.setState(MainViewModel.State.General)
            }

            else -> {}
        }
    }

    Column(
        modifier = modifier.systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.Start),
            onClick = { toBack.invoke() }) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = palette.onBackground
            )
        }
        Box {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var data by remember { mutableStateOf<SensorData?>(null) }

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

            val depthMultiplier by remember { mutableIntStateOf(10) }
            val roll by remember { derivedStateOf { (data?.roll ?: 0f) * depthMultiplier } }
            val pitch by remember { derivedStateOf { (data?.pitch ?: 0f) * depthMultiplier } }
            Column(modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(
                        x = (roll * .9).dp.roundToPx(),
                        y = -(pitch * .9).dp.roundToPx()
                    )
                }
            ) {
                MerchantsIconsList(
                    modifier = Modifier
                        .scale(scaleTop + .1F)
                        .alpha(alpha),
                    bundle = BrandsIconsBundle(
                        items.take(8), ItemsType.TOP
                    ),
                )
                MerchantsIconsList(
                    modifier = Modifier
                        .scale(scaleBottom)
                        .alpha(alpha),
                    bundle = BrandsIconsBundle(
                        items.reversed().take(8), ItemsType.BOTTOM
                    ),
                )
                Spacer(modifier = Modifier.height(26.dp))
            }
            if (!previewMode) {
                val packageManager = context.packageManager
                val applicationInfo =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getApplicationInfo(
                            context.packageName,
                            PackageManager.ApplicationInfoFlags.of(0)
                        )
                    } else {
                        packageManager.getApplicationInfo(
                            context.packageName,
                            PackageManager.GET_META_DATA
                        )
                    }
                val res = applicationInfo.icon
                AppImage(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(84.dp)
                        .offset {
                            IntOffset(
                                x = -(roll * .5).dp.roundToPx(),
                                y = (pitch * .5).dp.roundToPx()
                            )
                        },
                    resource = res
                )
            } else {
                FlexaLogo(modifier = Modifier
                    .size(84.dp)
                    .offset {
                        IntOffset(
                            x = -(roll * .5).dp.roundToPx(),
                            y = (pitch * .5).dp.roundToPx()
                        )
                    }
                    .align(Alignment.BottomCenter))
            }
        }
        Spacer(modifier = Modifier.height(26.dp))
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .weight(1F)
                .padding(horizontal = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(id = R.string.invitation_title, appName),
                style = TextStyle(
                    fontWeight = FontWeight.W500,
                    fontSize = 14.sp,
                    color = palette.onBackground.copy(alpha = .6F)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.pay_with_flexa),
                style = TextStyle(
                    fontWeight = FontWeight.W400,
                    fontSize = 32.sp,
                    color = palette.onBackground
                )
            )
            Spacer(modifier = Modifier.height(40.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Outlined.Storefront,
                    contentDescription = null,
                    tint = palette.onBackground
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier.padding(start = 16.dp),
                        text = stringResource(id = R.string.login_title_1),
                        style = TextStyle(
                            fontWeight = FontWeight.W400,
                            fontSize = 16.sp,
                            color = palette.onBackground.copy(alpha = .7F)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        modifier = Modifier.padding(start = 16.dp),
                        text = stringResource(id = R.string.login_description_1, appName),
                        style = TextStyle(
                            fontWeight = FontWeight.W500,
                            fontSize = 13.sp,
                            color = palette.outline.copy(alpha = .6F)
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    tint = palette.onBackground
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier.padding(start = 16.dp, end = 30.dp),
                        text = stringResource(id = R.string.login_title_2),
                        style = TextStyle(
                            fontWeight = FontWeight.W400,
                            fontSize = 16.sp,
                            color = palette.onBackground.copy(alpha = .7F)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        modifier = Modifier.padding(start = 16.dp),
                        text = stringResource(id = R.string.login_description_2),
                        style = TextStyle(
                            fontWeight = FontWeight.W500,
                            fontSize = 13.sp,
                            color = palette.outline.copy(alpha = .6F)
                        )
                    )
                    TextButton(
                        modifier = Modifier.padding(start = 4.dp),
                        onClick = { toUrl("https://flexa.co/legal/privacy") }) {
                        Text(text = stringResource(id = R.string.about_flexa_and_privacy))
                    }
                }
            }
        }
        val userData by userVM.userData.collectAsStateWithLifecycle()
        val email by remember { derivedStateOf { userData.email ?: "" } }
        val progress by viewModel.progress.collectAsStateWithLifecycle()
        val buttonActive by remember {
            derivedStateOf {
                RegExHelper.isEmailValid(email) && !progress
            }
        }
        val keyboardController = LocalSoftwareKeyboardController.current
        val emailTextValue = remember { mutableStateOf(TextFieldValue(email)) }
        KeyboardHandler()
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .autofill(
                    autofillTypes = listOf(AutofillType.EmailAddress),
                    onFill = {
                        emailTextValue.value = TextFieldValue(
                            text = it, selection = TextRange(it.length)
                        )
                        userVM.userData.value = userVM.userData.value.copy(email = it)
                        keyboardController?.hide()
                    },
                ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { keyboardController?.hide() }
            ),

            maxLines = 3,
            leadingIcon = {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Outlined.AlternateEmail,
                    contentDescription = null
                )
            },
            trailingIcon = {
                IconButton(onClick = { viewModel.state.value = MainViewModel.State.Info }) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null
                    )
                }
            },
            label = {
                Text(
                    text = stringResource(id = R.string.whats_your_email_address),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            shape = CircleShape,
            value = emailTextValue.value,
            onValueChange = {
                emailTextValue.value = it.copy()
                userVM.userData.value = userVM.userData.value.copy(email = it.text)
            }
        )
        Spacer(modifier = Modifier.height(22.dp))
        val context = LocalContext.current
        val buttonText by remember {
            derivedStateOf {
                if (progress) {
                    "${context.getString(R.string.processing)}..."
                } else {
                    context.getString(R.string.continue_)
                }
            }
        }
        Button(
            modifier = Modifier.height(50.dp),
            enabled = buttonActive,
            onClick = { viewModel.tokens(email) }) {
            AnimatedContent(
                targetState = progress,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }, label = "Text"
            ) { state ->
                state
                Text(
                    modifier = Modifier.animateContentSize(),
                    text = buttonText
                )
            }
        }
        Spacer(
            modifier = Modifier
                .height(40.dp)
                .navigationBarsPadding()
        )
    }
    if (state is MainViewModel.State.Info) {
        AlertDialog(
            shape = RoundedCornerShape(25.dp),
            onDismissRequest = {
                viewModel.state.value = MainViewModel.State.General
            },
            title = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Email address privacy",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.email_description, appName),
                    style = TextStyle(textAlign = TextAlign.Center)
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.state.value = MainViewModel.State.General
                }) {
                    Text(text = stringResource(id = R.string.got_it))
                }
            }
        )
    }
    ErrorDialog(
        errorHandler = viewModel.errorHandler,
        onDismissed = { }
    )
}

@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun LoginPreview() {
    FlexaTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LoginScreen(
                viewModel = viewModel { MainViewModel(FakeInteractor()) },
                toBack = {},
                toContinue = {},
                toSignIn = {},
                toUrl = {}
            )
        }
    }
}
