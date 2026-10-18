package com.flexa.spend.main.places_to_pay

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import com.flexa.spend.domain.FakeInteractor
import com.flexa.spend.main.main_screen.SheetScreen
import com.flexa.spend.main.main_screen.SpendViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Locations(
    modifier: Modifier = Modifier,
    spendViewModel: SpendViewModel,
    viewModel: PlacesToPayViewModel,
    toBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var title by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isFirstPage by remember { mutableStateOf(true) }
    val webViewScrollState = rememberScrollState()
    var scrollPosition by rememberSaveable { mutableStateOf(0) }
    val goBack = {
        isFirstPage = if (webView?.canGoBack() == true) {
            webView?.goBack()
            runCatching { viewModel.urlsList.remove(viewModel.urlsList.last()) }
            viewModel.urlsList.isEmpty()
        } else {
            viewModel.bundle.clear()
            viewModel.urlsList.clear()
            toBack()
            true
        }
        if (isFirstPage) {
            scope.launch {
                webViewScrollState.scrollTo(scrollPosition)
            }
        }
    }
    val bottomSheetScreen = spendViewModel.sheetScreen
    fun shouldHandleBackPress() = bottomSheetScreen is SheetScreen.PlacesToPay

    BackHandler(shouldHandleBackPress()) { goBack() }

    DisposableEffect(Unit) {
        onDispose {
            if (shouldHandleBackPress()) webView?.saveState(viewModel.bundle)
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(
                        onClick = { goBack() }) {
                        Icon(
                            modifier = Modifier.fillMaxSize(),
                            imageVector = Icons.Rounded.ChevronLeft,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LocationsWebView(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(webViewScrollState)
                .padding(padding),
            viewModel = viewModel,
            onViewCreated = { webView = it },
            onTitle = { title = it },
            onFirstPage = {
                isFirstPage = it
                if (!it) {
                    scrollPosition = webViewScrollState.value
                    scope.launch {
                        webViewScrollState.scrollTo(0)
                    }
                }
            },
        )
    }
}

@Preview
@Composable
private fun LocationsPreview() {
    Locations(
        modifier = Modifier.fillMaxWidth(),
        spendViewModel = SpendViewModel(FakeInteractor()),
        viewModel = PlacesToPayViewModel("", FakeInteractor()),
        toBack = {})
}
