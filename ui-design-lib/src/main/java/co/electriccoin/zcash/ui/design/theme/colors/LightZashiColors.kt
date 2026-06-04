package co.electriccoin.zcash.ui.design.theme.colors

val LightZashiColorsInternal =
    ZashiColorsInternal(
        Surfaces =
            Surfaces(
                bgPrimary = Base.Bone,
                bgAdjust = Base.Bone,
                bgSecondary = Base.Concrete,
                bgTertiary = Gray.`100`,
                bgQuaternary = Gray.`200`,
                strokePrimary = Gray.`200`,
                strokeSecondary = Gray.`100`,
                bgAlt = Base.Obsidian,
                bgHide = Base.Obsidian,
                brandBg = Base.Brand,
                brandFg = Base.Obsidian,
                divider = Gray.`50`
            ),
        Text =
            Text(
                textPrimary = Base.Obsidian,
                textSecondary = Gray.`800`,
                textTertiary = Gray.`700`,
                textQuaternary = Gray.`600`,
                textSupport = Gray.`500`,
                textDisabled = Gray.`300`,
                textError = ErrorRed.`500`,
                textLink = HyperBlue.`500`,
                textLight = Gray.`25`,
                textLightSupport = Gray.`200`
            ),
        Btns =
            Btns(
                Brand =
                    BtnBrand(
                        btnBrandBg = Brand.`400`,
                        btnBrandBgHover = Brand.`300`,
                        btnBrandFg = Base.Obsidian,
                        btnBrandFgHover = Base.Obsidian,
                        btnBrandBgDisabled = Gray.`100`,
                        btnBrandFgDisabled = Gray.`500`
                    ),
                Secondary =
                    BtnSecondary(
                        btnSecondaryBg = Base.Bone,
                        btnSecondaryBgHover = Gray.`50`,
                        btnSecondaryFg = Base.Obsidian,
                        btnSecondaryFgHover = Base.Obsidian,
                        btnSecondaryBorder = Gray.`200`,
                        btnSecondaryBorderHover = Gray.`200`,
                        btnSecondaryBgDisabled = Gray.`100`,
                        btnSecondaryFgDisabled = Gray.`500`
                    ),
                Tertiary =
                    BtnTertiary(
                        btnTertiaryBg = Gray.`100`,
                        btnTertiaryBgHover = Gray.`200`,
                        btnTertiaryFg = Gray.`900`,
                        btnTertiaryFgHover = Gray.`900`,
                        btnTertiaryBgDisabled = Gray.`100`,
                        btnTertiaryFgDisabled = Gray.`500`
                    ),
                Quaternary =
                    BtnQuaternary(
                        btnQuartBg = Gray.`200`,
                        btnQuartBgHover = Gray.`300`,
                        btnQuartFg = Gray.`900`,
                        btnQuartFgHover = Gray.`900`,
                        btnQuartBgDisabled = Gray.`200`,
                        btnQuartFgDisabled = Gray.`500`
                    ),
                Destructive1 =
                    BtnDestructive1(
                        btnDestroy1Bg = Base.Bone,
                        btnDestroy1BgHover = ErrorRed.`50`,
                        btnDestroy1Fg = ErrorRed.`600`,
                        btnDestroy1FgHover = ErrorRed.`700`,
                        btnDestroy1Border = ErrorRed.`300`,
                        btnDestroy1BorderHover = ErrorRed.`300`,
                        btnDestroy1BgDisabled = Gray.`100`,
                        btnDestroy1FgDisabled = Gray.`500`
                    ),
                Destructive2 =
                    BtnDestructive2(
                        btnDestroy2Bg = ErrorRed.`600`,
                        btnDestroy2BgHover = ErrorRed.`700`,
                        btnDestroy2Fg = Base.Bone,
                        btnDestroy2BgDisabled = Gray.`100`,
                        btnDestroy2FgDisabled = Gray.`500`
                    ),
                Primary =
                    BtnPrimary(
                        btnPrimaryBg = Base.Obsidian,
                        btnPrimaryBgHover = Gray.`900`,
                        btnPrimaryFg = Base.Bone,
                        btnPrimaryBgDisabled = Gray.`100`,
                        btnBoldFgDisabled = Gray.`500`
                    ),
                Ghost =
                    BtnGhost(
                        btnGhostBg = Base.Bone,
                        btnGhostBgHover = Gray.`50`,
                        btnGhostFg = Base.Obsidian,
                        btnGhostBgDisabled = Gray.`100`,
                        btnGhostFgDisabled = Gray.`500`
                    )
            ),
        Avatars =
            Avatars(
                avatarProfileBorder = Base.Bone,
                avatarBg = Gray.`600`,
                avatarBgSecondary = Gray.`500`,
                avatarStatus = SuccessGreen.`500`,
                avatarTextFg = Base.Bone,
                avatarBadgeBg = HyperBlue.`400`,
                avatarBadgeFg = Base.Bone
            ),
        Sliders =
            Sliders(
                sliderHandleBorder = Gray.`200`,
                sliderHandleBg = Base.Bone
            ),
        Inputs =
            Inputs(
                Default =
                    InputDefault(
                        bg = Gray.`50`,
                        bgAlt = Base.Bone,
                        label = Base.Obsidian,
                        text = Gray.`600`,
                        hint = Gray.`700`,
                        required = ErrorRed.`600`,
                        icon = Gray.`400`,
                        stroke = Gray.`200`
                    ),
                Hover =
                    InputHover(
                        bg = Gray.`100`,
                        bgAlt = Base.Bone,
                        asideBg = Gray.`50`,
                        stroke = Gray.`300`,
                        label = Base.Obsidian,
                        text = Gray.`700`,
                        hint = Gray.`700`,
                        icon = Gray.`400`,
                        required = ErrorRed.`600`
                    ),
                Filled =
                    InputFilled(
                        bg = Gray.`50`,
                        bgAlt = Base.Bone,
                        asideBg = Gray.`50`,
                        stroke = Gray.`300`,
                        label = Base.Obsidian,
                        text = Gray.`900`,
                        hint = Gray.`700`,
                        icon = Gray.`400`,
                        iconMain = Gray.`500`,
                        required = ErrorRed.`600`
                    ),
                Focused =
                    InputFocused(
                        bg = Base.Bone,
                        asideBg = Gray.`50`,
                        stroke = Gray.`900`,
                        stroke2 = Gray.`300`,
                        label = Base.Obsidian,
                        text = Gray.`900`,
                        hint = Gray.`700`,
                        icon = Gray.`400`,
                        iconMain = Gray.`500`,
                        defaultRequired = ErrorRed.`600`
                    ),
                Disabled =
                    InputDisabled(
                        bg = Gray.`50`,
                        stroke = Gray.`300`,
                        label = Base.Obsidian,
                        text = Gray.`500`,
                        hint = Gray.`700`,
                        icon = Gray.`400`,
                        iconMain = Gray.`500`,
                        required = ErrorRed.`600`
                    ),
                ErrorDefault =
                    InputErrorDefault(
                        bg = Base.Bone,
                        bgAlt = Gray.`50`,
                        label = Base.Obsidian,
                        text = Gray.`600`,
                        textAside = Gray.`600`,
                        textMain = Gray.`900`,
                        hint = ErrorRed.`600`,
                        icon = ErrorRed.`500`,
                        iconMain = Gray.`500`,
                        stroke = ErrorRed.`300`,
                        strokeAlt = Gray.`300`,
                        dropdown = Gray.`400`
                    ),
                ErrorHover =
                    InputErrorHover(
                        bg = Base.Bone,
                        bgAlt = Gray.`50`,
                        label = Base.Obsidian,
                        text = Gray.`700`,
                        textAside = Gray.`600`,
                        textMain = Gray.`900`,
                        hint = ErrorRed.`600`,
                        icon = ErrorRed.`500`,
                        iconMain = Gray.`500`,
                        stroke = ErrorRed.`400`,
                        strokeAlt = Gray.`300`,
                        dropdown = Gray.`400`
                    ),
                ErrorFilled =
                    InputErrorFilled(
                        bg = Base.Bone,
                        bgAlt = Gray.`50`,
                        label = Base.Obsidian,
                        text = Gray.`900`,
                        textAside = Gray.`600`,
                        hint = ErrorRed.`600`,
                        icon = ErrorRed.`500`,
                        iconMain = Gray.`500`,
                        stroke = ErrorRed.`400`,
                        strokeAlt = Gray.`300`,
                        dropdown = Gray.`400`,
                    ),
                ErrorFocused =
                    InputErrorFocused(
                        bg = Base.Bone,
                        bgAlt = Gray.`50`,
                        label = Base.Obsidian,
                        text = Gray.`900`,
                        textAside = Gray.`600`,
                        hint = ErrorRed.`600`,
                        icon = ErrorRed.`500`,
                        iconMain = Gray.`500`,
                        stroke = ErrorRed.`500`,
                        strokeAlt = Gray.`300`,
                        dropdown = Gray.`400`
                    )
            ),
        Accordion =
            Accordion(
                xBtnDefaultFg = Gray.`600`,
                xBtnHoverBg = Gray.`50`,
                xBtnOnHoverBg = Gray.`100`,
                xBtnHoverFg = Gray.`600`,
                xBtnFocusBg = Gray.`100`,
                xBtnFocusFg = Gray.`600`,
                xBtnFocusStroke = Gray.`600`,
                xBtnDisabledBg = Gray.`50`,
                xBtnDisabledFg = Gray.`300`,
                defaultBg = Base.Bone,
                defaultStroke = Gray.`200`,
                defaultIcon = Gray.`600`,
                focusStroke = Gray.`900`,
                expandedBg = Gray.`50`,
                expandedHoverBg = Gray.`100`,
                expandedStroke = Gray.`200`,
                dividers = Gray.`200`,
                expandedFocusStroke = Gray.`900`
            ),
        Switcher =
            Switcher(
                defaultText = Gray.`800`,
                defaultTagBg = Gray.`200`,
                defaultIcon = Gray.`600`,
                hoverBg = Gray.`200`,
                hoverTagBg = Gray.`300`,
                hoverIcon = Gray.`600`,
                hoverText = Gray.`900`,
                hoverTagText = Gray.`900`,
                selectedBg = Base.Bone,
                selectedIcon = Gray.`600`,
                selectedText = Gray.`900`,
                selectedTagBg = Gray.`50`,
                selectedStroke = Gray.`200`,
                disabledText = Gray.`400`,
                disabledIcon = Gray.`400`,
                disabledTagBg = Gray.`200`,
                surfacePrimary = Gray.`100`
            ),
        Toggles =
            Toggles(
                tgDefaultBg = Gray.`100`,
                tgDefaultFg = Base.Bone,
                tgActiveBg = Base.Obsidian,
                tgActiveFg = Base.Bone,
                tgDefaultHoverBg = Gray.`200`,
                tgDefaultHoverFg = Base.Bone,
                tgActiveHoverBg = Gray.`800`,
                tgActiveHoverFg = Base.Bone,
                tgDefaultDisabledBg = Gray.`200`,
                tgDefaultDisabledFg = Gray.`100`,
                tgActiveDisabledBg = Gray.`200`,
                tgActiveDisabledFg = Gray.`100`
            ),
        Tags =
            Tags(
                tcDefaultFg = Gray.`400`,
                tcHoverBg = Gray.`50`,
                tcHoverFg = Gray.`600`,
                tcCountBg = Gray.`50`,
                tcCountFg = Gray.`700`,
                statusIndicator = SuccessGreen.`600`,
                surfacePrimary = Base.Bone,
                surfaceStroke = Gray.`300`
            ),
        Dropdowns =
            Dropdowns(
                Default =
                    DropdownDefault(
                        bg = Gray.`50`,
                        label = Base.Obsidian,
                        text = Gray.`600`,
                        hint = Gray.`700`,
                        required = ErrorRed.`600`,
                        icon = Gray.`400`,
                        dropdown = Gray.`500`,
                        active = SuccessGreen.`500`
                    ),
                Filled =
                    DropdownFilled(
                        bg = Gray.`50`,
                        label = Base.Obsidian,
                        textMain = Gray.`900`,
                        textSupport = Gray.`700`,
                        hint = Gray.`700`,
                        required = ErrorRed.`600`,
                        icon = Gray.`400`,
                        dropdown = Gray.`500`,
                        active = SuccessGreen.`500`
                    ),
                Focused =
                    DropdownFocused(
                        bg = Base.Bone,
                        stroke = Gray.`900`,
                        label = Base.Obsidian,
                        textMain = Gray.`900`,
                        textSupport = Gray.`700`,
                        hint = Gray.`700`,
                        defaultRequired = ErrorRed.`600`,
                        icon = Gray.`400`,
                        dropdown = Gray.`500`,
                        active = SuccessGreen.`500`
                    ),
                Disabled =
                    DropdownDisabled(
                        bg = Gray.`50`,
                        stroke = Gray.`300`,
                        label = Base.Obsidian,
                        textMain = Gray.`900`,
                        textSupport = Gray.`700`,
                        hint = Gray.`700`,
                        required = ErrorRed.`600`,
                        icon = Gray.`400`,
                        dropdown = Gray.`500`,
                        active = SuccessGreen.`500`
                    ),
                Parts =
                    DropdownParts(
                        scrollBar = Gray.`200`,
                        divider = Gray.`200`,
                        lhText = Gray.`700`,
                        lhBorder = Gray.`200`,
                        liTextPrimary = Gray.`900`,
                        liTextSecondary = Gray.`600`,
                        liTextTertiary = Gray.`500`,
                        liFgDisabled = Gray.`500`,
                        liIconDisabled = Gray.`500`,
                        liBgHover = Gray.`50`,
                        statusActive = SuccessGreen.`500`,
                        statusMain = SuccessGreen.`600`,
                        statusDisabled = Gray.`300`,
                        bgDisabled = Base.Concrete
                    )
            ),
        Tabs =
            Tabs(
                defaultText = Gray.`600`,
                defaultIcon = Gray.`500`,
                defaultTagBg = Gray.`100`,
                hoverText = Gray.`900`,
                hoverTagText = Gray.`600`,
                hoverIcon = Gray.`500`,
                hoverTagBg = Gray.`100`,
                hoverBorder = Gray.`200`,
                selectedText = Gray.`900`,
                selectedIcon = Gray.`500`,
                selectedTagBg = Gray.`100`,
                selectedBorder = Gray.`900`,
                disabledText = Gray.`400`,
                disabledIcon = Gray.`500`,
                disabledTagBg = Gray.`50`,
                disabledTagText = Gray.`400`
            ),
        Checkboxes =
            Checkboxes(
                boxOffBg = Base.Bone,
                boxOffStroke = Gray.`300`,
                boxOffHoverBg = Base.Bone,
                boxOffHoverStroke = Gray.`400`,
                boxOffDisabledBg = Gray.`100`,
                boxOffDisabledStroke = Gray.`300`,
                boxOnBg = Base.Obsidian,
                boxOnFg = Base.Bone,
                boxOnHoverBg = Gray.`800`,
                boxOnDisabledBg = Gray.`100`,
                boxOnDisabledStroke = Gray.`300`,
                boxOnDisabledFg = Gray.`300`
            ),
        Loading =
            Loading(
                loadingBgPrimary = Base.Bone,
                loadingBgSecondary = Gray.`100`,
                loadingFgPrimary = Gray.`900`
            ),
        Modals =
            Modals(
                defaultBg = Base.Bone,
                defaultFg = Gray.`600`,
                hoverBg = Gray.`100`,
                hoverFg = Gray.`600`,
                focusedBg = Gray.`100`,
                focusedStroke = Gray.`600`,
                disabledBg = Base.Concrete,
                disabledFg = Gray.`400`,
                surfacePrimary = Base.Bone,
                surfaceStroke = Gray.`200`
            ),
        HintTooltips =
            HintTooltips(
                surfacePrimary = Gray.`950`,
                defaultBg = Gray.`950`,
                defaultFg = Gray.`200`,
                hoverBg = Gray.`900`,
                hoverFg = Gray.`200`,
                focusedBg = Gray.`900`,
                focusedStroke = Gray.`500`,
                disabledBg = Gray.`900`,
                disabledFg = Gray.`400`
            ),
        TwoFA =
            TwoFA(
                defaultBg = Gray.`50`,
                defaultStroke = Gray.`50`,
                defaultText = Gray.`50`,
                focusedBg = Base.Bone,
                focusedStroke = Gray.`300`,
                focusedText = Gray.`600`,
                filledBg = Gray.`100`,
                filledStroke = Gray.`100`,
                filledText = Gray.`700`,
                disabledBg = Gray.`100`,
                disabledText = Gray.`300`,
                separatorDash = Gray.`200`
            ),
        Utility =
            Utility(
                Gray =
                    UtilityGray(
                        utilityGray700 = Gray.`700`,
                        utilityGray600 = Gray.`600`,
                        utilityGray500 = Gray.`500`,
                        utilityGray200 = Gray.`200`,
                        utilityGray50 = Gray.`50`,
                        utilityGray100 = Gray.`100`,
                        utilityGray400 = Gray.`400`,
                        utilityGray300 = Gray.`300`,
                        utilityGray900 = Gray.`900`,
                        utilityGray800 = Gray.`800`
                    ),
                SuccessGreen =
                    UtilitySuccessGreen(
                        utilitySuccess600 = SuccessGreen.`600`,
                        utilitySuccess700 = SuccessGreen.`700`,
                        utilitySuccess500 = SuccessGreen.`500`,
                        utilitySuccess200 = SuccessGreen.`200`,
                        utilitySuccess800 = SuccessGreen.`800`,
                        utilitySuccess50 = SuccessGreen.`50`,
                        utilitySuccess100 = SuccessGreen.`100`,
                        utilitySuccess400 = SuccessGreen.`400`,
                        utilitySuccess300 = SuccessGreen.`300`
                    ),
                ErrorRed =
                    UtilityErrorRed(
                        utilityError600 = ErrorRed.`600`,
                        utilityError700 = ErrorRed.`700`,
                        utilityError500 = ErrorRed.`500`,
                        utilityError200 = ErrorRed.`200`,
                        utilityError800 = ErrorRed.`800`,
                        utilityError50 = ErrorRed.`50`,
                        utilityError100 = ErrorRed.`100`,
                        utilityError400 = ErrorRed.`400`,
                        utilityError300 = ErrorRed.`300`
                    ),
                WarningYellow =
                    UtilityWarningYellow(
                        utilityOrange600 = WarningYellow.`600`,
                        utilityOrange700 = WarningYellow.`700`,
                        utilityOrange500 = WarningYellow.`500`,
                        utilityOrange200 = WarningYellow.`200`,
                        utilityOrange800 = WarningYellow.`800`,
                        utilityOrange900 = WarningYellow.`900`,
                        utilityOrange50 = WarningYellow.`50`,
                        utilityOrange100 = WarningYellow.`100`,
                        utilityOrange400 = WarningYellow.`400`,
                        utilityOrange300 = WarningYellow.`300`
                    ),
                HyperBlue =
                    UtilityHyperBlue(
                        utilityBlueDark600 = HyperBlue.`600`,
                        utilityBlueDark700 = HyperBlue.`700`,
                        utilityBlueDark500 = HyperBlue.`500`,
                        utilityBlueDark200 = HyperBlue.`200`,
                        utilityBlueDark800 = HyperBlue.`800`,
                        utilityBlueDark50 = HyperBlue.`50`,
                        utilityBlueDark100 = HyperBlue.`100`,
                        utilityBlueDark400 = HyperBlue.`400`,
                        utilityBlueDark300 = HyperBlue.`300`
                    ),
                Indigo =
                    UtilityIndigo(
                        utilityIndigo600 = Indigo.`600`,
                        utilityIndigo700 = Indigo.`700`,
                        utilityIndigo500 = Indigo.`500`,
                        utilityIndigo200 = Indigo.`200`,
                        utilityIndigo800 = Indigo.`800`,
                        utilityIndigo50 = Indigo.`50`,
                        utilityIndigo100 = Indigo.`100`,
                        utilityIndigo400 = Indigo.`400`,
                        utilityIndigo300 = Indigo.`300`
                    ),
                Purple =
                    UtilityPurple(
                        utilityPurple600 = Purple.`600`,
                        utilityPurple700 = Purple.`700`,
                        utilityPurple500 = Purple.`500`,
                        utilityPurple200 = Purple.`200`,
                        utilityPurple800 = Purple.`800`,
                        utilityPurple50 = Purple.`50`,
                        utilityPurple100 = Purple.`100`,
                        utilityPurple400 = Purple.`400`,
                        utilityPurple300 = Purple.`300`,
                        utilityPurple900 = Purple.`900`
                    ),
                Espresso =
                    UtilityEspresso(
                        utilityEspresso700 = Espresso.`700`,
                        utilityEspresso600 = Espresso.`600`,
                        utilityEspresso500 = Espresso.`500`,
                        utilityEspresso200 = Espresso.`200`,
                        utilityEspresso50 = Espresso.`50`,
                        utilityEspresso100 = Espresso.`100`,
                        utilityEspresso400 = Espresso.`400`,
                        utilityEspresso300 = Espresso.`300`,
                        utilityEspresso900 = Espresso.`900`,
                        utilityEspresso800 = Espresso.`800`,
                        utilityEspresso950 = Espresso.`950`
                    )
            ),
        Accents =
            Accents(
                green = Accent.Green
            ),
        Transparent =
            Transparent(
                bgPrimary = TransparentColorPalette.Light
            ),
        NoTheme = NoTheme(welcomeText = Shark.`50`)
    )
