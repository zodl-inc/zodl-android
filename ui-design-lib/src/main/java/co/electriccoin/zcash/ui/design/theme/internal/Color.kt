@file:Suppress("MagicNumber")

package co.electriccoin.zcash.ui.design.theme.internal

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import co.electriccoin.zcash.ui.design.theme.ExtendedColors

internal object Dark {
    val primaryColor = Color(0xFF231F20)
    val secondaryColor = Color(0xFFFFFFFF)

    val backgroundColor = primaryColor
    val gridColor = Color(0xFF272727)

    val textPrimary = secondaryColor
    val textSecondary = primaryColor
    val textDisabled = Color(0xFF4D4D4D)
    val textFieldFrame = Color(0xFFFFFFFF)
    val textFieldWarning = Color(0xFFFE5858)
    val textFieldHint = Color(0xFFB7B7B7)
    val textDescription = Color(0xFFC1C1C1)
    val textDescriptionDark = Color(0xFFFFFFFF)
    val reference = Color(0xFFFFFFFF)

    val welcomeAnimationColor = Color(0xFF181716)
    val complementaryColor = Color(0xFFF4B728)

    val primaryDividerColor = Color(0xFF4D4D4D)
    val secondaryDividerColor = Color(0xFFFFFFFF)
    val tertiaryDividerColor = Color(0xFF4D4D4D)

    val panelBackgroundColor = Color(0xFF262324)
    val panelBackgroundColorActive = Color(0xFF000000)

    val layoutStroke = Color(0xFFFFFFFF)
    val layoutStrokeSecondary = Color(0xFFDDDDDD)
    val cameraDisabledBackgroundColor = Color(0xFF000000)
    val cameraDisabledFrameColor = Color(0xFF5E5C5C)

    val primaryButtonColors = DarkPrimaryButtonColors()
    val secondaryButtonColors = DarkSecondaryButtonColors()
    val tertiaryButtonColors = DarkTertiaryButtonColors()

    val circularProgressBarSmall = Color(0xFFFFFFFF)
    val circularProgressBarSmallDark = Color(0xFFFFFFFF)
    val circularProgressBarScreen = Color(0xFFFFFFFF)
    val linearProgressBarTrack = Color(0xFFDDDDDD)
    val linearProgressBarBackground = complementaryColor

    val overlay = Color(0x22000000)
    val overlayProgressBar = Color(0xFFFFFFFF)

    val historyBackgroundColor = Color(0xFF262324)
    val historyRedColor = textFieldWarning
    val historySyncingColor = Color(0xFF181716)
    val historyMessageBubbleColor = Color(0xFF000000)
    val historyMessageBubbleStrokeColor = Color(0xFF000000)

    val topAppBarColors = DarkTopAppBarColors()
    val transparentTopAppBarColors = TransparentTopAppBarColors()
}

internal object Light {
    val primaryColor = Color(0xFFFFFFFF)
    val secondaryColor = Color(0xFF000000)

    val backgroundColor = primaryColor
    val gridColor = Color(0xFFFBFBFB)

    val textPrimary = secondaryColor
    val textSecondary = primaryColor
    val textDisabled = Color(0xFFB7B7B7)
    val textFieldFrame = Color(0xFF000000)
    val textFieldWarning = Color(0xFFF40202)
    val textFieldHint = Color(0xFFB7B7B7)
    val textDescription = Color(0xFF777777)
    val textDescriptionDark = Color(0xFF4D4D4D)
    val reference = Color(0xFF000000)

    val welcomeAnimationColor = Color(0xFF231F20)
    val complementaryColor = Color(0xFFF4B728)

    val primaryDividerColor = Color(0xFFDDDDDD)
    val secondaryDividerColor = Color(0xFF000000)
    val tertiaryDividerColor = Color(0xFF000000)

    val panelBackgroundColor = Color(0xFFEBEBEB)
    val panelBackgroundColorActive = Color(0xFFFFFFFF)

    val layoutStroke = Color(0xFF000000)
    val layoutStrokeSecondary = Color(0xFFDDDDDD)
    val cameraDisabledBackgroundColor = Color(0xFF5E5C5C)
    val cameraDisabledFrameColor = Color(0xFFFFFFFF)

    val primaryButtonColors = LightPrimaryButtonColors()
    val secondaryButtonColors = LightSecondaryButtonColors()
    val tertiaryButtonColors = LightTertiaryButtonColors()

    val circularProgressBarSmall = Color(0xFF8B8A8A)
    val circularProgressBarSmallDark = textPrimary
    val circularProgressBarScreen = Color(0xFF000000)
    val linearProgressBarTrack = Color(0xFFDDDDDD)
    val linearProgressBarBackground = complementaryColor

