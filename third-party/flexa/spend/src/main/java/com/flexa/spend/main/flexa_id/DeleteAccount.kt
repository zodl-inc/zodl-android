package com.flexa.spend.main.flexa_id

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flexa.core.shared.ErrorDialog
import com.flexa.core.theme.FlexaTheme
import com.flexa.spend.R
import com.flexa.spend.domain.FakeInteractor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccount(
    modifier: Modifier = Modifier,
    viewModel: FlexaIDViewModel,
    toContinue: () -> Unit,
    toBack: () -> Unit,
) {
    val scrollBehavior =
        TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val palette = MaterialTheme.colorScheme
    val deleteAccount by viewModel.deleteAccount.collectAsStateWithLifecycle()

    LaunchedEffect(deleteAccount) {
        if (deleteAccount) toContinue()
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
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
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)
                    .navigationBarsPadding()
            ) {
                val progress by viewModel.progress.collectAsStateWithLifecycle()
                Button(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enabled = !progress,
                    onClick = { viewModel.deleteAccount() }
                ) {
                    AnimatedContent(
                        targetState = !progress,
                        transitionSpec = {
                            (slideInHorizontally() + fadeIn()).togetherWith(
                                slideOutHorizontally() + fadeOut()
                            )
                        }, label = "button"
                    ) { prg ->
                        if (prg) {
                            Text(text = stringResource(R.string._continue))
                        } else {
                            Text(text = "${stringResource(R.string.processing)}...")
                        }
                    }
                }
            }
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = modifier
                .padding(padding)
                .padding(horizontal = 22.dp)
                .verticalScroll(scrollState),
        ) {
            Icon(
                modifier = Modifier.size(60.dp),
                imageVector = Icons.Outlined.PersonRemove,
                tint = palette.primary,
                contentDescription = null,
            )
            Text(
                text = stringResource(R.string.delete_your_flexa_account),
                fontSize = 32.sp, lineHeight = 36.sp
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.delete_account_copy),
                fontSize = 14.sp, lineHeight = 21.sp
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
private fun DeleteAccountPreview() {
    FlexaTheme {
        DeleteAccount(
            modifier = Modifier.fillMaxSize(),
            viewModel = FlexaIDViewModel(FakeInteractor()),
            toContinue = {},
            toBack = {},
        )
    }
}