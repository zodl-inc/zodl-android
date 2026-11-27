@file:Suppress("PropertyName", "ConstructorParameterNaming")

package co.electriccoin.zcash.ui.design.theme.colors

import androidx.compose.ui.graphics.Color

data class ZashiColorsInternal(
    val Surfaces: Surfaces,
    val Text: Text,
    val Btns: Btns,
    val Avatars: Avatars,
    val Sliders: Sliders,
    val Inputs: Inputs,
    val Accordion: Accordion,
    val Switcher: Switcher,
    val Toggles: Toggles,
    val Tags: Tags,
    val Dropdowns: Dropdowns,
    val Tabs: Tabs,
    val Checkboxes: Checkboxes,
    val Loading: Loading,
    val Modals: Modals,
    val HintTooltips: HintTooltips,
    val TwoFA: TwoFA,
    val Utility: Utility,
    val Accents: Accents,
    val Transparent: Transparent,
    val NoTheme: NoTheme
)

data class Surfaces(
    val bgPrimary: Color,
    val bgAdjust: Color,
    val bgSecondary: Color,
    val bgTertiary: Color,
    val bgQuaternary: Color,
    val strokePrimary: Color,
    val strokeSecondary: Color,
    val bgAlt: Color,
    val bgHide: Color,
    val brandBg: Color,
    val brandFg: Color,
    val divider: Color
)

data class Text(
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textQuaternary: Color,
    val textSupport: Color,
    val textDisabled: Color,
    val textError: Color,
    val textLink: Color,
    val textLight: Color,
    val textLightSupport: Color
)

data class Btns(
    val Brand: BtnBrand,
    val Secondary: BtnSecondary,
    val Tertiary: BtnTertiary,
    val Quaternary: BtnQuaternary,
    val Destructive1: BtnDestructive1,
    val Destructive2: BtnDestructive2,
    val Primary: BtnPrimary,
    val Ghost: BtnGhost
)

data class BtnBrand(
    val btnBrandBg: Color,
    val btnBrandBgHover: Color,
    val btnBrandFg: Color,
    val btnBrandFgHover: Color,
    val btnBrandBgDisabled: Color,
    val btnBrandFgDisabled: Color
)

data class BtnSecondary(
    val btnSecondaryBg: Color,
    val btnSecondaryBgHover: Color,
    val btnSecondaryFg: Color,
    val btnSecondaryFgHover: Color,
    val btnSecondaryBorder: Color,
    val btnSecondaryBorderHover: Color,
    val btnSecondaryBgDisabled: Color,
    val btnSecondaryFgDisabled: Color
)

data class BtnTertiary(
    val btnTertiaryBg: Color,
    val btnTertiaryBgHover: Color,
    val btnTertiaryFg: Color,
    val btnTertiaryFgHover: Color,
    val btnTertiaryBgDisabled: Color,
    val btnTertiaryFgDisabled: Color
)

data class BtnQuaternary(
    val btnQuartBg: Color,
    val btnQuartBgHover: Color,
    val btnQuartFg: Color,
    val btnQuartFgHover: Color,
    val btnQuartBgDisabled: Color,
    val btnQuartFgDisabled: Color
)

data class BtnDestructive1(
    val btnDestroy1Bg: Color,
    val btnDestroy1BgHover: Color,
    val btnDestroy1Fg: Color,
    val btnDestroy1FgHover: Color,
    val btnDestroy1Border: Color,
    val btnDestroy1BorderHover: Color,
    val btnDestroy1BgDisabled: Color,
    val btnDestroy1FgDisabled: Color
)

data class BtnDestructive2(
    val btnDestroy2Bg: Color,
    val btnDestroy2BgHover: Color,
    val btnDestroy2Fg: Color,
    val btnDestroy2BgDisabled: Color,
    val btnDestroy2FgDisabled: Color
)

data class BtnPrimary(
    val btnPrimaryBg: Color,
    val btnPrimaryBgHover: Color,
    val btnPrimaryFg: Color,
    val btnPrimaryBgDisabled: Color,
    val btnBoldFgDisabled: Color
)

data class BtnGhost(
    val btnGhostBg: Color,
    val btnGhostBgHover: Color,
    val btnGhostFg: Color,
    val btnGhostBgDisabled: Color,
    val btnGhostFgDisabled: Color
)

