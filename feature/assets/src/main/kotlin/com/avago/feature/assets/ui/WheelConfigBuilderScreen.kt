package com.avago.feature.assets.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Data models ──────────────────────────────────────────────────────────────

enum class AxleRole(val label: String) {
    STEER("Steer"), DRIVE("Drive"), TRAILER("Trailer"), TAG("Tag"), LIFT("Lift")
}

enum class TireType(val label: String) {
    SINGLE("Single"), DUAL("Dual"), SUPER_SINGLE("Super-Single")
}

enum class VehicleCategory(val label: String, val subtitle: String, val defaultAxles: Int) {
    PASSENGER("Passenger / Light Truck", "Sedan, pickup, SUV", 2),
    VAN_BOX("Van / Box Truck", "Cargo van, box truck", 2),
    STRAIGHT_TRUCK("Straight Truck", "Medium-duty single-unit", 2),
    SEMI_TRACTOR("Semi-Tractor", "Class 8 tractor unit", 3),
    TRAILER("Trailer", "Semi-trailer, flatbed", 2),
    BUS_COACH("Bus / Coach", "Transit bus, motorcoach", 2),
    CUSTOM("Custom", "Define your own configuration", 1),
}

data class AxleDef(
    val role: AxleRole = AxleRole.DRIVE,
    val tireType: TireType = TireType.DUAL,
)

data class WheelPosition(val id: String, val label: String)

