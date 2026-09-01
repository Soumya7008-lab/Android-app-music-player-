package com.example.myapplication

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EqualizerScreen(
    viewModel: MusicViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val themeColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(bottom = 110.dp) // Space for mini-player
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- HEADER ---
        Text(
            "EQUALIZER",
            style = MaterialTheme.typography.titleLarge,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )

        // --- 3D VISUALIZER (COMPACT) ---
        DynamicEqualizer(
            data = uiState.visualizerData,
            modifier = Modifier
                .height(50.dp) // SIGNIFICANTLY REDUCED
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- PRESET CHIPS ---
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "PRESETS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp) // Reduced padding
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.eqPresets) { preset ->
                    val isBuiltIn = preset.name in listOf("Flat", "Bass Boost", "Vocals", "High Hat", "Cinema")
                    PresetChip(
                        label = preset.name,
                        isSelected = uiState.selectedPreset == preset.name,
                        onClick = { viewModel.applyPreset(preset.name) },
                        onLongClick = { if (!isBuiltIn) presetToDelete = preset.name }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp)) // Compact space

        // --- MASTER VOLUME ---
        MasterVolumeBar(
            value = uiState.masterVolume,
            onValueChange = { viewModel.setMasterVolume(it) },
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.weight(1f)) // Utilize empty space

        // --- EQ BANDS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val labels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
            uiState.eqBands.forEachIndexed { index, value ->
                EqualizerSlider(
                    label = labels[index],
                    value = value,
                    onValueChange = { viewModel.updateEqBand(index, it) }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f)) // Balanced bottom spacing

        // --- SAVE CUSTOM ACTION ---
        Button(
            onClick = { showSaveDialog = true },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = themeColor,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .graphicsLayer {
                    if (isDark) {
                        shadowElevation = 12.dp.toPx()
                        spotShadowColor = themeColor.copy(alpha = 0.5f)
                    }
                }
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("SAVE AS CUSTOM PRESET", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }

    if (showSaveDialog) {
        SavePresetDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                viewModel.saveCustomPreset(name)
                showSaveDialog = false
            }
        )
    }

    if (presetToDelete != null) {
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text("Delete Preset", fontWeight = FontWeight.Bold) },
            text = { Text("Delete '$presetToDelete'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePreset(presetToDelete!!)
                        presetToDelete = null
                    }
                ) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun SavePresetDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Save Custom Preset", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Preset Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name) },
                shape = RoundedCornerShape(12.dp)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val themeColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background == Color.Black

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ChipScale"
    )

    Box(
        modifier = Modifier
            .height(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) themeColor else MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .drawBehind {
                if (isSelected && isDark) {
                    drawRoundRect(
                        color = themeColor.copy(alpha = 0.3f),
                        style = Stroke(width = 2.dp.toPx()),
                        cornerRadius = CornerRadius(16.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}
