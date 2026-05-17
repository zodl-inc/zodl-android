@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.internal.SecondaryTypography
import co.electriccoin.zcash.ui.design.theme.internal.TopAppBarColors

@Preview(showBackground = true)
@Composable
private fun TopAppBarTextComposablePreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            SmallTopAppBar(
                navigationAction = {
                    TopAppBarBackNavigation(
                        onBack = {},
                        backText = "BACK",
                        backContentDescriptionText = "BACK"
                    )
                },
                titleText = "Screen A"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopAppBarTextDarkComposablePreview() {
    ZcashTheme(forceDarkMode = true) {
        BlankSurface {
            SmallTopAppBar(
                navigationAction = {
                    TopAppBarBackNavigation(
                        onBack = {},
                        backText = "BACK",
                        backContentDescriptionText = "BACK"
                    )
                },
                titleText = "Screen A"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopAppBarTextRestoringComposablePreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            SmallTopAppBar(
                navigationAction = {
                    TopAppBarBackNavigation(
                        onBack = {},
                        backText = "BACK",
                        backContentDescriptionText = "BACK"
                    )
                },
                titleText = "Screen A",
                subTitle = "[RESTORING YOUR WALLET…]"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopAppBarTextRestoringLongComposablePreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            SmallTopAppBar(
                navigationAction = {
                    TopAppBarBackNavigation(
                        onBack = {},
                        backText = "BACK",
                        backContentDescriptionText = "BACK"
                    )
                },
                subTitle = "[RESTORING YOUR WALLET LONG TEXT…]",
                titleText = "Screen A",
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopAppBarLogoComposablePreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            SmallTopAppBar(
                navigationAction = {
                    TopAppBarBackNavigation(
                        onBack = {},
                        backText = "BACK",
                        backContentDescriptionText = "BACK"
                    )
                },
                showTitleLogo = true
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopAppBarLogoRestoringComposablePreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            SmallTopAppBar(
                navigationAction = {
                    TopAppBarBackNavigation(
                        onBack = {},
                        backText = "BACK",
                        backContentDescriptionText = "BACK"
                    )
                },
                showTitleLogo = true,
                subTitle = "[RESTORING YOUR WALLET…]"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopAppBarLogoRestoringDarkComposablePreview() {
    ZcashTheme(forceDarkMode = true) {
        BlankSurface {
            SmallTopAppBar(
                navigationAction = {
                    TopAppBarBackNavigation(
                        onBack = {},
                        backText = "BACK",
                        backContentDescriptionText = "BACK"
                    )
                },
                showTitleLogo = true,
                subTitle = "[RESTORING YOUR WALLET…]"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopAppBarRegularMenuComposablePreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            SmallTopAppBar(
                titleText = "Screen B",
                regularActions = {
                    TopBarRegularMenuExample(
                        actionOneCallback = {},
                        actionTwoCallback = {}
                    )
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopAppBarOneVisibleActionMenuComposablePreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            SmallTopAppBar(
                titleText = "Screen C",
                navigationAction = {
                    TopAppBarBackNavigation(
                        onBack = {},
                        backText = "BACK",
                        backContentDescriptionText = "BACK"
                    )
                },
                regularActions = {
                    TopBarOneVisibleActionMenuExample(
                        actionCallback = {}
                    )
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopAppBarHamburgerMenuComposablePreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            SmallTopAppBar(
                titleText = "Screen D",
                hamburgerMenuActions = {
                    TopBarHamburgerMenuExample(
                        actionCallback = {}
                    )
                },
                navigationAction = {
                    TopAppBarBackNavigation(
                        onBack = {},
                        backText = "BACK",
                        backContentDescriptionText = "BACK"
                    )
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopAppBarHamburgerPlusActionComposablePreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            SmallTopAppBar(
                titleText = "Screen E",
                hamburgerMenuActions = {
                    TopBarHamburgerMenuExample(
                        actionCallback = {}
                    )
                },
                regularActions = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Content description text"
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun TopBarHamburgerMenuExample(
    modifier: Modifier = Modifier,
    actionCallback: () -> Unit
) {
    Column(
        modifier = modifier
    ) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        IconButton(
            onClick = {
                expanded = true
                actionCallback()
            }
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_hamburger_menu),
                contentDescription = "Content description text"
            )
        }
    }
}

@Composable
private fun TopBarRegularMenuExample(
    actionOneCallback: () -> Unit,
    actionTwoCallback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
    ) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Content description text"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Action One") },
                onClick = {
                    actionOneCallback()
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Action Two") },
                onClick = {
                    actionTwoCallback()
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun TopBarOneVisibleActionMenuExample(
    modifier: Modifier = Modifier,
    actionCallback: () -> Unit
) {
    Reference(
        text = "Action 1",
        onClick = actionCallback,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(all = ZcashTheme.dimens.spacingDefault)
    )
}

@Composable
fun TopAppBarBackNavigation(
    backContentDescriptionText: String? = null,
    painter: Painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
    backText: String? = null,
    onBack: () -> Unit
) {
    Row(
        modifier =
            Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(ZcashTheme.dimens.regularRippleEffectCorner))
                .clickable { onBack() }
                .padding(all = ZcashTheme.dimens.spacingMid),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painter,
            contentDescription = backContentDescriptionText,
        )

        backText?.let {
            Spacer(modifier = Modifier.size(size = ZcashTheme.dimens.spacingSmall))

            Text(text = backText)
        }
    }
}

@Composable
fun TopAppBarHideBalancesNavigation(
    iconVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
fun SmallTopAppBar(
    modifier: Modifier = Modifier,
    colors: TopAppBarColors = ZcashTheme.colors.topAppBarColors,
    hamburgerMenuActions: (@Composable RowScope.() -> Unit)? = null,
    navigationAction: @Composable () -> Unit = {},
    regularActions: (@Composable RowScope.() -> Unit)? = null,
    subTitle: String? = null,
    showTitleLogo: Boolean = false,
    titleText: String? = null,
    titleStyle: TextStyle = SecondaryTypography.headlineSmall,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    CenterAlignedTopAppBar(
        windowInsets = windowInsets,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var restoringSpacerHeight: Dp = 0.dp

                if (titleText != null) {
                    Text(
                        text = titleText.uppercase(),
                        style = titleStyle,
                        color = colors.titleColor,
                    )
                    restoringSpacerHeight = ZcashTheme.dimens.spacingTiny
                } else if (showTitleLogo) {
                    Icon(
                        painter = painterResource(id = R.drawable.zashi_text_logo),
                        contentDescription = null,
                        tint = colors.titleColor,
                        modifier = Modifier.height(ZcashTheme.dimens.topAppBarZcashLogoHeight)
                    )
                    restoringSpacerHeight = ZcashTheme.dimens.spacingSmall
                }

                if (subTitle != null) {
                    Spacer(modifier = Modifier.height(restoringSpacerHeight))

                    @Suppress("MagicNumber")
                    Text(
                        text = subTitle.uppercase(),
                        style = ZcashTheme.extendedTypography.restoringTopAppBarStyle,
                        color = colors.subTitleColor,
                        modifier = Modifier.fillMaxWidth(0.75f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            navigationAction()
        },
        actions = {
            regularActions?.invoke(this)
            hamburgerMenuActions?.invoke(this)
        },
        colors = colors.toMaterialTopAppBarColors(),
        modifier =
            Modifier
                .testTag(CommonTag.TOP_APP_BAR)
                .then(modifier)
    )
}

@Composable
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
fun SmallTopAppBar(
    modifier: Modifier = Modifier,
    colors: TopAppBarColors = ZcashTheme.colors.topAppBarColors,
    hamburgerMenuActions: (@Composable RowScope.() -> Unit)? = null,
    navigationAction: @Composable () -> Unit = {},
    regularActions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    CenterAlignedTopAppBar(
        windowInsets = windowInsets,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (content != null) content()
            }
        },
        navigationIcon = {
            navigationAction()
        },
        actions = {
            regularActions?.invoke(this)
            hamburgerMenuActions?.invoke(this)
        },
        colors = colors.toMaterialTopAppBarColors(),
        modifier =
            Modifier
                .testTag(CommonTag.TOP_APP_BAR)
                .then(modifier)
    )
}
