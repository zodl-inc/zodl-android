package com.flexa.identity.create_id

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContactMail
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flexa.R
import com.flexa.core.data.data.AppInfoProvider
import com.flexa.core.shared.ErrorDialog
import com.flexa.core.theme.FlexaTheme
import com.flexa.identity.autofill
import com.flexa.identity.coppa.CoppaHelper
import com.flexa.identity.domain.FakeInteractor
import com.flexa.identity.main.UserData
import com.flexa.identity.main.UserViewModel
import java.text.DateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Date
import java.util.TimeZone


@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
internal fun CreateId(
    modifier: Modifier = Modifier,
    viewModel: CreateIdViewModel,
    userVM: UserViewModel,
    toBack: () -> Unit = {},
    toPrivacy: () -> Unit = {},
    toTermsOfUse: () -> Unit = {},
    toCoppa: () -> Unit = {},
    toContinue: () -> Unit = {},
) {
    val palette = MaterialTheme.colorScheme
    val context = LocalContext.current
    val preview = LocalInspectionMode.current

    val userData by userVM.userData.collectAsStateWithLifecycle()
    val firstName by remember { derivedStateOf { userData.firstName ?: "" } }
    val lastName by remember { derivedStateOf { userData.lastName ?: "" } }
    val birthday by remember { derivedStateOf { userData.birthday } }
    val firstNameFilled by remember { derivedStateOf { firstName.isNotBlank() } }
    val lastNameFilled by remember { derivedStateOf { lastName.isNotBlank() } }
    val birthdayFilled by remember { derivedStateOf { birthday != null } }

    val focusManager = LocalFocusManager.current
    val lastNameFocus = remember { FocusRequester() }
    val firstNameIsFocused = remember { mutableStateOf(false) }
    var firstNameBlocked by remember { mutableStateOf(false) }
    val lastNameIsFocused = remember { mutableStateOf(false) }
    val nameExpanded by remember {
        if (!preview)
            derivedStateOf { firstNameIsFocused.value || lastNameIsFocused.value }
        else mutableStateOf(true)
    }

    var firstNameError by remember { mutableStateOf(false) }
    var lastNameError by remember { mutableStateOf(false) }
    var birthdayError by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        when (state) {
            is CreateIdViewModel.State.Success -> {
                toContinue()
                viewModel.state.value = CreateIdViewModel.State.General
            }

            else -> {}
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Icon(
                            modifier = Modifier.size(44.dp),
                            imageVector = Icons.Outlined.ContactMail,
                            contentDescription = null,
                            tint = palette.primary
                        )
                    }
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
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp, start = 20.dp, end = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    modifier = Modifier.padding(start = 0.dp),
                    onClick = toPrivacy
                ) {
                    Text(text = stringResource(id = R.string.about_flexa_and_privacy))
                }
                val progress by viewModel.progress.collectAsStateWithLifecycle()
                val buttonText by remember {
                    derivedStateOf {
                        if (progress) {
                            "${context.getString(R.string.processing)}..."
                        } else {
                            context.getString(R.string.get_started)
                        }
                    }
                }
                Button(
                    enabled = !progress,
                    onClick = {
                        if (!firstNameFilled) firstNameError = true
                        if (!lastNameFilled) lastNameError = true
                        when {
                            !birthdayFilled -> birthdayError = true
                            CoppaHelper.isTooYoung(birthday) -> toCoppa()
                            else -> viewModel.accounts(context, userData)

                        }
                    }) {
                    AnimatedContent(
                        targetState = progress,
                        transitionSpec = {
                            if (targetState) {
                                slideInHorizontally { width -> width } +
                                        fadeIn() togetherWith slideOutHorizontally()
                                { width -> -width } + fadeOut()
                            } else {
                                slideInHorizontally { width -> -width } +
                                        fadeIn() togetherWith slideOutHorizontally()
                                { width -> width } + fadeOut()
                            }.using(SizeTransform(clip = false))
                        }, label = stringResource(R.string.get_started)
                    ) { state ->
                        state
                        Text(text = buttonText)
                    }
                }
            }
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = stringResource(id = R.string.create_your_flexa_account),
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.W400,
                    color = palette.onBackground
                )
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(id = R.string.create_id_description_1),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W400,
                    color = palette.onBackground
                )
            )
            Spacer(modifier = Modifier.height(32.dp))
            val dateString by remember {
                derivedStateOf {
                    birthday?.run {
                        val df = DateFormat.getDateInstance(DateFormat.LONG)
                        df.timeZone = TimeZone.getTimeZone("UTC")
                        df.format(this)
                    } ?: ""
                }
            }
            val keyboardController = LocalSoftwareKeyboardController.current
            KeyboardHandler()
            val firstNameTextValue = remember {
                mutableStateOf(
                    TextFieldValue(
                        text = firstName,
                        selection = TextRange(firstName.length)
                    )
                )
            }
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .autofill(
                        autofillTypes = listOf(AutofillType.PersonFirstName),
                        onFill = {
                            firstNameTextValue.value =
                                TextFieldValue(text = it, selection = TextRange(it.length))
                            firstNameError = it.isEmpty()
                            userVM.userData.value = userVM.userData.value.copy(firstName = it)
                            kotlin.runCatching { lastNameFocus.requestFocus() }
                        },
                        onFocusChanged = { firstNameIsFocused.value = it.isFocused }
                    ),
                supportingText = {
                    if (firstNameError) Text(
                        "${stringResource(R.string.first_name)} ${stringResource(R.string.required).lowercase()}",
                        style = TextStyle(color = palette.error)
                    )
                },
                maxLines = 1,
                label = {
                    val text by remember {
                        derivedStateOf {
                            if (nameExpanded) context.getString(R.string.first_name)
                            else context.getString(R.string.full_name)
                        }
                    }
                    AnimatedContent(
                        targetState = text,
                        transitionSpec = {
                            if (targetState.length > initialState.length) {
                                slideInVertically { width -> width } +
                                        fadeIn() togetherWith slideOutVertically()
                                { width -> -width } + fadeOut()
                            } else {
                                slideInVertically { width -> -width } +
                                        fadeIn() togetherWith slideOutVertically()
                                { width -> width } + fadeOut()
                            }.using(SizeTransform(clip = false))
                        }, label = "Text"
                    ) { state -> Text(text = state) }
                },
                value = if (nameExpanded) firstNameTextValue.value
                else {
                    firstNameBlocked = true
                    TextFieldValue(
                        if (firstName.isNotBlank() || lastName.isNotBlank())
                            "$firstName $lastName"
                        else ""
                    )
                },
                onValueChange = {
                    when {
                        firstNameTextValue.value.text.isEmpty() -> {
                            firstNameBlocked = false
                            firstNameTextValue.value = it
                            firstNameError = it.text.isEmpty()
                            userVM.userData.value = userVM.userData.value.copy(firstName = it.text)
                        }

                        firstNameBlocked -> firstNameBlocked = false
                        else -> {
                            firstNameTextValue.value = it
                            firstNameError = it.text.isEmpty()
                            userVM.userData.value = userVM.userData.value.copy(firstName = it.text)
                        }
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null
                    )
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        focusManager.moveFocus(FocusDirection.Down)
                    }
                )
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = nameExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                val lastNameTextValue = remember {
                    mutableStateOf(
                        TextFieldValue(
                            text = lastName,
                            selection = TextRange(lastName.length)
                        )
                    )
                }
                TextField(
                    modifier = Modifier
                        .focusRequester(lastNameFocus)
                        .fillMaxWidth()
                        .autofill(
                            autofillTypes = listOf(AutofillType.PersonLastName),
                            onFill = {
                                lastNameTextValue.value = TextFieldValue(
                                    text = it, selection = TextRange(it.length)
                                )
                                lastNameError = it.isEmpty()
                                userVM.userData.value = userVM.userData.value.copy(lastName = it)
                                keyboardController?.hide()
                                showDatePicker = true
                            },
                            onFocusChanged = { lastNameIsFocused.value = it.isFocused }
                        ),
                    supportingText = {
                        if (lastNameError) Text(
                            "${stringResource(R.string.last_name)} ${stringResource(R.string.required).lowercase()}",
                            style = TextStyle(color = MaterialTheme.colorScheme.error)
                        )
                    },
                    value = lastNameTextValue.value,
                    maxLines = 1,
                    label = { Text(text = stringResource(id = R.string.last_name)) },
                    onValueChange = {
                        lastNameTextValue.value = it
                        lastNameError = it.text.isEmpty()
                        userVM.userData.value = userVM.userData.value.copy(lastName = it.text)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            focusManager.moveFocus(FocusDirection.Down)
                            showDatePicker = true
                        }
                    )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box {
                var height by remember { mutableStateOf(4.dp) }
                val density = LocalDensity.current
                TextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            height = with(density) { it.size.height.toDp() }
                        }
                        .focusable(false),
                    value = dateString,
                    label = { Text(text = stringResource(id = R.string.date_of_birth)) },
                    maxLines = 1,
                    readOnly = true,
                    onValueChange = { birthdayError = false },
                    supportingText = {
                        if (birthdayError) Text(
                            "Date of birth required",
                            style = TextStyle(color = MaterialTheme.colorScheme.error)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Event,
                            contentDescription = null
                        )
                    }
                )
                Spacer(modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(4.dp))
                    .focusable(true)
                    .clickable { showDatePicker = !showDatePicker })
            }
            Spacer(modifier = Modifier.height(32.dp))
            val appName by remember {
                mutableStateOf(AppInfoProvider.getAppName(context))
            }
            val copy = stringResource(id = R.string.create_id_info_description, appName)
            ClickableText(
                style = TextStyle(fontSize = 11.sp, textAlign = TextAlign.Start),
                text = buildAnnotatedString {
                    append(
                        AnnotatedString(
                            text = copy,
                            spanStyle = SpanStyle(
                                color = MaterialTheme.colorScheme.outline.copy(
                                    alpha = .8f
                                )
                            )
                        )
                    )
                    append(AnnotatedString(" "))
                    append(
                        AnnotatedString(
                            "${stringResource(id = R.string.term_of_service)}.",
                            spanStyle = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    )
                },
                onClick = { position ->
                    if (position > copy.length) {
                        toTermsOfUse.invoke()
                    }
                }
            )
        }
    }
    if (showDatePicker) {
        FlexaDatePickerDialog(
            onDateSelected = {
                birthdayError = false
                userVM.userData.value = userVM.userData.value.copy(
                    birthday = Date(
                        it.toInstant().toEpochMilli()
                    )
                )
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
    ErrorDialog(viewModel.errorHandler) {
        viewModel.clearError()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlexaDatePickerDialog(
    onDateSelected: (OffsetDateTime) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        yearRange = IntRange(
            start = ZonedDateTime.now().year - 100,
            endInclusive = ZonedDateTime.now().year - CoppaHelper.MINIMUM_ALLOWED_AGE + 1
        )
    )

    val selectedDate = datePickerState.selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC)
    }

    DatePickerDialog(
        onDismissRequest = { onDismiss() },
        confirmButton = {
            Button(onClick = {
                selectedDate?.let { onDateSelected(it) }
                onDismiss()
            }

            ) {
                Text(text = "OK")
            }
        },
        dismissButton = {
            Button(onClick = { onDismiss() }) {
                Text(text = "Cancel")
            }
        }
    ) {
        DatePicker(
            state = datePickerState
        )
    }
}

@Preview
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CreateIdPreview() {
    FlexaTheme {
        CreateId(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            viewModel = CreateIdViewModel(interactor = FakeInteractor()),
            userVM = UserViewModel().apply {
                userData.value =
                    UserData(
                        email = "",
                        firstName = "Satoshi",
                        lastName = "Nakamoto",
                        birthday = Date(443232000)
                    )
            },
        )
    }
}