data class Avatars(
    val avatarProfileBorder: Color,
    val avatarBg: Color,
    val avatarBgSecondary: Color,
    val avatarStatus: Color,
    val avatarTextFg: Color,
    val avatarBadgeBg: Color,
    val avatarBadgeFg: Color
)

data class Sliders(
    val sliderHandleBorder: Color,
    val sliderHandleBg: Color
)

data class Inputs(
    val Default: InputDefault,
    val Hover: InputHover,
    val Filled: InputFilled,
    val Focused: InputFocused,
    val Disabled: InputDisabled,
    val ErrorDefault: InputErrorDefault,
    val ErrorHover: InputErrorHover,
    val ErrorFilled: InputErrorFilled,
    val ErrorFocused: InputErrorFocused
)

data class InputDefault(
    val bg: Color,
    val bgAlt: Color,
    val label: Color,
    val text: Color,
    val hint: Color,
    val required: Color,
    val icon: Color,
    val stroke: Color
)

data class InputHover(
    val bg: Color,
    val bgAlt: Color,
    val asideBg: Color,
    val stroke: Color,
    val label: Color,
    val text: Color,
    val hint: Color,
    val icon: Color,
    val required: Color
)

data class InputFilled(
    val bg: Color,
    val bgAlt: Color,
    val asideBg: Color,
    val stroke: Color,
    val label: Color,
    val text: Color,
    val hint: Color,
    val icon: Color,
    val iconMain: Color,
    val required: Color
)

data class InputFocused(
    val bg: Color,
    val asideBg: Color,
    val stroke: Color,
    val stroke2: Color,
    val label: Color,
    val text: Color,
    val hint: Color,
    val icon: Color,
    val iconMain: Color,
    val defaultRequired: Color
)

data class InputDisabled(
    val bg: Color,
    val stroke: Color,
    val label: Color,
    val text: Color,
    val hint: Color,
    val icon: Color,
    val iconMain: Color,
    val required: Color
)

data class InputErrorDefault(
    val bg: Color,
    val bgAlt: Color,
    val label: Color,
    val text: Color,
    val textAside: Color,
    val textMain: Color,
    val hint: Color,
    val icon: Color,
    val iconMain: Color,
    val stroke: Color,
    val strokeAlt: Color,
    val dropdown: Color
)

data class InputErrorHover(
    val bg: Color,
    val bgAlt: Color,
    val label: Color,
    val text: Color,
    val textAside: Color,
    val textMain: Color,
    val hint: Color,
    val icon: Color,
    val iconMain: Color,
    val stroke: Color,
    val strokeAlt: Color,
    val dropdown: Color
)

data class InputErrorFilled(
    val bg: Color,
    val bgAlt: Color,
    val label: Color,
    val text: Color,
    val textAside: Color,
    val hint: Color,
    val icon: Color,
    val iconMain: Color,
    val stroke: Color,
    val strokeAlt: Color,
    val dropdown: Color
)

data class InputErrorFocused(
    val bg: Color,
    val bgAlt: Color,
    val label: Color,
    val text: Color,
    val textAside: Color,
    val hint: Color,
    val icon: Color,
    val iconMain: Color,
    val stroke: Color,
    val strokeAlt: Color,
    val dropdown: Color
)

data class Accordion(
    val xBtnDefaultFg: Color,
    val xBtnHoverBg: Color,
    val xBtnOnHoverBg: Color,
    val xBtnHoverFg: Color,
    val xBtnFocusBg: Color,
    val xBtnFocusFg: Color,
    val xBtnFocusStroke: Color,
    val xBtnDisabledBg: Color,
    val xBtnDisabledFg: Color,
    val defaultBg: Color,
    val defaultStroke: Color,
    val defaultIcon: Color,
    val focusStroke: Color,
    val expandedBg: Color,
    val expandedHoverBg: Color,
    val expandedStroke: Color,
    val dividers: Color,
    val expandedFocusStroke: Color
)

data class Switcher(
    val defaultText: Color,
    val defaultTagBg: Color,
    val defaultIcon: Color,
    val hoverBg: Color,
    val hoverTagBg: Color,
    val hoverIcon: Color,
    val hoverText: Color,
    val hoverTagText: Color,
    val selectedBg: Color,
    val selectedIcon: Color,
    val selectedText: Color,
    val selectedTagBg: Color,
    val selectedStroke: Color,
    val disabledText: Color,
    val disabledIcon: Color,
    val disabledTagBg: Color,
    val surfacePrimary: Color
)

