package co.electriccoin.zcash.ui.design.theme.colors

val DarkZashiColorsInternal =
    ZashiColorsInternal(
        Surfaces =
            Surfaces(
                bgPrimary = Base.Obsidian,
                bgAdjust = Shark.`900`,
                bgSecondary = SharkShades.`06dp`,
                bgTertiary = Shark.`800`,
                bgQuaternary = Shark.`700`,
                strokePrimary = Shark.`700`,
                strokeSecondary = Shark.`800`,
                bgAlt = Base.Bone,
                bgHide = Base.Obsidian,
                brandBg = Base.Brand,
                brandFg = Base.Obsidian,
                divider = Shark.`900`
            ),
        Text =
            Text(
                textPrimary = Shark.`50`,
                textSecondary = Shark.`200`,
                textTertiary = Shark.`200`,
                textQuaternary = Shark.`300`,
                textSupport = Shark.`400`,
                textDisabled = Shark.`600`,
                textError = ErrorRed.`300`,
                textLink = HyperBlue.`300`,
                textLight = Shark.`50`,
                textLightSupport = Shark.`200`
            ),
        Btns =
            Btns(
                Brand =
                    BtnBrand(
                        btnBrandBg = Brand.`400`,
                        btnBrandBgHover = Brand.`300`,
                        btnBrandFg = Base.Obsidian,
                        btnBrandFgHover = Base.Obsidian,
                        btnBrandBgDisabled = Shark.`900`,
                        btnBrandFgDisabled = Shark.`500`
                    ),
                Secondary =
                    BtnSecondary(
                        btnSecondaryBg = Base.Obsidian,
                        btnSecondaryBgHover = Shark.`950`,
                        btnSecondaryFg = Shark.`50`,
                        btnSecondaryFgHover = Shark.`50`,
                        btnSecondaryBorder = Shark.`700`,
                        btnSecondaryBorderHover = Shark.`600`,
                        btnSecondaryBgDisabled = Shark.`900`,
                        btnSecondaryFgDisabled = Shark.`500`
                    ),
                Tertiary =
                    BtnTertiary(
                        btnTertiaryBg = Shark.`900`,
                        btnTertiaryBgHover = Shark.`800`,
                        btnTertiaryFg = Shark.`100`,
                        btnTertiaryFgHover = Shark.`100`,
                        btnTertiaryBgDisabled = Shark.`900`,
                        btnTertiaryFgDisabled = Shark.`500`
                    ),
                Quaternary =
                    BtnQuaternary(
                        btnQuartBg = Shark.`700`,
                        btnQuartBgHover = Shark.`600`,
                        btnQuartFg = Shark.`50`,
                        btnQuartFgHover = Shark.`50`,
                        btnQuartBgDisabled = Shark.`900`,
                        btnQuartFgDisabled = Shark.`500`
                    ),
                Destructive1 =
                    BtnDestructive1(
                        btnDestroy1Bg = ErrorRed.`950`,
                        btnDestroy1BgHover = ErrorRed.`900`,
                        btnDestroy1Fg = ErrorRed.`100`,
                        btnDestroy1FgHover = ErrorRed.`50`,
                        btnDestroy1Border = ErrorRed.`800`,
                        btnDestroy1BorderHover = ErrorRed.`700`,
                        btnDestroy1BgDisabled = Shark.`900`,
                        btnDestroy1FgDisabled = Shark.`500`
                    ),
                Destructive2 =
                    BtnDestructive2(
                        btnDestroy2Bg = ErrorRed.`600`,
                        btnDestroy2BgHover = ErrorRed.`700`,
                        btnDestroy2Fg = ErrorRed.`50`,
                        btnDestroy2BgDisabled = Shark.`900`,
                        btnDestroy2FgDisabled = Shark.`500`
                    ),
                Primary =
                    BtnPrimary(
                        btnPrimaryBg = Base.Bone,
                        btnPrimaryBgHover = Gray.`100`,
                        btnPrimaryFg = Base.Obsidian,
                        btnPrimaryBgDisabled = Shark.`900`,
                        btnBoldFgDisabled = Shark.`500`
                    ),
                Ghost =
                    BtnGhost(
                        btnGhostBg = Base.Obsidian,
                        btnGhostBgHover = Gray.`900`,
                        btnGhostFg = Shark.`50`,
                        btnGhostBgDisabled = Shark.`900`,
                        btnGhostFgDisabled = Shark.`500`
                    )
            ),
        Avatars =
            Avatars(
                avatarProfileBorder = Base.Obsidian,
                avatarBg = Shark.`600`,
                avatarBgSecondary = Shark.`500`,
                avatarStatus = SuccessGreen.`400`,
                avatarTextFg = Shark.`100`,
                avatarBadgeBg = HyperBlue.`400`,
                avatarBadgeFg = Base.Obsidian
            ),
        Sliders =
            Sliders(
                sliderHandleBorder = Shark.`500`,
                sliderHandleBg = Base.Obsidian
            ),
        Inputs =
            Inputs(
                Default =
                    InputDefault(
                        bg = Shark.`900`,
                        bgAlt = Shark.`950`,
                        label = Shark.`50`,
                        text = Shark.`400`,
                        hint = Shark.`300`,
                        required = ErrorRed.`400`,
                        icon = Shark.`400`,
                        stroke = Shark.`800`
                    ),
                Hover =
                    InputHover(
                        bg = Shark.`800`,
                        bgAlt = Shark.`950`,
                        asideBg = Shark.`900`,
                        stroke = Shark.`700`,
                        label = Shark.`50`,
                        text = Shark.`300`,
                        hint = Shark.`300`,
                        icon = Shark.`400`,
                        required = ErrorRed.`400`
                    ),
                Filled =
                    InputFilled(
                        bg = Shark.`900`,
                        bgAlt = Shark.`950`,
                        asideBg = Shark.`900`,
                        stroke = Shark.`700`,
                        label = Shark.`50`,
                        text = Shark.`100`,
                        hint = Shark.`300`,
                        icon = Shark.`400`,
                        iconMain = Shark.`500`,
                        required = ErrorRed.`400`
                    ),
                Focused =
                    InputFocused(
                        bg = Shark.`950`,
                        asideBg = Shark.`900`,
                        stroke = Shark.`100`,
                        stroke2 = Shark.`700`,
                        label = Shark.`50`,
                        text = Shark.`100`,
                        hint = Shark.`300`,
                        icon = Shark.`400`,
                        iconMain = Shark.`500`,
                        defaultRequired = ErrorRed.`400`
                    ),
                Disabled =
                    InputDisabled(
                        bg = Shark.`900`,
                        stroke = Shark.`700`,
                        label = Shark.`50`,
                        text = Shark.`300`,
                        hint = Shark.`300`,
                        icon = Shark.`600`,
                        iconMain = Shark.`500`,
                        required = ErrorRed.`400`
                    ),
                ErrorDefault =
                    InputErrorDefault(
                        bg = Shark.`950`,
                        bgAlt = Shark.`900`,
                        label = Shark.`50`,
                        text = Shark.`400`,
                        textAside = Shark.`400`,
                        textMain = Shark.`100`,
                        hint = ErrorRed.`400`,
                        icon = ErrorRed.`400`,
                        iconMain = Shark.`500`,
                        stroke = ErrorRed.`400`,
                        strokeAlt = Shark.`700`,
                        dropdown = Shark.`600`
                    ),
                ErrorHover =
                    InputErrorHover(
                        bg = Shark.`950`,
                        bgAlt = Shark.`900`,
                        label = Shark.`50`,
                        text = Shark.`300`,
                        textAside = Shark.`400`,
                        textMain = Shark.`100`,
                        hint = ErrorRed.`400`,
                        icon = ErrorRed.`400`,
                        iconMain = Shark.`500`,
                        stroke = ErrorRed.`500`,
                        strokeAlt = Shark.`700`,
                        dropdown = Shark.`600`
                    ),
                ErrorFilled =
                    InputErrorFilled(
                        bg = Shark.`950`,
                        bgAlt = Shark.`900`,
                        label = Shark.`50`,
                        text = Shark.`100`,
                        textAside = Shark.`400`,
                        hint = ErrorRed.`400`,
                        icon = ErrorRed.`400`,
                        iconMain = Shark.`500`,
                        stroke = ErrorRed.`500`,
                        strokeAlt = Shark.`700`,
                        dropdown = Shark.`600`
                    ),
                ErrorFocused =
                    InputErrorFocused(
                        bg = Shark.`950`,
                        bgAlt = Shark.`900`,
                        label = Shark.`50`,
                        text = Shark.`100`,
                        textAside = Shark.`400`,
                        hint = ErrorRed.`400`,
                        icon = ErrorRed.`400`,
                        iconMain = Shark.`500`,
                        stroke = ErrorRed.`500`,
                        strokeAlt = Shark.`700`,
                        dropdown = Shark.`600`
                    )
            ),
        Accordion =
            Accordion(
                xBtnDefaultFg = Shark.`200`,
                xBtnHoverBg = Shark.`800`,
                xBtnOnHoverBg = Shark.`800`,
                xBtnHoverFg = Shark.`200`,
                xBtnFocusBg = Shark.`700`,
                xBtnFocusFg = Shark.`200`,
                xBtnFocusStroke = Shark.`500`,
                xBtnDisabledBg = Shark.`900`,
                xBtnDisabledFg = Shark.`600`,
                defaultBg = Base.Obsidian,
                defaultStroke = Shark.`900`,
                defaultIcon = Shark.`500`,
                focusStroke = Shark.`400`,
                expandedBg = Shark.`900`,
                expandedHoverBg = Shark.`800`,
                expandedStroke = Shark.`700`,
                dividers = Shark.`700`,
                expandedFocusStroke = Shark.`400`
            ),
        Switcher =
            Switcher(
                defaultText = Shark.`200`,
                defaultTagBg = Shark.`700`,
                defaultIcon = Shark.`500`,
                hoverBg = Shark.`700`,
                hoverTagBg = Shark.`600`,
                hoverIcon = Shark.`500`,
                hoverText = Shark.`200`,
                hoverTagText = Shark.`200`,
                selectedBg = Shark.`50`,
                selectedIcon = Shark.`600`,
                selectedText = Shark.`900`,
                selectedTagBg = Shark.`100`,
                selectedStroke = Shark.`200`,
                disabledText = Shark.`500`,
                disabledIcon = Shark.`600`,
                disabledTagBg = Shark.`800`,
                surfacePrimary = Shark.`900`
            ),
        Toggles =
            Toggles(
                tgDefaultBg = Shark.`600`,
                tgDefaultFg = Shark.`400`,
                tgActiveBg = Shark.`50`,
                tgActiveFg = Shark.`900`,
                tgDefaultHoverBg = Shark.`500`,
                tgDefaultHoverFg = Shark.`400`,
                tgActiveHoverBg = Shark.`300`,
                tgActiveHoverFg = Shark.`900`,
                tgDefaultDisabledBg = Shark.`700`,
                tgDefaultDisabledFg = Shark.`500`,
                tgActiveDisabledBg = Shark.`700`,
                tgActiveDisabledFg = Shark.`500`
            ),
        Tags =
            Tags(
                tcDefaultFg = Shark.`500`,
                tcHoverBg = Shark.`800`,
                tcHoverFg = Shark.`200`,
                tcCountBg = Shark.`700`,
                tcCountFg = Shark.`300`,
                statusIndicator = SuccessGreen.`500`,
                surfacePrimary = Base.Obsidian,
                surfaceStroke = Shark.`700`
            ),
        Dropdowns =
            Dropdowns(
                Default =
                    DropdownDefault(
                        bg = Shark.`900`,
                        label = Shark.`50`,
                        text = Shark.`400`,
                        hint = Shark.`300`,
                        required = ErrorRed.`400`,
                        icon = Shark.`400`,
                        dropdown = Shark.`500`,
                        active = SuccessGreen.`400`
                    ),
                Filled =
                    DropdownFilled(
                        bg = Shark.`900`,
                        label = Shark.`50`,
                        textMain = Shark.`100`,
                        textSupport = Shark.`300`,
                        hint = Shark.`300`,
                        required = ErrorRed.`400`,
                        icon = Shark.`400`,
                        dropdown = Shark.`500`,
                        active = SuccessGreen.`400`
                    ),
                Focused =
                    DropdownFocused(
                        bg = Shark.`950`,
                        stroke = Shark.`100`,
                        label = Shark.`50`,
                        textMain = Shark.`100`,
                        textSupport = Shark.`300`,
                        hint = Shark.`300`,
                        defaultRequired = ErrorRed.`400`,
                        icon = Shark.`400`,
                        dropdown = Shark.`500`,
                        active = SuccessGreen.`400`
                    ),
                Disabled =
                    DropdownDisabled(
                        bg = Shark.`900`,
                        stroke = Shark.`700`,
                        label = Shark.`50`,
                        textMain = Shark.`100`,
                        textSupport = Shark.`300`,
                        hint = Shark.`300`,
                        required = ErrorRed.`400`,
                        icon = Shark.`400`,
                        dropdown = Shark.`500`,
                        active = SuccessGreen.`400`
                    ),
                Parts =
                    DropdownParts(
                        scrollBar = Shark.`700`,
                        divider = Shark.`700`,
                        lhText = Shark.`300`,
                        lhBorder = Shark.`700`,
                        liTextPrimary = Shark.`100`,
                        liTextSecondary = Shark.`400`,
                        liTextTertiary = Shark.`500`,
                        liFgDisabled = Shark.`500`,
                        liIconDisabled = Shark.`500`,
                        liBgHover = Shark.`800`,
                        statusActive = SuccessGreen.`400`,
                        statusMain = SuccessGreen.`500`,
                        statusDisabled = Shark.`600`,
                        bgDisabled = Shark.`900`
                    )
            ),
        Tabs =
            Tabs(
                defaultText = Shark.`300`,
                defaultIcon = Shark.`500`,
                defaultTagBg = Shark.`900`,
                hoverText = Shark.`200`,
                hoverTagText = Shark.`200`,
                hoverIcon = Shark.`500`,
                hoverTagBg = Shark.`600`,
                hoverBorder = Shark.`500`,
                selectedText = Shark.`50`,
                selectedIcon = Shark.`200`,
                selectedTagBg = Shark.`500`,
                selectedBorder = Shark.`50`,
                disabledText = Shark.`500`,
                disabledIcon = Shark.`500`,
                disabledTagBg = Shark.`900`,
                disabledTagText = Shark.`500`
            ),
        Checkboxes =
            Checkboxes(
                boxOffBg = Shark.`900`,
                boxOffStroke = Shark.`400`,
                boxOffHoverBg = Shark.`500`,
                boxOffHoverStroke = Shark.`400`,
                boxOffDisabledBg = Shark.`700`,
                boxOffDisabledStroke = Shark.`600`,
                boxOnBg = Shark.`50`,
                boxOnFg = Shark.`900`,
                boxOnHoverBg = Shark.`300`,
                boxOnDisabledBg = Shark.`700`,
                boxOnDisabledStroke = Shark.`600`,
                boxOnDisabledFg = Shark.`400`
            ),
        Loading =
            Loading(
                loadingBgPrimary = Base.Obsidian,
                loadingBgSecondary = Shark.`800`,
                loadingFgPrimary = Base.Bone
            ),
        Modals =
            Modals(
                defaultBg = Base.Obsidian,
                defaultFg = Shark.`200`,
                hoverBg = Shark.`800`,
                hoverFg = Shark.`200`,
                focusedBg = Shark.`700`,
                focusedStroke = Shark.`500`,
                disabledBg = Shark.`900`,
                disabledFg = Shark.`600`,
                surfacePrimary = Base.Obsidian,
                surfaceStroke = Shark.`800`
            ),
        HintTooltips =
            HintTooltips(
                surfacePrimary = Shark.`800`,
                defaultBg = Shark.`800`,
                defaultFg = Shark.`200`,
                hoverBg = Shark.`700`,
                hoverFg = Shark.`200`,
                focusedBg = Shark.`700`,
                focusedStroke = Shark.`500`,
                disabledBg = Shark.`800`,
                disabledFg = Shark.`600`
            ),
        TwoFA =
            TwoFA(
                defaultBg = Shark.`800`,
                defaultStroke = Shark.`800`,
                defaultText = Shark.`800`,
                focusedBg = Shark.`600`,
                focusedStroke = Shark.`500`,
                focusedText = Shark.`200`,
                filledBg = Shark.`700`,
                filledStroke = Shark.`700`,
                filledText = Shark.`200`,
                disabledBg = Shark.`900`,
                disabledText = Shark.`700`,
                separatorDash = Shark.`600`
            ),
        Utility =
            Utility(
                Gray =
                    UtilityGray(
                        utilityGray700 = Shark.`200`,
                        utilityGray600 = Shark.`300`,
                        utilityGray500 = Shark.`400`,
                        utilityGray200 = Shark.`700`,
                        utilityGray50 = Shark.`900`,
                        utilityGray100 = Shark.`800`,
                        utilityGray400 = Shark.`500`,
                        utilityGray300 = Shark.`600`,
                        utilityGray900 = Shark.`50`,
                        utilityGray800 = Shark.`100`
                    ),
                SuccessGreen =
                    UtilitySuccessGreen(
                        utilitySuccess600 = SuccessGreen.`400`,
                        utilitySuccess700 = SuccessGreen.`300`,
                        utilitySuccess500 = SuccessGreen.`500`,
                        utilitySuccess200 = SuccessGreen.`800`,
                        utilitySuccess800 = SuccessGreen.`200`,
                        utilitySuccess50 = SuccessGreen.`950`,
                        utilitySuccess100 = SuccessGreen.`900`,
                        utilitySuccess400 = SuccessGreen.`600`,
                        utilitySuccess300 = SuccessGreen.`700`
                    ),
                ErrorRed =
                    UtilityErrorRed(
                        utilityError600 = ErrorRed.`400`,
                        utilityError700 = ErrorRed.`300`,
                        utilityError500 = ErrorRed.`500`,
                        utilityError200 = ErrorRed.`800`,
                        utilityError800 = ErrorRed.`200`,
                        utilityError50 = ErrorRed.`950`,
                        utilityError100 = ErrorRed.`900`,
                        utilityError400 = ErrorRed.`600`,
                        utilityError300 = ErrorRed.`700`
                    ),
                WarningYellow =
                    UtilityWarningYellow(
                        utilityOrange600 = WarningYellow.`400`,
                        utilityOrange700 = WarningYellow.`300`,
                        utilityOrange500 = WarningYellow.`500`,
                        utilityOrange200 = WarningYellow.`800`,
                        utilityOrange800 = WarningYellow.`200`,
                        utilityOrange900 = WarningYellow.`100`,
                        utilityOrange50 = WarningYellow.`950`,
                        utilityOrange100 = WarningYellow.`900`,
                        utilityOrange400 = WarningYellow.`600`,
                        utilityOrange300 = WarningYellow.`700`
                    ),
                HyperBlue =
                    UtilityHyperBlue(
                        utilityBlueDark600 = HyperBlue.`400`,
                        utilityBlueDark700 = HyperBlue.`300`,
                        utilityBlueDark500 = HyperBlue.`500`,
                        utilityBlueDark200 = HyperBlue.`800`,
                        utilityBlueDark800 = HyperBlue.`200`,
                        utilityBlueDark50 = HyperBlue.`950`,
                        utilityBlueDark100 = HyperBlue.`900`,
                        utilityBlueDark400 = HyperBlue.`600`,
                        utilityBlueDark300 = HyperBlue.`700`
                    ),
                Indigo =
                    UtilityIndigo(
                        utilityIndigo600 = Indigo.`400`,
                        utilityIndigo700 = Indigo.`300`,
                        utilityIndigo500 = Indigo.`500`,
                        utilityIndigo200 = Indigo.`800`,
                        utilityIndigo800 = Indigo.`200`,
                        utilityIndigo50 = Indigo.`950`,
                        utilityIndigo100 = Indigo.`900`,
                        utilityIndigo400 = Indigo.`600`,
                        utilityIndigo300 = Indigo.`700`
                    ),
                Purple =
                    UtilityPurple(
                        utilityPurple600 = Purple.`400`,
                        utilityPurple700 = Purple.`300`,
                        utilityPurple500 = Purple.`500`,
                        utilityPurple200 = Purple.`800`,
                        utilityPurple800 = Purple.`200`,
                        utilityPurple50 = Purple.`950`,
                        utilityPurple100 = Purple.`900`,
                        utilityPurple400 = Purple.`600`,
                        utilityPurple300 = Purple.`700`,
                        utilityPurple900 = Purple.`50`
                    ),
                Espresso =
                    UtilityEspresso(
                        utilityEspresso700 = Espresso.`200`,
                        utilityEspresso600 = Espresso.`300`,
                        utilityEspresso500 = Espresso.`400`,
                        utilityEspresso200 = Espresso.`700`,
                        utilityEspresso50 = Espresso.`950`,
                        utilityEspresso100 = Espresso.`900`,
                        utilityEspresso400 = Espresso.`500`,
                        utilityEspresso300 = Espresso.`600`,
                        utilityEspresso800 = Espresso.`100`,
                        utilityEspresso900 = Espresso.`50`,
                        utilityEspresso950 = Espresso.`25`
                    )
            ),
        Accents =
            Accents(
                green = Accent.Green
            ),
        Transparent =
            Transparent(
                bgPrimary = TransparentColorPalette.Dark
            ),
        NoTheme = NoTheme(welcomeText = Shark.`50`)
    )
