package com.flexa.identity.coppa

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexa.R
import com.flexa.core.theme.FlexaTheme

@Composable
fun CoppaScreen(
    modifier: Modifier = Modifier,
    toBack: () -> Unit = {},
) {
    val palette = MaterialTheme.colorScheme

    BackHandler {

    }

    Box(modifier = modifier.systemBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                modifier = Modifier.size(90.dp),
                imageVector = Icons.Rounded.Report,
                contentDescription = null,
                tint = Color.Red.copy(alpha = .7F)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.coppa_title),
                style = TextStyle(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.onBackground
                )
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(id = R.string.coppa_description),
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = palette.onBackground
                )
            )
            Spacer(modifier = Modifier.height(200.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .systemBarsPadding()
                .padding(32.dp)
                .align(Alignment.BottomCenter)
        ) {
            val context = LocalContext.current
            Button(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(0.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                onClick = {
                    if (context is Activity)
                        context.finish()
                    else toBack.invoke()
                },
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
            ) {
                Text(
                    stringResource(id = R.string.exit),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium
                    ),
                )
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun CoppaPreview() {
    FlexaTheme {
        Surface {
            CoppaScreen(
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
