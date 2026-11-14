package com.avago.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avago.core.ui.R

/**
 * Horizontal wizard stepper: numbered dots (check when done), connector bar and
 * labels. Completed steps are tappable (≥ 48dp) when [onStepClick] is set.
 * Announced as "Step 2 of 4: Condition".
 */
@Composable
fun StepIndicator(
    steps: List<String>,
    current: Int,
    modifier: Modifier = Modifier,
    onStepClick: ((Int) -> Unit)? = null,
) {
    val progress = if (steps.size <= 1) 1f else current.toFloat() / (steps.size - 1)
    val summary = stringResource(R.string.core_step_progress, current + 1, steps.size, steps.getOrElse(current) { "" })
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).clearAndSetSemantics { contentDescription = summary },
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            steps.forEachIndexed { i, label ->
                val done = i < current
                val active = i == current
                val state = when {
                    done -> stringResource(R.string.core_step_done)
                    active -> stringResource(R.string.core_step_current)
                    else -> ""
                }
                val clickable = onStepClick != null && done
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .then(if (clickable) Modifier.clickable(role = Role.Tab) { onStepClick?.invoke(i) } else Modifier)
                        .semantics(mergeDescendants = true) { stateDescription = state }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val bg = when {
                        done -> MaterialTheme.colorScheme.primary
                        active -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val fg = when {
                        done -> MaterialTheme.colorScheme.onPrimary
                        active -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .then(if (active) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
                        } else {
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = fg)
                        }
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * One entry of a vertical history timeline (order events, handovers, payments).
 * The rail line connects consecutive entries; the dot carries an icon so tone is
 * not conveyed by colour alone.
 */
@Composable
fun TimelineEntry(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
    supporting: String? = null,
    time: String? = null,
    trailing: String? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
) {
    val c = toneColors(tone)
    val line = MaterialTheme.colorScheme.outline
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp).semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.width(32.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(12.dp).background(if (isFirst) androidx.compose.ui.graphics.Color.Transparent else line))
            Box(Modifier.size(28.dp).clip(CircleShape).background(c.container), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = c.content, modifier = Modifier.size(16.dp))
            }
            Box(Modifier.width(2.dp).weight(1f).background(if (isLast) androidx.compose.ui.graphics.Color.Transparent else line))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                trailing?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }
            supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            time?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
