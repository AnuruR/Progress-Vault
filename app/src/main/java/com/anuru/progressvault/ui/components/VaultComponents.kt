package com.anuru.progressvault.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anuru.progressvault.R
import com.anuru.progressvault.ui.theme.VaultColors

/** Surface card with the signature 1dp border; optional accent tints the border. */
@Composable
fun VaultCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val border = BorderStroke(1.dp, accent?.copy(alpha = 0.45f) ?: VaultColors.Border)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = VaultColors.Surface, border = border) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = VaultColors.Surface, border = border) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    }
}

/** Small mono "// label" header in the terminal style. */
@Composable
fun TerminalLabel(text: String, modifier: Modifier = Modifier, color: Color = VaultColors.TextMuted) {
    Text(
        text = stringResource(R.string.terminal_label, text.uppercase()),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

/** Icon inside an accent-tinted rounded square. Decorative unless [contentDescription] is set. */
@Composable
fun AccentIcon(
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = accent, modifier = Modifier.size(size * 0.55f))
    }
}

/** Compact mono chip, e.g. "AUTO-SYNC" or "NOT IN DIAL". */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun VaultPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = VaultColors.Cyan,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = VaultColors.OnAccent,
            disabledContainerColor = VaultColors.Border,
            disabledContentColor = VaultColors.TextMuted,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun VaultSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, VaultColors.BorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.TextPrimary),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun VaultTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    leadingIcon: ImageVector? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    OutlinedTextField(
        visualTransformation = visualTransformation,
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = VaultColors.TextMuted) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VaultColors.Cyan,
            unfocusedBorderColor = VaultColors.Border,
            focusedLabelColor = VaultColors.Cyan,
            cursorColor = VaultColors.Cyan,
            focusedContainerColor = VaultColors.Surface,
            unfocusedContainerColor = VaultColors.Surface,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Row of selectable neon swatches. */
@Composable
fun ColorSwatchRow(
    colors: List<Long>,
    selected: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = listOf(
        R.string.color_cyan, R.string.color_green, R.string.color_purple,
        R.string.color_amber, R.string.color_pink, R.string.color_blue, R.string.color_github_green,
    )
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEachIndexed { index, argb ->
            val color = Color(argb.toInt())
            val isSelected = argb == selected
            val description = stringResource(names.getOrElse(index) { R.string.color_custom })
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (isSelected) 1f else 0.35f))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) VaultColors.TextPrimary else color.copy(alpha = 0.6f),
                        shape = CircleShape,
                    )
                    .clickable(role = Role.RadioButton) { onSelect(argb) }
                    .semantics {
                        contentDescription = description
                        this.selected = isSelected
                    },
            )
        }
    }
}

/** Centered illustration-less empty state with a mono headline. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accent: Color = VaultColors.Cyan,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AccentIcon(icon = icon, accent = accent, size = 64.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = VaultColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** Thin step indicator: filled segments for completed/current steps. */
@Composable
fun StepIndicator(current: Int, total: Int, modifier: Modifier = Modifier, contentDescription: String) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= current) VaultColors.Cyan else VaultColors.Border),
            )
        }
    }
}
