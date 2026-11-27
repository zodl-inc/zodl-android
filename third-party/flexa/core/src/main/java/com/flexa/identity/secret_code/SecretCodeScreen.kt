package com.flexa.identity.secret_code

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flexa.R
import com.flexa.core.shared.ErrorDialog
import com.flexa.core.theme.FlexaTheme
import com.flexa.identity.domain.FakeInteractor
import com.flexa.identity.mirror

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SecretCodeScreen(
    modifier: Modifier = Modifier,
    viewModel: SecretCodeViewModel,
    onBack: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val palette = MaterialTheme.colorScheme
    var showDialog by remember { mutableStateOf(false) }
    val result by viewModel.result.collectAsStateWithLifecycle(initialValue = null)
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(result) {
        if (result != null) onClose()
    }

    BackHandler { onBack() }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = palette.onBackground
                        )
                    }
                },
                title = {
                    Icon(
                        modifier = Modifier
                            .size(42.dp)
                            .mirror()
                            .fillMaxSize(),
                        imageVector = Icons.Outlined.LockReset,
                        contentDescription = null,
                        tint = palette.primary
                    )
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(
                    modifier = Modifier.padding(vertical = 16.dp),
                    onClick = { showDialog = true }) {
                    Text(text = stringResource(id = R.string.didnt_get_a_code))
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
            val secretCode by viewModel.secretCode.collectAsStateWithLifecycle()
            val progress by viewModel.progress.collectAsStateWithLifecycle()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                val context = LocalContext.current
                AnimatedContent(
                    targetState = progress, label = ""
                ) { prg ->
                    val text = if (!prg) context.getString(R.string.enter_your_verification_code)
                    else context.getString(R.string.processing) + "..."
                    Text(
                        modifier = Modifier.padding(vertical = 30.dp),
                        text = text,
                        style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Normal),
                        color = palette.onBackground
                    )
                }
                AnimatedVisibility(visible = !progress) {
                    SecretCode(
                        modifier = Modifier.fillMaxWidth(),
                        clickable = !progress,
                        value = secretCode ?: "",
                        onValueChanged = { viewModel.secretCode.value = it },
                        onFulfilled = {
                            viewModel.loginWithMagicCode(it)
                        }
                    )
                }
            }
            AnimatedVisibility(
                modifier = Modifier.align(Alignment.Center),
                visible = progress,
                enter = scaleIn(initialScale = 1.2F) + fadeIn(),
                exit = scaleOut(targetScale = .8F) + fadeOut()
            ) {
                CircularProgressIndicator()
            }
        }
    }
    if (showDialog) {
        AlertDialog(
            shape = RoundedCornerShape(25.dp),
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.didnt_get_a_code),
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.secret_code_description),
                    style = TextStyle(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )
                )
            },
            confirmButton = {
                Button(onClick = { showDialog = false }) {
                    Text(text = stringResource(id = R.string.got_it))
                }
            }
        )

    }
    ErrorDialog(errorHandler = viewModel.errorHandler) {
        viewModel.secretCode.value = null
    }
}

@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
fun SecretCodeScreenPreview() {
    FlexaTheme {
        Surface {
            SecretCodeScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = viewModel(initializer = {
                    SecretCodeViewModel(FakeInteractor())
                }),
            )
        }
    }
}
