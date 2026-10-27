package co.electriccoin.zcash.ui.design.theme.internal

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import co.electriccoin.zcash.ui.design.R

private val provider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )

// We use bestEffort here to be able to get the closest font weight, if accidentally use
// an unspecified font weight and not the default one.
private val InterFont = GoogleFont(name = "Inter", bestEffort = true)
private val ArchivoFont = GoogleFont(name = "Archivo", bestEffort = true)

private val InterFontFamily =
    FontFamily(
        // W400
        Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Normal),
        // W500
        Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Medium),
        // W600
        Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.SemiBold),
        // W700
        Font(googleFont = InterFont, fontProvider = provider, weight = FontWeight.Bold)
    )
private val ArchivoFontFamily =
    FontFamily(
        // W400
        Font(googleFont = ArchivoFont, fontProvider = provider, weight = FontWeight.Normal),
        // W500
        Font(googleFont = ArchivoFont, fontProvider = provider, weight = FontWeight.Medium),
        // W600
        Font(googleFont = ArchivoFont, fontProvider = provider, weight = FontWeight.SemiBold),
        // W700
        Font(googleFont = ArchivoFont, fontProvider = provider, weight = FontWeight.Bold)
    )

// If you change this definition of our Typography, don't forget to check if you use only
// the defined font weights above, otherwise the closest one will be used.
internal val PrimaryTypography =
    Typography(
        headlineLarge =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
        titleSmall =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            ),
        bodyLarge =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
            ),
        bodySmall =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp
            ),
        labelLarge =
            TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
            )
    )

internal val SecondaryTypography =
    Typography(
        headlineLarge =
            TextStyle(
                fontFamily = ArchivoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp
            ),
        headlineMedium =
            TextStyle(
                fontFamily = ArchivoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 25.sp,
                textAlign = TextAlign.Center
            ),
        headlineSmall =
            TextStyle(
                fontFamily = ArchivoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            ),
        bodyLarge =
            TextStyle(
                fontFamily = ArchivoFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
            ),
        bodySmall =
            TextStyle(
                fontFamily = ArchivoFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            ),
        labelLarge =
            TextStyle(
                fontFamily = ArchivoFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
            ),
        labelMedium =
            TextStyle(
                fontFamily = ArchivoFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp
            )
    )

data class Typography(
    val primary: Typography,
    val secondary: Typography
)

data class BalanceWidgetTextStyles(
    val first: TextStyle,
    val second: TextStyle,
    val third: TextStyle,
    val fourth: TextStyle,
)

data class BalanceSingleTextStyles(
    val first: TextStyle,
    val second: TextStyle,
)

data class TransactionItemTextStyles(
    val titleRegular: TextStyle,
    val titleRunning: TextStyle,
    val titleFailed: TextStyle,
    val addressCollapsed: TextStyle,
    val valueFirstPart: TextStyle,
    val valueSecondPart: TextStyle,
    val content: TextStyle,
    val contentItalic: TextStyle,
    val contentMedium: TextStyle,
    val contentUnderline: TextStyle,
    val contentLineThrough: TextStyle,
    val feeFirstPart: TextStyle,
    val feeSecondPart: TextStyle,
)

data class ExtendedTypography(
    // Grouping balances text styles to a wrapper class for BalanceWidget
    val balanceWidgetStyles: BalanceWidgetTextStyles,
    // Grouping balances text styles to a wrapper class for single balance use case
    val balanceSingleStyles: BalanceSingleTextStyles,
    val addressStyle: TextStyle,
    val aboutText: TextStyle,
    val buttonText: TextStyle,
    val buttonTextSmall: TextStyle,
    val checkboxText: TextStyle,
    val securityWarningText: TextStyle,
    val footnote: TextStyle,
    val textFieldHint: TextStyle,
    val textFieldValue: TextStyle,
    val textFieldBirthday: TextStyle,
    val textNavTab: TextStyle,
    val referenceSmall: TextStyle,
    val radioButton: TextStyle,
    // Grouping transaction item text styles to a wrapper class
    val transactionItemStyles: TransactionItemTextStyles,
    val deleteWalletWarnStyle: TextStyle,
)

