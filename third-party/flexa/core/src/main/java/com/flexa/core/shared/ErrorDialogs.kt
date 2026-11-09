package com.flexa.core.shared

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flexa.R
import com.flexa.core.theme.FlexaTheme

@Composable
fun ErrorDialog(
    errorHandler: ApiErrorHandler,
    icon: ImageVector? = null,
    onDismissed: (() -> Unit)? = null
) {
    val error by errorHandler.error.collectAsStateWithLifecycle()
    val hasError by remember { derivedStateOf { error != null } }
    if (hasError) {
        when (val err = error) {
            is ApiError.InfoEntity -> {
                InfoDialog(
                    message = err.message ?: "",
                    onDismissRequest = {
                        errorHandler.clearError()
                        onDismissed?.invoke()
                    },
                    onClick = {
                        errorHandler.clearError()
                        onDismissed?.invoke()
                    }
                )
            }

            is ApiError.ReportEntity -> {
                ReportDialog(
                    message = err.text,
                    title = err.title,
                    error = err,
                    icon = icon,
                    onDismissRequest = {
                        errorHandler.clearError()
                        onDismissed?.invoke()
                    },
                    onClick = {
                        errorHandler.clearError()
                        onDismissed?.invoke()
                    }
                )
            }

            else -> {}
        }
    }
}

@Composable
private fun InfoDialog(
    title: String? = null,
    message: String,
    icon: ImageVector? = null,
    onDismissRequest: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = {
            onDismissRequest?.invoke()
        },
        icon = { icon?.let { Icon(imageVector = it, contentDescription = null) } },
        title = {
            val context = LocalContext.current
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = title ?: context.getString(R.string.something_went_wrong),
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = message,
                fontWeight = FontWeight.W500,
            )

        },
        confirmButton = {
            TextButton(onClick = {
                onClick?.invoke()
            }) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        }
    )
}

@Composable
private fun ReportDialog(
    title: String? = null,
    message: String,
    error: ApiError.ReportEntity,
    icon: ImageVector? = null,
    onDismissRequest: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { onDismissRequest?.invoke() },
        icon = { icon?.let { Icon(imageVector = it, contentDescription = null) } },
        title = {
            title?.let {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = it,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = message,
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp
                )
            )
        },
        dismissButton = {
            TextButton(onClick = { onClick?.invoke() }) { Text(text = stringResource(id = R.string.close)) }
        },
        confirmButton = {
            Button(onClick = {
                onDismissRequest?.invoke()
                if (context is Activity) error.sendEmailReport(context)
            }) { Text(text = context.getString(R.string.report_an_issue)) }
        }
    )
}

@Preview
@Composable
fun InfoDialogPreview() {
    val context = LocalContext.current
    FlexaTheme {
        InfoDialog(
            title = "Title example",
            message = context.getString(R.string.we_are_sorry_we_encountered_a_problem)
        )
    }
}

@Preview
@Composable
fun ReportDialogPreview() {
    val context = LocalContext.current
    FlexaTheme {
        ReportDialog(
            icon = Icons.Default.BugReport,
            title = context.getString(R.string.something_went_wrong),
            message = context.getString(R.string.we_are_sorry_we_encountered_a_problem),
            error = ApiError.ReportEntity(
                data = "",
                message = "Error example",
                traceId = "trace id example"
            )
        )
    }
}