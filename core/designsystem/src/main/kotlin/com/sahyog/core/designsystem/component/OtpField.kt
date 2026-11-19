package com.sahyog.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sahyog.core.designsystem.theme.SahyogSpacing
import com.sahyog.core.designsystem.theme.SahyogTheme

/**
 * One-time-code input rendered as individual digit boxes.
 *
 * Backed by a single text field, so paste, backspace and the system SMS-code
 * autofill (ContentType.SmsOtpCode) all work as with a normal input.
 */
@Composable
fun SahyogOtpField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    isError: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onDone: () -> Unit = {},
) {
    val colors = SahyogTheme.colors
    BasicTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit).take(length)) },
        modifier = modifier
            .focusRequester(focusRequester)
            .semantics { contentType = ContentType.SmsOtpCode },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(SahyogSpacing.sm)) {
                repeat(length) { index ->
                    val digit = value.getOrNull(index)
                    val isCurrent = enabled && index == value.length.coerceAtMost(length - 1) &&
                        value.length < length
                    val borderColor = when {
                        isError -> colors.error
                        isCurrent -> colors.primary
                        digit != null -> colors.outline
                        else -> colors.outlineVariant
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 56.dp)
                            .clip(SahyogTheme.shapes.medium)
                            .border(
                                width = if (isCurrent || isError) 2.dp else 1.dp,
                                color = borderColor,
                                shape = SahyogTheme.shapes.medium,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = digit?.toString().orEmpty(),
                            style = SahyogTheme.typography.headlineSmall,
                            color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
                        )
                    }
                }
            }
        },
        cursorBrush = SolidColor(Color.Transparent),
    )
}
