package com.avago.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Semantic status tone shared by pills, KPI tiles, banners and timelines.
 *
 * The app theme only customises the primary/secondary accents, so M3 default
 * `tertiaryContainer` / `errorContainer` are purple-ish and clash with the
 * iOS-like surfaces. These pairs are tuned for ≥ 4.5:1 text contrast on both
 * the light (#FFFFFF) and dark (#161B22) card surfaces.
 */
enum class StatusTone { Neutral, Info, Success, Warning, Danger, Accent }

@Immutable
data class ToneColors(val container: Color, val content: Color, val accent: Color)

/** True when the active colour scheme is dark (works with dynamic colour too). */
@Composable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

@Composable
fun toneColors(tone: StatusTone): ToneColors {
    val dark = isDarkScheme()
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        StatusTone.Neutral -> ToneColors(scheme.surfaceVariant, scheme.onSurfaceVariant, scheme.onSurfaceVariant)
        StatusTone.Info -> if (dark) {
            ToneColors(Color(0xFF102A44), Color(0xFF79C0FF), Color(0xFF58A6FF))
        } else {
            ToneColors(Color(0xFFDDF4FF), Color(0xFF0550AE), Color(0xFF0969DA))
        }
        StatusTone.Success -> if (dark) {
            ToneColors(Color(0xFF14301C), Color(0xFF56D364), Color(0xFF46954A))
        } else {
            ToneColors(Color(0xFFDAFBE1), Color(0xFF116329), Color(0xFF1A7F37))
        }
        StatusTone.Warning -> if (dark) {
            ToneColors(Color(0xFF3A2A10), Color(0xFFE3B341), Color(0xFFD29922))
        } else {
            ToneColors(Color(0xFFFFF1D6), Color(0xFF7A4500), Color(0xFFBF8700))
        }
        StatusTone.Danger -> if (dark) {
            ToneColors(Color(0xFF3D1518), Color(0xFFFF7B72), Color(0xFFF85149))
        } else {
            ToneColors(Color(0xFFFFEBE9), Color(0xFFA40E26), Color(0xFFCF222E))
        }
        StatusTone.Accent -> if (dark) {
            ToneColors(Color(0xFF2B1F45), Color(0xFFD2A8FF), Color(0xFFA371F7))
        } else {
            ToneColors(Color(0xFFFBEFFF), Color(0xFF6639BA), Color(0xFF8250DF))
        }
    }
}

/**
 * Compact status label: icon + text on a tonal container. Colour is never the
 * only cue — the text always carries the meaning and the icon reinforces it.
 */
@Composable
fun StatusPill(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val c = toneColors(tone)
    Surface(shape = RoundedCornerShape(50), color = c.container, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = if (icon != null) 8.dp else 10.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = c.content, modifier = Modifier.size(14.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = c.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
