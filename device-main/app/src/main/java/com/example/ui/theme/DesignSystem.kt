package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Reusable Tamil-first bilingual heading/label stack:
 * Tamil primary text first, English subtitle immediately below it.
 */
@Composable
fun BilingualText(
    tamil: String,
    english: String,
    modifier: Modifier = Modifier,
    tamilStyle: TextStyle = MaterialTheme.typography.titleMedium,
    englishStyle: TextStyle = MaterialTheme.typography.bodySmall,
    tamilColor: Color = MaterialTheme.colorScheme.onSurface,
    englishColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    textAlign: TextAlign = TextAlign.Start
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment
    ) {
        Text(
            text = tamil,
            style = tamilStyle,
            color = tamilColor,
            fontWeight = FontWeight.Bold,
            textAlign = textAlign
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = english,
            style = englishStyle,
            color = englishColor,
            fontWeight = FontWeight.Medium,
            textAlign = textAlign
        )
    }
}

/**
 * Subtle organic mint-green wave and soft particle glow background matching the reference mockup.
 */
@Composable
fun MagicalWaveBackdrop(
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val waveColorPrimary = if (isDark) Color(0xFF15803D).copy(alpha = 0.16f) else Color(0xFF86EFAC).copy(alpha = 0.25f)
    val waveColorSecondary = if (isDark) Color(0xFF22C55E).copy(alpha = 0.10f) else Color(0xFFBBF7D0).copy(alpha = 0.35f)
    val sparkleColor = if (isDark) Color(0xFF4ADE80).copy(alpha = 0.45f) else Color(0xFF22C55E).copy(alpha = 0.45f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(waveColorSecondary, Color.Transparent),
                center = Offset(w * 0.5f, h * 0.26f),
                radius = w * 0.62f
            ),
            center = Offset(w * 0.5f, h * 0.26f),
            radius = w * 0.62f
        )

        val wavePath1 = Path().apply {
            moveTo(0f, h * 0.18f)
            cubicTo(
                w * 0.25f, h * 0.12f,
                w * 0.72f, h * 0.28f,
                w, h * 0.16f
            )
            lineTo(w, h * 0.36f)
            cubicTo(
                w * 0.68f, h * 0.42f,
                w * 0.28f, h * 0.26f,
                0f, h * 0.34f
            )
            close()
        }
        drawPath(path = wavePath1, color = waveColorPrimary)

        // Subtle decorative sparkle dots around the top hero area
        val sparkles = listOf(
            Offset(w * 0.16f, h * 0.20f) to 3.5.dp.toPx(),
            Offset(w * 0.84f, h * 0.19f) to 4.dp.toPx(),
            Offset(w * 0.12f, h * 0.31f) to 3.dp.toPx(),
            Offset(w * 0.88f, h * 0.30f) to 3.5.dp.toPx(),
            Offset(w * 0.24f, h * 0.35f) to 2.5.dp.toPx(),
            Offset(w * 0.76f, h * 0.36f) to 2.5.dp.toPx()
        )
        sparkles.forEach { (pt, rad) ->
            drawCircle(color = sparkleColor, radius = rad, center = pt)
        }
    }
}

/**
 * Apple-inspired white/translucent rounded glass card with subtle glowing green border and soft shadow.
 */
@Composable
fun MagicalGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val cardModifier = modifier
        .shadow(
            elevation = 6.dp,
            shape = shape,
            ambientColor = Color(0xFF16A34A).copy(alpha = 0.10f),
            spotColor = Color(0xFF16A34A).copy(alpha = 0.12f)
        )
        .then(
            if (onClick != null) {
                Modifier
                    .clip(shape)
                    .clickable(onClick = onClick)
            } else {
                Modifier
            }
        )

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

/**
 * Soft mint-green circular icon container used across Monitor, Alerts & Audio, and Diagnostics cards.
 */
@Composable
fun MagicalIconCircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    circleColor: Color = if (isSystemInDarkTheme()) Color(0xFF1A3828) else MagicalIconCircleBg,
    iconTint: Color = if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF16A34A),
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(circleColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Pill badge for status indicators (`Running`, `Normal`, `Default`, etc.).
 */
@Composable
fun MagicalStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    isPositive: Boolean = true
) {
    val isDark = isSystemInDarkTheme()
    val bg = when {
        isPositive && isDark -> Color(0xFF14532D)
        isPositive -> Color(0xFFDCFCE7)
        isDark -> Color(0xFF3F2E1E)
        else -> Color(0xFFFEF3C7)
    }
    val fg = when {
        isPositive && isDark -> Color(0xFF86EFAC)
        isPositive -> Color(0xFF15803D)
        isDark -> Color(0xFFFCD34D)
        else -> Color(0xFFB45309)
    }

    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

/**
 * Secondary screen top header bar with circular back button `<` and centered Tamil + English title.
 */
@Composable
fun MagicalScreenTopBar(
    tamilTitle: String,
    englishTitle: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            shadowElevation = 2.dp,
            modifier = Modifier.size(40.dp)
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = stringResource(R.string.back_button),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 4.dp)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = tamilTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = englishTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Balance spacer so title stays perfectly centered
        Spacer(modifier = Modifier.size(40.dp))
    }
}