    val overlay = Color(0x22000000)
    val overlayProgressBar = Color(0xFFFFFFFF)

    val historyBackgroundColor = Color(0xFFF6F6F6)
    val historyRedColor = textFieldWarning
    val historySyncingColor = Color(0xFFEBEBEB)
    val historyMessageBubbleColor = Color(0xFFDDDDDD)
    val historyMessageBubbleStrokeColor = Color(0xFF000000)

    val topAppBarColors = LightTopAppBarColors()
    val transparentTopAppBarColors = TransparentTopAppBarColors()
}

internal val DarkColorPalette =
    darkColorScheme(
        // Our colors intentionally use a different naming than the ones from MaterialTheme
        primary = Dark.textPrimary,
        secondary = Dark.secondaryColor,
        onPrimary = Dark.textPrimary,
        onSecondary = Dark.textSecondary,
        surface = Dark.backgroundColor,
        onSurface = Dark.textPrimary,
        background = Dark.backgroundColor,
        onBackground = Dark.textPrimary,
    )

internal val LightColorPalette =
    lightColorScheme(
        // Our colors intentionally use a different naming than the ones from MaterialTheme
        primary = Light.textPrimary,
        secondary = Light.secondaryColor,
        onPrimary = Light.textPrimary,
        onSecondary = Light.textSecondary,
        surface = Light.backgroundColor,
        onSurface = Light.textPrimary,
        background = Light.backgroundColor,
        onBackground = Light.textPrimary,
    )

internal val DarkExtendedColorPalette =
    ExtendedColors(
        primaryColor = Dark.primaryColor,
        secondaryColor = Dark.secondaryColor,
        backgroundColor = Dark.backgroundColor,
        gridColor = Dark.gridColor,
        circularProgressBarSmall = Dark.circularProgressBarSmall,
        circularProgressBarSmallDark = Dark.circularProgressBarSmallDark,
        circularProgressBarScreen = Dark.circularProgressBarScreen,
        linearProgressBarTrack = Dark.linearProgressBarTrack,
        linearProgressBarBackground = Dark.linearProgressBarBackground,
        textPrimary = Dark.textPrimary,
        textSecondary = Dark.textSecondary,
        textDisabled = Dark.textDisabled,
        textFieldFrame = Dark.textFieldFrame,
        textFieldWarning = Dark.textFieldWarning,
        textFieldHint = Dark.textFieldHint,
        textDescription = Dark.textDescription,
        textDescriptionDark = Dark.textDescriptionDark,
        layoutStroke = Dark.layoutStroke,
        layoutStrokeSecondary = Dark.layoutStrokeSecondary,
        overlay = Dark.overlay,
        overlayProgressBar = Dark.overlayProgressBar,
        reference = Dark.reference,
        welcomeAnimationColor = Dark.welcomeAnimationColor,
        complementaryColor = Dark.complementaryColor,
        primaryDividerColor = Dark.primaryDividerColor,
        secondaryDividerColor = Dark.secondaryDividerColor,
        tertiaryDividerColor = Dark.tertiaryDividerColor,
        panelBackgroundColor = Dark.panelBackgroundColor,
        panelBackgroundColorActive = Dark.panelBackgroundColorActive,
        cameraDisabledBackgroundColor = Dark.cameraDisabledBackgroundColor,
        cameraDisabledFrameColor = Dark.cameraDisabledFrameColor,
        historyBackgroundColor = Dark.historyBackgroundColor,
        historyRedColor = Dark.historyRedColor,
        historySyncingColor = Dark.historySyncingColor,
        historyMessageBubbleColor = Dark.historyMessageBubbleColor,
        historyMessageBubbleStrokeColor = Dark.historyMessageBubbleStrokeColor,
        topAppBarColors = Dark.topAppBarColors,
        transparentTopAppBarColors = Dark.transparentTopAppBarColors,
        primaryButtonColors = Dark.primaryButtonColors,
        secondaryButtonColors = Dark.secondaryButtonColors,
        tertiaryButtonColors = Dark.tertiaryButtonColors,
    )

internal object OledDark {
    val backgroundColor = Color(0xFF000000)
    val panelBackgroundColor = Color(0xFF231F20)
    val historyBackgroundColor = Color(0xFF231F20)
}

internal val OledColorPalette =
    DarkColorPalette.copy(
        surface = OledDark.backgroundColor,
        background = OledDark.backgroundColor,
    )

internal val OledExtendedColorPalette =
    DarkExtendedColorPalette.copy(
        backgroundColor = OledDark.backgroundColor,
        panelBackgroundColor = OledDark.panelBackgroundColor,
        historyBackgroundColor = OledDark.historyBackgroundColor,
        topAppBarColors = Dark.topAppBarColors.copyColors(containerColor = OledDark.backgroundColor),
    )