data class Toggles(
    val tgDefaultBg: Color,
    val tgDefaultFg: Color,
    val tgActiveBg: Color,
    val tgActiveFg: Color,
    val tgDefaultHoverBg: Color,
    val tgDefaultHoverFg: Color,
    val tgActiveHoverBg: Color,
    val tgActiveHoverFg: Color,
    val tgDefaultDisabledBg: Color,
    val tgDefaultDisabledFg: Color,
    val tgActiveDisabledBg: Color,
    val tgActiveDisabledFg: Color
)

data class Tags(
    val tcDefaultFg: Color,
    val tcHoverBg: Color,
    val tcHoverFg: Color,
    val tcCountBg: Color,
    val tcCountFg: Color,
    val statusIndicator: Color,
    val surfacePrimary: Color,
    val surfaceStroke: Color
)

data class Dropdowns(
    val Default: DropdownDefault,
    val Filled: DropdownFilled,
    val Focused: DropdownFocused,
    val Disabled: DropdownDisabled,
    val Parts: DropdownParts
)

data class DropdownDefault(
    val bg: Color,
    val label: Color,
    val text: Color,
    val hint: Color,
    val required: Color,
    val icon: Color,
    val dropdown: Color,
    val active: Color
)

data class DropdownFilled(
    val bg: Color,
    val label: Color,
    val textMain: Color,
    val textSupport: Color,
    val hint: Color,
    val required: Color,
    val icon: Color,
    val dropdown: Color,
    val active: Color
)

data class DropdownFocused(
    val bg: Color,
    val stroke: Color,
    val label: Color,
    val textMain: Color,
    val textSupport: Color,
    val hint: Color,
    val defaultRequired: Color,
    val icon: Color,
    val dropdown: Color,
    val active: Color
)

data class DropdownDisabled(
    val bg: Color,
    val stroke: Color,
    val label: Color,
    val textMain: Color,
    val textSupport: Color,
    val hint: Color,
    val required: Color,
    val icon: Color,
    val dropdown: Color,
    val active: Color
)

data class DropdownParts(
    val scrollBar: Color,
    val divider: Color,
    val lhText: Color,
    val lhBorder: Color,
    val liTextPrimary: Color,
    val liTextSecondary: Color,
    val liTextTertiary: Color,
    val liFgDisabled: Color,
    val liIconDisabled: Color,
    val liBgHover: Color,
    val statusActive: Color,
    val statusMain: Color,
    val statusDisabled: Color,
    val bgDisabled: Color
)

data class Tabs(
    val defaultText: Color,
    val defaultIcon: Color,
    val defaultTagBg: Color,
    val hoverText: Color,
    val hoverTagText: Color,
    val hoverIcon: Color,
    val hoverTagBg: Color,
    val hoverBorder: Color,
    val selectedText: Color,
    val selectedIcon: Color,
    val selectedTagBg: Color,
    val selectedBorder: Color,
    val disabledText: Color,
    val disabledIcon: Color,
    val disabledTagBg: Color,
    val disabledTagText: Color
)

data class Checkboxes(
    val boxOffBg: Color,
    val boxOffStroke: Color,
    val boxOffHoverBg: Color,
    val boxOffHoverStroke: Color,
    val boxOffDisabledBg: Color,
    val boxOffDisabledStroke: Color,
    val boxOnBg: Color,
    val boxOnFg: Color,
    val boxOnHoverBg: Color,
    val boxOnDisabledBg: Color,
    val boxOnDisabledStroke: Color,
    val boxOnDisabledFg: Color
)

data class Loading(
    val loadingBgPrimary: Color,
    val loadingBgSecondary: Color,
    val loadingFgPrimary: Color
)

data class Modals(
    val defaultBg: Color,
    val defaultFg: Color,
    val hoverBg: Color,
    val hoverFg: Color,
    val focusedBg: Color,
    val focusedStroke: Color,
    val disabledBg: Color,
    val disabledFg: Color,
    val surfacePrimary: Color,
    val surfaceStroke: Color
)

