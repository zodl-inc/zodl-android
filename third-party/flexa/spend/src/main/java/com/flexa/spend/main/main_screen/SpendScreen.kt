package com.flexa.spend.main.main_screen

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flexa.core.Flexa
import com.flexa.core.shared.ErrorDialog
import com.flexa.core.theme.FlexaTheme
import com.flexa.identity.buildIdentity
import com.flexa.spend.MockFactory
import com.flexa.spend.R
import com.flexa.spend.Spend
import com.flexa.spend.TokenState
import com.flexa.spend.coveringAmount
import com.flexa.spend.data.DeepLink
import com.flexa.spend.data.DeepLinkParser
import com.flexa.spend.domain.CommerceSessionWorker
import com.flexa.spend.domain.FakeInteractor
import com.flexa.spend.main.assets.AccountBalance
import com.flexa.spend.main.assets.AccountCoverageCard
import com.flexa.spend.main.assets.AssetDetailsScreen
import com.flexa.spend.main.assets.AssetInfoSheetHeader
import com.flexa.spend.main.assets.AssetsBottomSheet
import com.flexa.spend.main.assets.AssetsScreen
import com.flexa.spend.main.assets.AssetsState
import com.flexa.spend.main.assets.AssetsViewModel
import com.flexa.spend.main.confirm.ConfirmCard
import com.flexa.spend.main.confirm.ConfirmViewModel
import com.flexa.spend.main.confirm.PaymentDetailsScreen
import com.flexa.spend.main.legacy_flexcode.LegacyFlexcode
import com.flexa.spend.main.places_to_pay.Locations
import com.flexa.spend.main.places_to_pay.PlacesToPay
import com.flexa.spend.main.places_to_pay.PlacesToPayViewModel
import com.flexa.spend.main.settings_popup.PopupViewModel
import com.flexa.spend.main.settings_popup.SettingsPopup
import com.flexa.spend.merchants.BrandsViewModel
import com.flexa.spend.nextGenNeedsClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendScreen(
    modifier: Modifier = Modifier,
    viewModel: SpendViewModel,
    assetsViewModel: AssetsViewModel,
    brandsViewModel: BrandsViewModel,
    settingsPopupViewModel: PopupViewModel = viewModel(),
    deepLink: String? = null,
    toBack: () -> Unit,
    toLogin: () -> Unit,
    toEdit: () -> Unit,
    toInputAmount: () -> Unit,
    toManageAccount: () -> Unit,
    toUrl: (@ParameterName("url") String) -> Unit,
) {
    val context = LocalContext.current

    SpendLifecycleRelatedMethods(assetsViewModel)

    Box(modifier = modifier) {
        val isPreview = LocalInspectionMode.current
        val assetsState by if (!isPreview) assetsViewModel.assetsState.collectAsStateWithLifecycle() else MutableStateFlow(
            AssetsState.Fine(emptyList())
        ).collectAsStateWithLifecycle()
        val isAssetsState by remember { derivedStateOf { assetsState !is AssetsState.NoAssets } }
        val scrollBehavior = if (isAssetsState)
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        else TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
        var showBottomSheet by rememberSaveable { mutableStateOf(false) }
        val closeBottomSheet = {
            showBottomSheet = false
        }
        val openBottomSheet: (SheetScreen) -> (Unit) = {
            viewModel.sheetScreen = it
            showBottomSheet = true
        }
        val commerceSession by viewModel.commerceSession.collectAsStateWithLifecycle()
        val showLegacyFlexcode by viewModel.openLegacyCard.collectAsStateWithLifecycle()
        val showNextGenCard by remember {
            derivedStateOf {
                viewModel.updateAccountData()
                commerceSession?.data?.isLegacy == false &&
                        !showLegacyFlexcode
            }
        }

        val link = rememberSaveable { mutableStateOf(deepLink) }
        LaunchedEffect(link.value) {
            link.value?.let {
                when (val lnk = DeepLinkParser.getDeepLink(it)) {
                    DeepLink.PlacesToPay -> {
                        openBottomSheet(SheetScreen.PlacesToPay)
                    }

                    is DeepLink.CommerceSession -> {
                        // todo under development
                    }

                    is DeepLink.ReportIssue -> toUrl(lnk.url)

                    else -> {}
                }
                link.value = null
            }
        }

        AnimatedVisibility( // Background Dim
            modifier = Modifier.zIndex(.1F),
            visible = showNextGenCard || showLegacyFlexcode || settingsPopupViewModel.opened,
            enter = fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 0)),
            exit = fadeOut(animationSpec = tween(durationMillis = 500, delayMillis = 0))
        ) {
            Canvas(modifier = Modifier
                .fillMaxSize(),
                onDraw = {
                    drawRect(color = Color.Black.copy(alpha = .4F))
                })
        }

        AnimatedVisibility( // Legacy Flexcode Card
            modifier = Modifier.zIndex(.2F),
            visible = showLegacyFlexcode,
            enter = fadeIn() + scaleIn(initialScale = .95F) + slideInVertically(initialOffsetY = { it / 4 }),
            exit = fadeOut() + scaleOut(targetScale = .95F) + slideOutVertically(targetOffsetY = { it / 4 })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 26.dp),
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .weight(.5F)
                )
                val scope = rememberCoroutineScope()
                LegacyFlexcode(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    viewModel = viewModel,
                    toBack = { commerceSessionId, containsAuthorization ->
                        scope.launch { viewModel.getAccount(useCached = false) }
                        viewModel.deleteCommerceSessionData()
                        commerceSessionId?.let { id ->
                            CommerceSessionWorker.execute(context, id)
                        }
                        viewModel.openLegacyCard.value = false
                        viewModel.setBrand(null)
                    },
                )
                Spacer(modifier = Modifier.height(26.dp))
                Spacer(
                    modifier = Modifier
                        .height(24.dp)
                        .navigationBarsPadding()
                )
            }
        }
        AnimatedVisibility( // Confirm Payment Card Dim
            modifier = Modifier.zIndex(.4F),
            visible = showNextGenCard && showBottomSheet,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            Canvas(modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = interactionSource, indication = null)
                {
                    if (viewModel.sheetScreen is SheetScreen.PaymentDetails) {
                        closeBottomSheet()
                    }
                },
                onDraw = { drawRect(color = Color.Black.copy(alpha = .4F)) })
        }
        AnimatedVisibility( // Confirm Payment Card
            modifier = Modifier.zIndex(.3F),
            visible = showNextGenCard,
            enter = fadeIn(animationSpec = tween(500)) + scaleIn(
                initialScale = .95F,
                animationSpec = tween(500)
            ),
            exit = fadeOut(animationSpec = tween(500)) + scaleOut(
                targetScale = .8F,
                animationSpec = tween(500)
            )
        ) {
            val key by remember { mutableStateOf(commerceSession?.data?.id) }
            val vm = viewModel(
                key = key,
                initializer = {
                    ConfirmViewModel(
                        session = viewModel.commerceSession,
                        interactor = Spend.interactor,
                    )
                }, viewModelStoreOwner = object : ViewModelStoreOwner {
                    override val viewModelStore: ViewModelStore
                        get() = viewModel.confirmViewModelStore
                }
            )

            DisposableEffect(showNextGenCard) {
                onDispose {
                    if (!showNextGenCard) {
                        viewModel.confirmViewModelStore.clear()
                        if (showBottomSheet && viewModel.sheetScreen is SheetScreen.PaymentDetails) {
                            closeBottomSheet()
                        }
                    }
                }
            }

            val interactionSource = remember { MutableInteractionSource() }
            val scope = rememberCoroutineScope()
            val close = {
                scope.launch { viewModel.getAccount(useCached = false) }
                commerceSession?.data?.let { session ->
                    if (session.nextGenNeedsClose()) {
                        viewModel.closeCommerceSession(context, session.id)
                    }
                    viewModel.deleteCommerceSessionData()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { },
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .weight(.5F)
                )
                ConfirmCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemBarsPadding()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { }
                        )
                        .padding(horizontal = 26.dp),
                    spendViewModel = viewModel,
                    viewModel = vm,
                    assetsViewModel = assetsViewModel,
                    onClose = { close() },
                    toDetails = {
                        openBottomSheet(SheetScreen.PaymentDetails(it))
                    },
                    toAssets = { openBottomSheet(SheetScreen.Assets(commerceSession?.data?.amount)) }
                )
                Spacer(modifier = Modifier.height(26.dp))
                Spacer(
                    modifier = Modifier
                        .height(24.dp)
                        .navigationBarsPadding()
                )
            }
        }
        SettingsPopup(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(color = MaterialTheme.colorScheme.tertiaryContainer)
                .offset(y = (0).dp),
            viewModel = settingsPopupViewModel,
            toPlaces = { openBottomSheet(SheetScreen.PlacesToPay) },
            toFlexaId = { toManageAccount() },
            toHowTo = { toUrl("https://flexa.co/guides/how-to-pay") },
            toReport = { toUrl("https://flexa.co/report-an-issue") }
        )
        Scaffold(
            modifier = modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                AnimatedVisibility(
                    visible = !isAssetsState,
                    enter = slideInVertically(
                        animationSpec = tween(
                            durationMillis = 700,
                            delayMillis = 600
                        )
                    )
                            + fadeIn(
                        animationSpec = tween(
                            durationMillis = 700,
                            delayMillis = 600
                        )
                    ),
                ) {
                    TopAppBar(
                        title = { Text(text = stringResource(id = R.string.pay_with_flexa)) },
                        navigationIcon = {
                            IconButton(
                                onClick = { toBack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { settingsPopupViewModel.switch() }) {
                                Icon(
                                    modifier = Modifier.size(30.dp),
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = null
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior
                    )
                }
                val blur by animateDpAsState(
                    targetValue = if (showNextGenCard || showLegacyFlexcode)
                        4.dp else 0.dp,
                    animationSpec = tween(
                        durationMillis = 500,
                        delayMillis = 500
                    ), label = "blur"
                )
                val density = LocalDensity.current
                AnimatedVisibility(
                    visible = isAssetsState,
                    exit = shrinkVertically(
                        targetHeight = { with(density) { 56.dp.toPx() }.toInt() },
                        animationSpec = tween(durationMillis = 1000, delayMillis = 300)
                    ) + fadeOut(animationSpec = tween(durationMillis = 500)),
                ) {
                    LargeTopAppBar(
                        modifier = Modifier.blur(
                            blur,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded
                        ),
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        title = { Text(text = stringResource(id = R.string.pay_with_flexa)) },
                        navigationIcon = {
                            IconButton(
                                onClick = { toBack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { settingsPopupViewModel.switch() }) {
                                Icon(
                                    modifier = Modifier.size(30.dp),
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = null
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior
                    )
                }
            }
        ) { padding ->
            val blur by animateDpAsState(
                targetValue = if (showNextGenCard || showLegacyFlexcode)
                    4.dp else 0.dp,
                animationSpec = tween(durationMillis = 500, delayMillis = 500),
                label = "blur"
            )
            Spend(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding()
                    ),
                viewModel = viewModel,
                assetsViewModel = assetsViewModel,
                brandsViewModel = brandsViewModel,
                sheetStateVisible = showBottomSheet,
                toAssets = {
                    viewModel.updateAccountData()
                    assetsViewModel.setScreen(AssetsScreen.Assets)
                    openBottomSheet(SheetScreen.Assets())
                },
                toAddAssets = { toBack() },
                toAssetInfo = { asset ->
                    viewModel.updateAccountData()
                    openBottomSheet(SheetScreen.AssetDetails(asset))
                },
                toEdit = { toEdit.invoke() },
                toInputAmount = {
                    viewModel.updateAccountData()
                    toInputAmount()
                },
                toLinkRoute = { linkRoute ->
                    when (linkRoute) {
                        LinkRoute.Account -> {
                            toManageAccount()
                        }

                        is LinkRoute.Url -> toUrl(linkRoute.url)
                    }
                }
            )

            if (showBottomSheet) {
                val sheetState = rememberModalBottomSheetState(
                    skipPartiallyExpanded = when (viewModel.sheetScreen) {
                        SheetScreen.PlacesToPay -> true
                        is SheetScreen.AssetDetails -> true
                        else -> false
                    }
                )
                ModalBottomSheet(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .zIndex(5f),
                    dragHandle = { SpendDragHandler() },
                    onDismissRequest = { closeBottomSheet() },
                    sheetState = sheetState,
                    properties = ModalBottomSheetProperties(
                        shouldDismissOnBackPress = viewModel.sheetScreen !is SheetScreen.Assets
                    )
                ) {
                    when (val screen = viewModel.sheetScreen) {
                        is SheetScreen.Assets -> {
                            val account by viewModel.account.collectAsStateWithLifecycle()
                            val amount = screen.amount
                            when {
                                account?.coveringAmount(amount) == true -> {
                                    AccountCoverageCard(assetsViewModel)
                                }

                                else -> {
                                    AssetsBottomSheet(
                                        viewModel = assetsViewModel,
                                        amount = screen.amount,
                                        toUrl = { toUrl(it) },
                                        toBack = { closeBottomSheet() },
                                    )
                                }
                            }
                        }

                        is SheetScreen.AssetDetails -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BottomSheetDefaults.ContainerColor),
                            ) {
                                AssetInfoSheetHeader(
                                    asset = screen.asset.asset,
                                    toBack = {},
                                    toSettings = {}
                                )
                                AccountBalance(assetsViewModel)
                                AssetDetailsScreen(
                                    assetBundle = screen.asset,
                                    assetsViewModel = assetsViewModel,
                                    toLearnMore = { toUrl(context.getString(R.string.learn_more_link)) },
                                )
                            }
                        }

                        is SheetScreen.PlacesToPay -> {
                            PlacesToPay(
                                modifier = Modifier.fillMaxWidth(),
                                spendViewModel = viewModel,
                                viewModel = viewModel(key = "ptp", initializer = {
                                    PlacesToPayViewModel("directory")
                                }),
                                sheetState = sheetState,
                                toBack = { closeBottomSheet() }
                            )
                        }

                        is SheetScreen.Locations -> {
                            Locations(
                                modifier = Modifier.fillMaxWidth(),
                                spendViewModel = viewModel,
                                viewModel = viewModel(key = "locations", initializer = {
                                    PlacesToPayViewModel("directory/${viewModel.selectedBrand.value?.name}/locations")
                                }),
                                toBack = { closeBottomSheet() }
                            )
                        }

                        is SheetScreen.PaymentDetails -> {
                            val sessionFlow by remember {
                                mutableStateOf(
                                    (viewModel.sheetScreen as? SheetScreen.PaymentDetails)?.session
                                        ?: MutableStateFlow(null)
                                )
                            }
                            PaymentDetailsScreen(
                                modifier = Modifier.fillMaxWidth(),
                                sessionFlow = sessionFlow,
                                assetsViewModel = assetsViewModel,
                                toBack = { closeBottomSheet() },
                                toLearnMore = { toUrl(context.getString(R.string.learn_more_link)) }
                            )
                        }
                    }
                }
            }
        }
    }

    ErrorDialog(errorHandler = viewModel.errorHandler) {

    }

    val previewMode = LocalInspectionMode.current
    val tokenState by if (!previewMode) Spend.tokenState.collectAsStateWithLifecycle()
    else remember { mutableStateOf(TokenState.Fine) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            Spend.tokenState.value = TokenState.Fine
        }
    }
    if (tokenState == TokenState.Error) {
        AlertDialog(
            onDismissRequest = {
                Spend.tokenState.value = TokenState.Fine
            },
            title = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.session_expired),
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    )
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.session_expired_copy),
                    style = TextStyle(fontWeight = FontWeight.Medium, lineHeight = 20.sp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    Spend.tokenState.value = TokenState.Fine
                    Flexa.buildIdentity().build().disconnect {
                        toLogin()
                    }
                }) {
                    Text(text = stringResource(id = R.string.sign_in))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    Spend.tokenState.value = TokenState.Fine
                    toBack()
                }) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            }
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun SpendScreenPreview() {
    FlexaTheme {
        SpendScreen(
            modifier = Modifier.fillMaxSize(),
            viewModel = SpendViewModel(FakeInteractor()),
            assetsViewModel = AssetsViewModel(
                FakeInteractor(), MutableStateFlow(MockFactory.getMockSelectedAsset())
            ),
            brandsViewModel = BrandsViewModel(FakeInteractor()),
            settingsPopupViewModel = PopupViewModel(),
            toBack = {},
            toEdit = {},
            toManageAccount = {},
            toUrl = {},
            toInputAmount = {},
            toLogin = {}
        )
    }
}