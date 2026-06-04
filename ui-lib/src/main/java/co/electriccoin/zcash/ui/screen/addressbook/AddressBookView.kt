@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.addressbook

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.OldZashiBottomBar
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.listitem.ContactListItemState
import co.electriccoin.zcash.ui.design.component.listitem.ZashiContactListItem
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.asScaffoldScrollPaddingValues
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle
import kotlinx.coroutines.launch

@Composable
fun AddressBookView(
    state: AddressBookState
) {
    BlankBgScaffold(
        topBar = {
            AddressBookTopAppBar(
                onBack = state.onBack,
                state = state
            )
        }
    ) { paddingValues ->
        when {
            state.items.isEmpty() && state.isLoading -> {
                CircularScreenProgressIndicator()
            }

            state.items.isEmpty() && !state.isLoading -> {
                EmptyFullscreen(
                    state = state,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .scaffoldPadding(paddingValues)
                )
            }

            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentPadding = paddingValues.asScaffoldScrollPaddingValues()
                    ) {
                        itemsIndexed(
                            contentType = { _, item -> item.contentType },
                            items = state.items,
                        ) { index, item ->
                            when (item) {
                                is AddressBookItem.Contact -> {
                                    ZashiContactListItem(
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        state = item.state,
                                    )
                                    if (index != state.items.lastIndex &&
                                        state.items[index + 1] is AddressBookItem.Contact
                                    ) {
                                        ZashiHorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                        )
                                    }
                                }

                                is AddressBookItem.Title -> {
                                    if (index == 0) {
                                        Spacer(Modifier.height(16.dp))
                                    } else {
                                        Spacer(Modifier.height(20.dp))
                                    }
                                    ZashiTitleItem(
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        state = item
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }

                                AddressBookItem.Empty -> {
                                    Spacer(modifier = Modifier.height(68.dp))
                                    EmptyItem(
                                        modifier =
                                            Modifier
                                                .padding(horizontal = 20.dp)
                                                .fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    OldZashiBottomBar {
                        AddContactButton(
                            state = state,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ZashiDimensions.Spacing.spacing3xl)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZashiTitleItem(
    state: AddressBookItem.Title,
    modifier: Modifier = Modifier
) {
    Text(
        modifier = modifier,
        text = state.title.getValue(),
        style = ZashiTypography.textSm,
        fontWeight = FontWeight.Medium,
        color = ZashiColors.Text.textTertiary,
    )
}

@Composable
private fun EmptyItem(modifier: Modifier = Modifier) {
    Surface(
        modifier =
            modifier.dashedBorder(
                strokeWidth = 2.5.dp,
                color = ZashiColors.Surfaces.strokeSecondary,
                cornerRadiusDp = 16.dp,
                density = LocalDensity.current
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(painter = painterResource(id = R.drawable.ic_address_book_empty), contentDescription = null)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.address_book_empty),
                color = ZashiColors.Text.textPrimary,
                style = ZashiTypography.header6,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EmptyFullscreen(
    state: AddressBookState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painter = painterResource(id = R.drawable.ic_address_book_empty), contentDescription = null)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(id = R.string.address_book_empty),
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
                style = ZashiTypography.header6,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(1f)
            AddContactButton(
                state = state,
                modifier =
                    Modifier
                        .fillMaxWidth()
            )
        }
    }
}

@Suppress("MagicNumber", "ModifierComposed")
fun Modifier.dashedBorder(
    strokeWidth: Dp,
    color: Color,
    cornerRadiusDp: Dp,
    density: Density
) = composed(
    factory = {
        val strokeWidthPx = density.run { strokeWidth.toPx() }
        val cornerRadiusPx = density.run { cornerRadiusDp.toPx() }

        this.then(
            Modifier.drawWithCache {
                onDrawBehind {
                    val stroke =
                        Stroke(
                            width = strokeWidthPx,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 25f), 0f)
                        )

                    drawRoundRect(
                        color = color,
                        style = stroke,
                        cornerRadius = CornerRadius(cornerRadiusPx)
                    )
                }
            }
        )
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddContactButton(
    state: AddressBookState,
    modifier: Modifier = Modifier
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    AddressBookPopup(
        modifier = modifier,
        tooltipState = tooltipState,
        state = state,
    ) {
        ZashiButton(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(AddressBookTag.ADD_CONTACT),
            state =
                ButtonState(
                    onClick = {
                        scope.launch {
                            if (tooltipState.isVisible) tooltipState.dismiss() else tooltipState.show()
                        }
                    },
                    text = stringRes(R.string.address_book_add)
                )
        ) { scope ->
            Image(
                painter = painterResource(id = R.drawable.ic_address_book_plus),
                colorFilter = ColorFilter.tint(ZashiColors.Btns.Primary.btnPrimaryFg),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            scope.Text()
            Spacer(modifier = Modifier.width(6.dp))
            scope.Loading()
        }
    }
}

@Composable
private fun AddressBookTopAppBar(
    onBack: () -> Unit,
    state: AddressBookState,
) {
    ZashiSmallTopAppBar(
        title = state.title.getValue(),
        modifier = Modifier.testTag(AddressBookTag.TOP_APP_BAR),
        showTitleLogo = true,
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = onBack)
        },
        regularActions = {
            state.info?.let {
                ZashiIconButton(it)
                Spacer(20.dp)
            }
        }
    )
}

@PreviewScreens
@Composable
private fun AddressBookDataPreview() {
    ZcashTheme {
        AddressBookView(
            state =
                AddressBookState(
                    isLoading = false,
                    onBack = {},
                    items =
                        listOf(
                            AddressBookItem.Title(stringRes("Title")),
                            AddressBookItem.Contact(
                                ContactListItemState(
                                    name = stringRes("Name Surname"),
                                    address = stringRes("3iY5ZSkRnevzSMu4hosasdasdasdasd12312312dasd9hw2").withStyle(),
                                    bigIcon = imageRes("NS"),
                                    smallIcon = null,
                                    isShielded = false,
                                    onClick = {}
                                )
                            ),
                            AddressBookItem.Contact(
                                ContactListItemState(
                                    name = stringRes("Name Surname"),
                                    address = stringRes("3iY5ZSkRnevzSMu4hosasdasdasdasd12312312dasd9hw2").withStyle(),
                                    bigIcon = imageRes("NS"),
                                    isShielded = false,
                                    smallIcon = null,
                                    onClick = {}
                                )
                            )
                        ),
                    scanButton =
                        ButtonState(
                            text = stringRes("Scan"),
                        ),
                    manualButton =
                        ButtonState(
                            text = stringRes("Manual"),
                        ),
                    title = stringRes("Address book"),
                    info = null
                ),
        )
    }
}

@PreviewScreens
@Composable
private fun SelectRecipientDataPreview() {
    ZcashTheme {
        AddressBookView(
            state =
                AddressBookState(
                    isLoading = false,
                    onBack = {},
                    items =
                        listOf(
                            AddressBookItem.Title(stringRes("Title")),
                            AddressBookItem.Contact(
                                ContactListItemState(
                                    name = stringRes("Name Surname"),
                                    address = stringRes("3iY5ZSkRnevzSMu4hosasdasdasdasd12312312dasd9hw2").withStyle(),
                                    bigIcon = imageRes("NS"),
                                    isShielded = false,
                                    smallIcon = null,
                                    onClick = {}
                                )
                            ),
                            AddressBookItem.Contact(
                                ContactListItemState(
                                    name = stringRes("Name Surname"),
                                    address = stringRes("3iY5ZSkRnevzSMu4hosasdasdasdasd12312312dasd9hw2").withStyle(),
                                    bigIcon = imageRes("NS"),
                                    isShielded = false,
                                    smallIcon = null,
                                    onClick = {}
                                )
                            ),
                            AddressBookItem.Title(stringRes("Title")),
                            AddressBookItem.Contact(
                                ContactListItemState(
                                    name = stringRes("Name Surname"),
                                    address = stringRes("3iY5ZSkRnevzSMu4hosasdasdasdasd12312312dasd9hw2").withStyle(),
                                    bigIcon = imageRes("NS"),
                                    isShielded = false,
                                    smallIcon = null,
                                    onClick = {}
                                )
                            ),
                            AddressBookItem.Contact(
                                ContactListItemState(
                                    name = stringRes("Name Surname"),
                                    address = stringRes("3iY5ZSkRnevzSMu4hosasdasdasdasd12312312dasd9hw2").withStyle(),
                                    bigIcon = imageRes("NS"),
                                    isShielded = false,
                                    smallIcon = null,
                                    onClick = {}
                                )
                            )
                        ),
                    scanButton =
                        ButtonState(
                            text = stringRes("Scan"),
                        ),
                    manualButton =
                        ButtonState(
                            text = stringRes("Manual"),
                        ),
                    title = stringRes("Select Recipient"),
                    info = IconButtonState(R.drawable.ic_help) {},
                ),
        )
    }
}

@PreviewScreens
@Composable
private fun LoadingPreview() {
    ZcashTheme {
        AddressBookView(
            state =
                AddressBookState(
                    isLoading = true,
                    onBack = {},
                    items = emptyList(),
                    scanButton =
                        ButtonState(
                            text = stringRes("Scan"),
                        ),
                    manualButton =
                        ButtonState(
                            text = stringRes("Manual"),
                        ),
                    title = stringRes("Select Recipient"),
                    info = null
                ),
        )
    }
}

@PreviewScreens
@Composable
private fun EmptyAddressBookPreview() {
    ZcashTheme {
        AddressBookView(
            state =
                AddressBookState(
                    isLoading = false,
                    onBack = {},
                    items = emptyList(),
                    scanButton =
                        ButtonState(
                            text = stringRes("Scan"),
                        ),
                    manualButton =
                        ButtonState(
                            text = stringRes("Manual"),
                        ),
                    title = stringRes("Address Book"),
                    info = null
                ),
        )
    }
}

@PreviewScreens
@Composable
private fun EmptySelectRecipientPreview() {
    ZcashTheme {
        AddressBookView(
            state =
                AddressBookState(
                    isLoading = false,
                    onBack = {},
                    items =
                        listOf(
                            AddressBookItem.Title(stringRes("Title")),
                            AddressBookItem.Contact(
                                ContactListItemState(
                                    name = stringRes("Name Surname"),
                                    address = stringRes("3iY5ZSkRnevzSMu4hosasdasdasdasd12312312dasd9hw2").withStyle(),
                                    bigIcon = imageRes("NS"),
                                    isShielded = false,
                                    smallIcon = null,
                                    onClick = {}
                                )
                            ),
                            AddressBookItem.Empty
                        ),
                    scanButton =
                        ButtonState(
                            text = stringRes("Scan"),
                        ),
                    manualButton =
                        ButtonState(
                            text = stringRes("Manual"),
                        ),
                    title = stringRes("Select Recipient"),
                    info = IconButtonState(R.drawable.ic_help) {},
                ),
        )
    }
}