internal val LightExtendedColorPalette =
    ExtendedColors(
        primaryColor = Light.primaryColor,
        secondaryColor = Light.secondaryColor,
        backgroundColor = Light.backgroundColor,
        gridColor = Light.gridColor,
        circularProgressBarScreen = Light.circularProgressBarScreen,
        circularProgressBarSmall = Light.circularProgressBarSmall,
        circularProgressBarSmallDark = Light.circularProgressBarSmallDark,
        linearProgressBarTrack = Light.linearProgressBarTrack,
        linearProgressBarBackground = Light.linearProgressBarBackground,
        textPrimary = Light.textPrimary,
        textSecondary = Light.textSecondary,
        textDisabled = Light.textDisabled,
        textFieldFrame = Light.textFieldFrame,
        textFieldWarning = Light.textFieldWarning,
        textFieldHint = Light.textFieldHint,
        textDescription = Light.textDescription,
        textDescriptionDark = Light.textDescriptionDark,
        layoutStroke = Light.layoutStroke,
        layoutStrokeSecondary = Light.layoutStrokeSecondary,
        overlay = Light.overlay,
        overlayProgressBar = Light.overlayProgressBar,
        reference = Light.reference,
        welcomeAnimationColor = Light.welcomeAnimationColor,
        complementaryColor = Light.complementaryColor,
        primaryDividerColor = Light.primaryDividerColor,
        secondaryDividerColor = Light.secondaryDividerColor,
        tertiaryDividerColor = Light.tertiaryDividerColor,
        panelBackgroundColor = Light.panelBackgroundColor,
        panelBackgroundColorActive = Light.panelBackgroundColorActive,
        cameraDisabledBackgroundColor = Light.cameraDisabledBackgroundColor,
        cameraDisabledFrameColor = Light.cameraDisabledFrameColor,
        historyBackgroundColor = Light.historyBackgroundColor,
        historyRedColor = Light.historyRedColor,
        historySyncingColor = Light.historySyncingColor,
        historyMessageBubbleColor = Light.historyMessageBubbleColor,
        historyMessageBubbleStrokeColor = Light.historyMessageBubbleStrokeColor,
        topAppBarColors = Light.topAppBarColors,
        transparentTopAppBarColors = Light.transparentTopAppBarColors,
        primaryButtonColors = Light.primaryButtonColors,
        secondaryButtonColors = Light.secondaryButtonColors,
        tertiaryButtonColors = Light.tertiaryButtonColors,
    )

@Suppress("CompositionLocalAllowlist")
internal val LocalExtendedColors =
    staticCompositionLocalOf {
        ExtendedColors(
            primaryColor = Color.Unspecified,
            secondaryColor = Color.Unspecified,
            backgroundColor = Color.Unspecified,
            gridColor = Color.Unspecified,
            circularProgressBarScreen = Color.Unspecified,
            circularProgressBarSmall = Color.Unspecified,
            circularProgressBarSmallDark = Color.Unspecified,
            linearProgressBarTrack = Color.Unspecified,
            linearProgressBarBackground = Color.Unspecified,
            textPrimary = Color.Unspecified,
            textSecondary = Color.Unspecified,
            textDisabled = Color.Unspecified,
            textFieldHint = Color.Unspecified,
            textFieldWarning = Color.Unspecified,
            textFieldFrame = Color.Unspecified,
            textDescription = Color.Unspecified,
            textDescriptionDark = Color.Unspecified,
            layoutStroke = Color.Unspecified,
            layoutStrokeSecondary = Color.Unspecified,
            overlay = Color.Unspecified,
            overlayProgressBar = Color.Unspecified,
            reference = Color.Unspecified,
            welcomeAnimationColor = Color.Unspecified,
            complementaryColor = Color.Unspecified,
            primaryDividerColor = Color.Unspecified,
            secondaryDividerColor = Color.Unspecified,
            tertiaryDividerColor = Color.Unspecified,
            panelBackgroundColor = Color.Unspecified,
            panelBackgroundColorActive = Color.Unspecified,
            cameraDisabledBackgroundColor = Color.Unspecified,
            cameraDisabledFrameColor = Color.Unspecified,
            historyBackgroundColor = Color.Unspecified,
            historyRedColor = Color.Unspecified,
            historySyncingColor = Color.Unspecified,
            historyMessageBubbleColor = Color.Unspecified,
            historyMessageBubbleStrokeColor = Color.Unspecified,
            topAppBarColors = DefaultTopAppBarColors(),
            transparentTopAppBarColors = DefaultTopAppBarColors(),
            primaryButtonColors = DefaultButtonColors(),
            secondaryButtonColors = DefaultButtonColors(),
            tertiaryButtonColors = DefaultButtonColors(),
        )
    }