@Suppress("CompositionLocalAllowlist")
val LocalTypographies =
    staticCompositionLocalOf {
        Typography(
            primary = PrimaryTypography,
            secondary = SecondaryTypography
        )
    }

@Suppress("CompositionLocalAllowlist")
val LocalExtendedTypography =
    staticCompositionLocalOf {
        ExtendedTypography(
            // Note: the order here matters, be careful when reordering
            balanceWidgetStyles =
                BalanceWidgetTextStyles(
                    first =
                        SecondaryTypography.headlineLarge.copy(
                            fontSize = 42.sp,
                            fontWeight = FontWeight.SemiBold,
                            // Remove font padding to achieve desired design
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                    second =
                        SecondaryTypography.headlineSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                    third =
                        PrimaryTypography.bodySmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                    fourth =
                        PrimaryTypography.bodySmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                ),
            balanceSingleStyles =
                BalanceSingleTextStyles(
                    first =
                        SecondaryTypography.bodySmall.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                    second =
                        SecondaryTypography.bodySmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                ),
            addressStyle =
                SecondaryTypography.bodyLarge.copy(),
            aboutText =
                PrimaryTypography.bodyLarge.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
            buttonText =
                PrimaryTypography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
            buttonTextSmall =
                PrimaryTypography.bodySmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                ),
            checkboxText =
                PrimaryTypography.bodyMedium.copy(
                    fontSize = 14.sp
                ),
            securityWarningText =
                PrimaryTypography.bodySmall.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
            footnote =
                PrimaryTypography.bodySmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
            textFieldHint =
                PrimaryTypography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 15.73.sp,
                    fontWeight = FontWeight.Normal
                ),
            textFieldValue =
                PrimaryTypography.bodyLarge.copy(
                    fontSize = 17.sp,
                ),
            textFieldBirthday = SecondaryTypography.headlineMedium.copy(),
            textNavTab =
                SecondaryTypography.labelSmall.copy(
                    fontSize = 13.sp
                ),
            referenceSmall =
                PrimaryTypography.bodySmall.copy(
                    fontSize = 13.sp
                ),
            radioButton =
                PrimaryTypography.bodySmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
            transactionItemStyles =
                TransactionItemTextStyles(
                    titleRegular =
                        PrimaryTypography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                    titleRunning =
                        PrimaryTypography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontStyle = FontStyle.Italic
                        ),
                    titleFailed =
                        PrimaryTypography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textDecoration = TextDecoration.LineThrough
                        ),
                    addressCollapsed =
                        SecondaryTypography.bodySmall.copy(
                            fontSize = 13.sp
                        ),
                    valueFirstPart =
                        PrimaryTypography.bodySmall.copy(
                            fontSize = 13.sp
                        ),
                    valueSecondPart =
                        PrimaryTypography.bodySmall.copy(
                            fontSize = 8.sp
                        ),
                    content =
                        PrimaryTypography.bodySmall.copy(
                            fontSize = 13.sp
                        ),
                    contentItalic =
                        PrimaryTypography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic
                        ),
                    contentMedium =
                        PrimaryTypography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                    contentUnderline =
                        PrimaryTypography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            textDecoration = TextDecoration.Underline
                        ),
                    contentLineThrough =
                        PrimaryTypography.bodySmall.copy(
                            fontSize = 13.sp,
                            textDecoration = TextDecoration.LineThrough
                        ),
                    feeFirstPart =
                        PrimaryTypography.bodySmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                    feeSecondPart =
                        PrimaryTypography.bodySmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                ),
            deleteWalletWarnStyle =
                PrimaryTypography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
        )
    }
