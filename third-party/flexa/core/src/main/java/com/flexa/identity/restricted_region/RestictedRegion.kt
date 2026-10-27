package com.flexa.identity.restricted_region

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexa.R
import com.flexa.core.theme.FlexaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestrictedRegion(
    modifier: Modifier = Modifier,
    toBack: () -> Unit,
) {
    val scrollBehavior =
        TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val palette = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = { toBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)
                    .navigationBarsPadding()
            ) {
                Button(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onClick = toBack
                ) { Text(text = stringResource(android.R.string.ok).uppercase()) }
            }
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = modifier
                .padding(padding)
                .padding(horizontal = 22.dp)
                .verticalScroll(scrollState),
        ) {
            Icon(
                modifier = Modifier
                    .size(60.dp)
                    .rotate(90F),
                imageVector = Icons.Default.Block,
                tint = palette.onBackground,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.restricted_region_title),
                fontSize = 32.sp, lineHeight = 36.sp
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.restricted_region_copy),
                fontSize = 14.sp, lineHeight = 21.sp
            )
        }
    }
}

@Preview
@Composable
private fun RestrictedRegionPreview() {
    FlexaTheme {
        RestrictedRegion(
            modifier = Modifier.fillMaxSize(),
            toBack = {}
        )
    }
}