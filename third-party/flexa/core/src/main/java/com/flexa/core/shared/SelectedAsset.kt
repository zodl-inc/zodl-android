package com.flexa.core.shared

import com.flexa.core.entity.AvailableAsset

data class SelectedAsset(
    val accountId: String,
    val asset: AvailableAsset,
)