data class WheelConfig(
    val category: VehicleCategory,
    val axles: List<AxleDef>,
) {
    val positions: List<WheelPosition>
        get() {
            val result = mutableListOf<WheelPosition>()
            axles.forEachIndexed { i, axle ->
                val axleNum = i + 1
                when (axle.tireType) {
                    TireType.SINGLE, TireType.SUPER_SINGLE -> {
                        result += WheelPosition("axle${axleNum}_left", "Axle $axleNum Left")
                        result += WheelPosition("axle${axleNum}_right", "Axle $axleNum Right")
                    }
                    TireType.DUAL -> {
                        result += WheelPosition("axle${axleNum}_left_outer", "Axle $axleNum Left Outer")
                        result += WheelPosition("axle${axleNum}_left_inner", "Axle $axleNum Left Inner")
                        result += WheelPosition("axle${axleNum}_right_inner", "Axle $axleNum Right Inner")
                        result += WheelPosition("axle${axleNum}_right_outer", "Axle $axleNum Right Outer")
                    }
                }
            }
            return result
        }
    val totalWheels: Int get() = positions.size
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WheelConfigBuilderScreen(
    assetId: String,
    onSave: (WheelConfig) -> Unit,
    onBack: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(0) }
    var selectedCategory by remember { mutableStateOf(VehicleCategory.SEMI_TRACTOR) }
    var axles by remember {
        mutableStateOf(listOf(AxleDef(AxleRole.STEER, TireType.SINGLE), AxleDef(AxleRole.DRIVE, TireType.DUAL), AxleDef(AxleRole.DRIVE, TireType.DUAL)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (step) {
                            0 -> "Vehicle Type"
                            1 -> "Axle Configuration"
                            else -> "Review Positions"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (step > 0) step-- else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (step) {
            0 -> VehicleTypeStep(
                selected = selectedCategory,
                modifier = Modifier.padding(padding),
                onSelect = { cat ->
                    selectedCategory = cat
                    axles = buildDefaultAxles(cat)
                    step = 1
                },
            )
            1 -> AxleBuilderStep(
                axles = axles,
                modifier = Modifier.padding(padding),
                onAxlesChanged = { axles = it },
                onReview = { step = 2 },
            )
            else -> WheelPreviewStep(
                config = WheelConfig(selectedCategory, axles),
                modifier = Modifier.padding(padding),
                onSave = { onSave(WheelConfig(selectedCategory, axles)) },
            )
        }
    }
}

private fun buildDefaultAxles(category: VehicleCategory): List<AxleDef> = when (category) {
    VehicleCategory.PASSENGER, VehicleCategory.VAN_BOX ->
        listOf(AxleDef(AxleRole.STEER, TireType.SINGLE), AxleDef(AxleRole.DRIVE, TireType.SINGLE))
    VehicleCategory.STRAIGHT_TRUCK ->
        listOf(AxleDef(AxleRole.STEER, TireType.SINGLE), AxleDef(AxleRole.DRIVE, TireType.DUAL))
    VehicleCategory.SEMI_TRACTOR ->
        listOf(AxleDef(AxleRole.STEER, TireType.SINGLE), AxleDef(AxleRole.DRIVE, TireType.DUAL), AxleDef(AxleRole.DRIVE, TireType.DUAL))
    VehicleCategory.TRAILER ->
        listOf(AxleDef(AxleRole.TRAILER, TireType.DUAL), AxleDef(AxleRole.TRAILER, TireType.DUAL))
    VehicleCategory.BUS_COACH ->
        listOf(AxleDef(AxleRole.STEER, TireType.SINGLE), AxleDef(AxleRole.DRIVE, TireType.DUAL))
    VehicleCategory.CUSTOM ->
        listOf(AxleDef(AxleRole.DRIVE, TireType.DUAL))
}

// ── Step 1: Vehicle type ──────────────────────────────────────────────────────

@Composable
private fun VehicleTypeStep(
    selected: VehicleCategory,
    modifier: Modifier = Modifier,
    onSelect: (VehicleCategory) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Choose the vehicle type to pre-fill an axle layout.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        VehicleCategory.entries.forEach { cat ->
            item(key = cat.name) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(cat) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (cat == selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(if (cat == selected) 4.dp else 1.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(cat.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(cat.subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ── Step 2: Axle builder ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AxleBuilderStep(
    axles: List<AxleDef>,
    modifier: Modifier = Modifier,
    onAxlesChanged: (List<AxleDef>) -> Unit,
    onReview: () -> Unit,
) {
    val totalWheels = axles.map { if (it.tireType == TireType.DUAL) 4 else 2 }.sum()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Cross-section visualization
        AxleCrossSectionView(
            axles = axles,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "Total wheels: $totalWheels  |  Axles: ${axles.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(axles) { index, axle ->
                AxleRow(
                    index = index,
                    axle = axle,
                    canDelete = axles.size > 1,
                    onRoleChange = { role -> onAxlesChanged(axles.toMutableList().also { it[index] = axle.copy(role = role) }) },
                    onTireTypeChange = { type -> onAxlesChanged(axles.toMutableList().also { it[index] = axle.copy(tireType = type) }) },
                    onDelete = { onAxlesChanged(axles.toMutableList().also { it.removeAt(index) }) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { onAxlesChanged(axles + AxleDef()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Axle")
                }
            }
        }

        Button(
            onClick = onReview,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Review Positions") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AxleRow(
    index: Int,
    axle: AxleDef,
    canDelete: Boolean,
    onRoleChange: (AxleRole) -> Unit,
    onTireTypeChange: (TireType) -> Unit,
    onDelete: () -> Unit,
) {
    var roleExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Axle ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete axle",
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded = roleExpanded, onExpandedChange = { roleExpanded = it }) {
                OutlinedTextField(
                    value = axle.role.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Role") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roleExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true,
                )
                ExposedDropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                    AxleRole.entries.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(role.label) },
                            onClick = { onRoleChange(role); roleExpanded = false },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Tire Type", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TireType.entries.forEachIndexed { i, type ->
                    SegmentedButton(
                        selected = axle.tireType == type,
                        onClick = { onTireTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = TireType.entries.size),
                        label = { Text(type.label, fontSize = 11.sp) },
                    )
                }
            }
        }
    }
}

// ── Step 3: Wheel position preview ────────────────────────────────────────────

@Composable
private fun WheelPreviewStep(
    config: WheelConfig,
    modifier: Modifier = Modifier,
    onSave: () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("${config.category.label}  ·  ${config.axles.size} axles  ·  ${config.totalWheels} wheels",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            config.axles.forEachIndexed { axleIndex, axle ->
                item(key = "axle_header_$axleIndex") {
                    Text("Axle ${axleIndex + 1} — ${axle.role.label} / ${axle.tireType.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = if (axleIndex > 0) 8.dp else 0.dp))
                }
                val positionsForAxle = config.positions.filter { it.id.startsWith("axle${axleIndex + 1}_") }
                positionsForAxle.forEach { pos ->
                    item(key = pos.id) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(axleRoleColor(axle.role)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(pos.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(pos.id, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Save Configuration") }
    }
}

// ── Axle cross-section visualization ─────────────────────────────────────────

@Composable
private fun AxleCrossSectionView(axles: List<AxleDef>, modifier: Modifier = Modifier) {
    val axleColors = axles.map { axleRoleColor(it.role) }
    Canvas(modifier = modifier.padding(12.dp)) {
        if (axles.isEmpty()) return@Canvas
        val axleSpacing = size.width / (axles.size + 1)
        val centerY = size.height / 2f
        val tireW = 10f
        val tireH = 30f
        val dualGap = 4f
        val hubR = 6f

        axles.forEachIndexed { i, axle ->
            val x = axleSpacing * (i + 1)
            val color = axleColors[i]
            // axle line
            drawLine(color = Color.Gray, start = Offset(x, centerY - 40f), end = Offset(x, centerY + 40f), strokeWidth = 2f)
            // hub
            drawCircle(color = color, radius = hubR, center = Offset(x, centerY))
            // tires
            when (axle.tireType) {
                TireType.SINGLE, TireType.SUPER_SINGLE -> {
                    val offsetY = 26f
                    drawRect(color = color.copy(alpha = 0.8f),
                        topLeft = Offset(x - tireW / 2, centerY - offsetY - tireH / 2), size = Size(tireW, tireH))
                    drawRect(color = color.copy(alpha = 0.8f),
                        topLeft = Offset(x - tireW / 2, centerY + offsetY - tireH / 2), size = Size(tireW, tireH))
                }
                TireType.DUAL -> {
                    val outerOffsetY = 34f
                    val innerOffsetY = 22f
                    // top pair
                    drawRect(color = color.copy(alpha = 0.8f),
                        topLeft = Offset(x - tireW / 2, centerY - outerOffsetY - tireH / 2), size = Size(tireW, tireH))
                    drawRect(color = color.copy(alpha = 0.6f),
                        topLeft = Offset(x - tireW / 2, centerY - innerOffsetY - tireH / 2), size = Size(tireW, tireH))
                    // bottom pair
                    drawRect(color = color.copy(alpha = 0.8f),
                        topLeft = Offset(x - tireW / 2, centerY + outerOffsetY - tireH / 2), size = Size(tireW, tireH))
                    drawRect(color = color.copy(alpha = 0.6f),
                        topLeft = Offset(x - tireW / 2, centerY + innerOffsetY - tireH / 2), size = Size(tireW, tireH))
                }
            }
        }
    }
}

@Composable
private fun axleRoleColor(role: AxleRole): Color = when (role) {
    AxleRole.STEER -> MaterialTheme.colorScheme.primary
    AxleRole.DRIVE -> MaterialTheme.colorScheme.tertiary
    AxleRole.TRAILER -> MaterialTheme.colorScheme.secondary
    AxleRole.TAG -> MaterialTheme.colorScheme.outline
    AxleRole.LIFT -> MaterialTheme.colorScheme.error
}