data class HintTooltips(
    val surfacePrimary: Color,
    val defaultBg: Color,
    val defaultFg: Color,
    val hoverBg: Color,
    val hoverFg: Color,
    val focusedBg: Color,
    val focusedStroke: Color,
    val disabledBg: Color,
    val disabledFg: Color
)

data class TwoFA(
    val defaultBg: Color,
    val defaultStroke: Color,
    val defaultText: Color,
    val focusedBg: Color,
    val focusedStroke: Color,
    val focusedText: Color,
    val filledBg: Color,
    val filledStroke: Color,
    val filledText: Color,
    val disabledBg: Color,
    val disabledText: Color,
    val separatorDash: Color
)

data class Utility(
    val Gray: UtilityGray,
    val SuccessGreen: UtilitySuccessGreen,
    val ErrorRed: UtilityErrorRed,
    val WarningYellow: UtilityWarningYellow,
    val HyperBlue: UtilityHyperBlue,
    val Indigo: UtilityIndigo,
    val Purple: UtilityPurple,
    val Espresso: UtilityEspresso
)

data class UtilityGray(
    val utilityGray700: Color,
    val utilityGray600: Color,
    val utilityGray500: Color,
    val utilityGray200: Color,
    val utilityGray50: Color,
    val utilityGray100: Color,
    val utilityGray400: Color,
    val utilityGray300: Color,
    val utilityGray900: Color,
    val utilityGray800: Color,
    val utilityGray950: Color
)

data class UtilitySuccessGreen(
    val utilitySuccess600: Color,
    val utilitySuccess700: Color,
    val utilitySuccess500: Color,
    val utilitySuccess200: Color,
    val utilitySuccess800: Color,
    val utilitySuccess50: Color,
    val utilitySuccess100: Color,
    val utilitySuccess400: Color,
    val utilitySuccess300: Color
)

data class UtilityErrorRed(
    val utilityError600: Color,
    val utilityError700: Color,
    val utilityError500: Color,
    val utilityError200: Color,
    val utilityError800: Color,
    val utilityError50: Color,
    val utilityError100: Color,
    val utilityError400: Color,
    val utilityError300: Color
)

data class UtilityWarningYellow(
    val utilityOrange600: Color,
    val utilityOrange700: Color,
    val utilityOrange500: Color,
    val utilityOrange200: Color,
    val utilityOrange800: Color,
    val utilityOrange900: Color,
    val utilityOrange50: Color,
    val utilityOrange100: Color,
    val utilityOrange400: Color,
    val utilityOrange300: Color
)

data class UtilityHyperBlue(
    val utilityBlueDark600: Color,
    val utilityBlueDark700: Color,
    val utilityBlueDark500: Color,
    val utilityBlueDark200: Color,
    val utilityBlueDark800: Color,
    val utilityBlueDark50: Color,
    val utilityBlueDark100: Color,
    val utilityBlueDark400: Color,
    val utilityBlueDark300: Color
)

data class UtilityIndigo(
    val utilityIndigo600: Color,
    val utilityIndigo700: Color,
    val utilityIndigo500: Color,
    val utilityIndigo200: Color,
    val utilityIndigo800: Color,
    val utilityIndigo50: Color,
    val utilityIndigo100: Color,
    val utilityIndigo400: Color,
    val utilityIndigo300: Color
)

data class UtilityPurple(
    val utilityPurple600: Color,
    val utilityPurple700: Color,
    val utilityPurple500: Color,
    val utilityPurple200: Color,
    val utilityPurple800: Color,
    val utilityPurple50: Color,
    val utilityPurple100: Color,
    val utilityPurple400: Color,
    val utilityPurple300: Color,
    val utilityPurple900: Color
)

data class UtilityEspresso(
    val utilityEspresso700: Color,
    val utilityEspresso600: Color,
    val utilityEspresso500: Color,
    val utilityEspresso200: Color,
    val utilityEspresso50: Color,
    val utilityEspresso100: Color,
    val utilityEspresso400: Color,
    val utilityEspresso300: Color,
    val utilityEspresso900: Color,
    val utilityEspresso950: Color,
    val utilityEspresso800: Color
)

data class Accents(
    val green: Color
)

data class Transparent(
    val bgPrimary: Color
)

data class NoTheme(
    val welcomeText: Color
)
