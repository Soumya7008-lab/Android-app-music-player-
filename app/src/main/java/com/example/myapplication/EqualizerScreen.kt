package com.example.myapplication

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EqualizerScreen(
    viewModel: MusicViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val themeColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val haptic = rememberHapticFeedback(uiState.isHapticsEnabled)
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<String?>(null) }

    val noParentScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return Offset(x = available.x, y = 0f)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return Velocity(x = available.x, y = 0f)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(bottom = 85.dp) // Perfectly tuned for mini-player
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- HEADER ---
        Text(
            "EQUALIZER",
            style = MaterialTheme.typography.titleLarge,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp) // More breathing room
        )

        // --- 3D VISUALIZER ---
        DynamicEqualizer(
            data = uiState.visualizerData,
            modifier = Modifier
                .height(44.dp)
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp)) // Utilized empty space

        // --- PRESET CHIPS ---
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "PRESETS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(noParentScrollConnection),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

        Spacer(modifier = Modifier.height(14.dp))

        // --- MASTER VOLUME ---
        MasterVolumeBar(
            value = uiState.masterVolume,
            onValueChange = { viewModel.setMasterVolume(it) },
            modifier = Modifier.padding(horizontal = 4.dp)
        )

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

        Spacer(modifier = Modifier.height(8.dp)) // Added space above the header

        // --- HI-FI STUDIO CONTROLS ---
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "TITAN STUDIO ENGINE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = themeColor,
                modifier = Modifier.padding(bottom = 0.dp) // Removed highlighted space below header
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    StudioControlSlider("CLARITY", uiState.clarityLevel) { viewModel.updateTitanParams(clarity = it) }
                    StudioControlSlider("SNAPPY", uiState.snappiness) { viewModel.updateTitanParams(snappiness = it) }
                }
                Column(modifier = Modifier.weight(1f)) {
                    StudioControlSlider("STAGE", uiState.soundstageWidth) { viewModel.updateTitanParams(soundstage = it) }
                    StudioControlSlider("TEMPO", (uiState.tempo - 0.5f) / 1.5f) { viewModel.setTempo(0.5f + it * 1.5f) }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- ACTION BUTTONS (16D & SAVE) ---
        val metalBrush = if (isDark) {
            Brush.verticalGradient(colors = listOf(Color(0xFFFFFFFF), Color(0xFFE5E5E5)))
        } else {
            Brush.verticalGradient(colors = listOf(Color(0xFF2C2C2C), Color(0xFF000000)))
        }
        val contentColor = if (isDark) Color.Black else Color.White

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp) // Narrower breadth
                .height(44.dp), // Slightly shorter too for a sleeker look
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 16D AUDIO MODE BUTTON
            val is16D = uiState.is16DEnabled
            val inactiveBrush = if (isDark) {
                Brush.verticalGradient(colors = listOf(Color(0xFF222222), Color(0xFF111111)))
            } else {
                Brush.verticalGradient(colors = listOf(Color(0xFFF0F0F0), Color(0xFFE0E0E0)))
            }
            
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .bounceClick { viewModel.toggle16D() }
                    .shadow(
                        elevation = if (is16D) 20.dp else 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = if (is16D) themeColor else Color.Black,
                        ambientColor = if (is16D) themeColor else Color.Black
                    )
                    .graphicsLayer {
                        shape = RoundedCornerShape(16.dp)
                        clip = true
                    }
                    .background(if (is16D) themeColor else Color.Transparent)
                    .then(if (!is16D) Modifier.background(inactiveBrush) else Modifier),
                color = Color.Transparent
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "16D MODE", 
                        fontWeight = FontWeight.Black, 
                        fontSize = 12.sp, 
                        color = if (is16D) (if (isDark) Color.Black else Color.White) else (if (isDark) Color.White else Color.Black),
                        letterSpacing = 1.sp
                    )
                }
            }

            // RESET STUDIO ENGINE BUTTON (same design as other buttons)
            Surface(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
                    .bounceClick { viewModel.resetStudioEngine() }
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = if (isDark) themeColor else Color.Black,
                        ambientColor = if (isDark) themeColor else Color.Black
                    )
                    .graphicsLayer {
                        shape = RoundedCornerShape(16.dp)
                        clip = true
                    }
                    .drawBehind {
                        val rimColor = if (isDark) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.2f)
                        drawRoundRect(
                            color = rimColor,
                            style = Stroke(width = 1.2.dp.toPx()),
                            cornerRadius = CornerRadius(16.dp.toPx())
                        )
                    }
                    .background(metalBrush),
                color = Color.Transparent
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentColor)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RESET", fontWeight = FontWeight.Black, fontSize = 12.sp, color = contentColor, letterSpacing = 1.sp)
                }
            }

            // SAVE AS CUSTOM PRESET BUTTON
            Surface(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
                    .bounceClick { showSaveDialog = true }
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = if (isDark) themeColor else Color.Black,
                        ambientColor = if (isDark) themeColor else Color.Black
                    )
                    .graphicsLayer {
                        shape = RoundedCornerShape(16.dp)
                        clip = true
                    }
                    .drawBehind {
                        val rimColor = if (isDark) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.2f)
                        drawRoundRect(
                            color = rimColor,
                            style = Stroke(width = 1.2.dp.toPx()),
                            cornerRadius = CornerRadius(16.dp.toPx())
                        )
                    }
                    .background(metalBrush),
                color = Color.Transparent
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentColor)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SAVE", fontWeight = FontWeight.Black, fontSize = 12.sp, color = contentColor, letterSpacing = 1.sp)
                }
            }
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
fun StudioControlSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 0.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(value * 100).toInt()}%", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.height(16.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current

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
                onClick = {
                    if (PreferenceManager.isHapticsEnabled(context)) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
                onLongClick = {
                    if (PreferenceManager.isHapticsEnabled(context)) view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    onLongClick()
                }
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
