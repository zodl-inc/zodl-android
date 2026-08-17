package co.electriccoin.zcash.ui.design.theme.colors

private val dark = DarkZashiColorsInternal

internal val OledZashiColorsInternal =
    dark.copy(
        Surfaces =
            dark.Surfaces.copy(
                bgPrimary = Base.Black,
                bgAdjust = Shark.`950`,
                bgSecondary = SharkShades.`01dp`,
                bgTertiary = Shark.`900`,
                bgQuaternary = Shark.`800`,
                strokePrimary = Shark.`800`,
                strokeSecondary = Shark.`900`,
                bgHide = Base.Black,
                divider = Shark.`950`
            ),
        Btns =
            dark.Btns.copy(
                Brand =
                    dark.Btns.Brand.copy(
                        btnBrandBgDisabled = Shark.`950`
                    ),
                Secondary =
                    dark.Btns.Secondary.copy(
                        btnSecondaryBg = Base.Black,
                        btnSecondaryBorder = Shark.`800`,
                        btnSecondaryBgDisabled = Shark.`950`
                    ),
                Tertiary =
                    dark.Btns.Tertiary.copy(
                        btnTertiaryBg = Shark.`950`,
                        btnTertiaryBgHover = Shark.`900`,
                        btnTertiaryBgDisabled = Shark.`950`
                    ),
                Quaternary =
                    dark.Btns.Quaternary.copy(
                        btnQuartBg = Shark.`800`,
                        btnQuartBgDisabled = Shark.`950`
                    ),
                Destructive1 =
                    dark.Btns.Destructive1.copy(
                        btnDestroy1BgDisabled = Shark.`950`
                    ),
                Destructive2 =
                    dark.Btns.Destructive2.copy(
                        btnDestroy2BgDisabled = Shark.`950`
                    ),
                Primary =
                    dark.Btns.Primary.copy(
                        btnPrimaryBgDisabled = Shark.`950`
                    ),
                Ghost =
                    dark.Btns.Ghost.copy(
                        btnGhostBg = Base.Black,
                        btnGhostBgDisabled = Shark.`950`
                    )
            ),
        Avatars =
            dark.Avatars.copy(
                avatarProfileBorder = Base.Black
            ),
        Sliders =
            dark.Sliders.copy(
                sliderHandleBg = Base.Black
            ),
        Inputs =
            dark.Inputs.copy(
                Default =
                    dark.Inputs.Default.copy(
                        bg = Shark.`950`,
                        stroke = Shark.`900`
                    ),
                Hover =
                    dark.Inputs.Hover.copy(
                        bg = Shark.`900`,
                        asideBg = Shark.`950`,
                        stroke = Shark.`800`
                    ),
                Filled =
                    dark.Inputs.Filled.copy(
                        bg = Shark.`950`,
                        asideBg = Shark.`950`,
                        stroke = Shark.`800`
                    ),
                Focused =
                    dark.Inputs.Focused.copy(
                        asideBg = Shark.`950`,
                        stroke2 = Shark.`800`
                    ),
                Disabled =
                    dark.Inputs.Disabled.copy(
                        bg = Shark.`950`,
                        stroke = Shark.`800`
                    ),
                ErrorDefault =
                    dark.Inputs.ErrorDefault.copy(
                        bgAlt = Shark.`950`,
                        strokeAlt = Shark.`800`
                    ),
                ErrorHover =
                    dark.Inputs.ErrorHover.copy(
                        bgAlt = Shark.`950`,
                        strokeAlt = Shark.`800`
                    ),
                ErrorFilled =
                    dark.Inputs.ErrorFilled.copy(
                        bgAlt = Shark.`950`,
                        strokeAlt = Shark.`800`
                    ),
                ErrorFocused =
                    dark.Inputs.ErrorFocused.copy(
                        bgAlt = Shark.`950`,
                        strokeAlt = Shark.`800`
                    )
            ),
        Accordion =
            dark.Accordion.copy(
                xBtnHoverBg = Shark.`900`,
                xBtnOnHoverBg = Shark.`900`,
                xBtnFocusBg = Shark.`800`,
                xBtnDisabledBg = Shark.`950`,
                defaultBg = Base.Black,
                defaultStroke = Shark.`950`,
                expandedBg = Shark.`950`,
                expandedHoverBg = Shark.`900`,
                expandedStroke = Shark.`800`,
                dividers = Shark.`800`
            ),
        Switcher =
            dark.Switcher.copy(
                defaultTagBg = Shark.`800`,
                hoverBg = Shark.`800`,
                disabledTagBg = Shark.`900`,
                surfacePrimary = Shark.`950`
            ),
        Tags =
            dark.Tags.copy(
                tcHoverBg = Shark.`900`,
                tcCountBg = Shark.`800`,
                surfacePrimary = Base.Black,
                surfaceStroke = Shark.`800`
            ),
        Dropdowns =
            dark.Dropdowns.copy(
                Default =
                    dark.Dropdowns.Default.copy(
                        bg = Shark.`950`
                    ),
                Filled =
                    dark.Dropdowns.Filled.copy(
                        bg = Shark.`950`
                    ),
                Disabled =
                    dark.Dropdowns.Disabled.copy(
                        bg = Shark.`950`,
                        stroke = Shark.`800`
                    ),
                Parts =
                    dark.Dropdowns.Parts.copy(
                        scrollBar = Shark.`800`,
                        divider = Shark.`800`,
                        lhBorder = Shark.`800`,
                        liBgHover = Shark.`900`,
                        bgDisabled = Shark.`950`
                    )
            ),
        Tabs =
            dark.Tabs.copy(
                defaultTagBg = Shark.`950`,
                disabledTagBg = Shark.`950`
            ),
        Checkboxes =
            dark.Checkboxes.copy(
                boxOffBg = Shark.`950`,
                boxOffDisabledBg = Shark.`800`,
                boxOnDisabledBg = Shark.`800`
            ),
        Loading =
            dark.Loading.copy(
                loadingBgPrimary = Base.Black,
                loadingBgSecondary = Shark.`900`
            ),
        Modals =
            dark.Modals.copy(
                defaultBg = Base.Black,
                hoverBg = Shark.`900`,
                focusedBg = Shark.`800`,
                disabledBg = Shark.`950`,
                surfacePrimary = Base.Black,
                surfaceStroke = Shark.`900`
            ),
        HintTooltips =
            dark.HintTooltips.copy(
                surfacePrimary = Shark.`900`,
                defaultBg = Shark.`900`,
                hoverBg = Shark.`800`,
                focusedBg = Shark.`800`,
                disabledBg = Shark.`900`
            ),
        TwoFA =
            dark.TwoFA.copy(
                defaultBg = Shark.`900`,
                defaultStroke = Shark.`900`,
                filledBg = Shark.`800`,
                filledStroke = Shark.`800`,
                disabledBg = Shark.`950`
            ),
        Transparent =
            dark.Transparent.copy(
                bgPrimary = Base.Black
            )
    )
