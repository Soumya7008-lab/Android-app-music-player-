package com.example.myapplication

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onBackClick: () -> Unit,
    userName: String,
    onUpdateName: (String) -> Unit,
    isHapticsEnabled: Boolean,
    onHapticsToggle: (Boolean) -> Unit
) {
    var showNameDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "SETTINGS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                "Appearance",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // INNOVATIVE THEME SWITCHER
            ThemeSwitcherItem(
                isDarkTheme = isDarkTheme,
                onClick = onThemeToggle
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsItem(
                icon = Icons.Default.Edit,
                title = "User Name",
                subtitle = userName,
                onClick = { showNameDialog = true }
            )

            val view = androidx.compose.ui.platform.LocalView.current
            SettingsItem(
                icon = androidx.compose.material.icons.Icons.Default.TouchApp,
                title = "Premium Haptics",
                subtitle = if (isHapticsEnabled) "Enabled" else "Disabled",
                onClick = {
                    val newState = !isHapticsEnabled
                    onHapticsToggle(newState)
                    if (newState) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                },
                trailing = {
                    Switch(
                        checked = isHapticsEnabled,
                        onCheckedChange = null, // Disable switch interaction, let parent handle click
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "About",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            StaticSettingsItem(
                icon = Icons.Default.Info,
                title = "App Version",
                subtitle = "1.0.0-Beta"
            )

            SettingsItem(
                icon = Icons.Default.Person,
                title = "Developer",
                subtitle = "MyMusic Team",
                onClick = {}
            )

            Spacer(modifier = Modifier.weight(1f))

            // Footer / Branding
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    Text(
                        "MY MUSIC PLAYER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        letterSpacing = 4.sp
                    )
                }
            }
        }
    }

    if (showNameDialog) {
        EditNameDialog(
            currentName = userName,
            onDismiss = { showNameDialog = false },
            onConfirm = { 
                onUpdateName(it)
                showNameDialog = false
            }
        )
    }
}

@Composable
fun ThemeSwitcherItem(
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "PressScale"
    )

    val metalBrush = if (isDark) {
        Brush.verticalGradient(colors = listOf(Color(0xFF1C1C1C), Color(0xFF0F0F0F)))
    } else {
        Brush.verticalGradient(colors = listOf(Color(0xFFF9F9F9), Color(0xFFEBEBEB)))
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current

    Surface(
        onClick = {
            if (PreferenceManager.isHapticsEnabled(context)) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
            onClick()
        },
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(82.dp) // Slightly taller for more presence
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shape = RoundedCornerShape(24.dp)
                clip = true
                if (isDark) {
                    shadowElevation = 8.dp.toPx()
                    spotShadowColor = Color.White.copy(alpha = 0.15f)
                } else {
                    shadowElevation = 6.dp.toPx()
                    spotShadowColor = Color.Black.copy(alpha = 0.1f)
                }
            }
            .drawBehind {
                val rimColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
                drawRoundRect(
                    color = rimColor,
                    style = Stroke(width = 1.2.dp.toPx()),
                    cornerRadius = CornerRadius(24.dp.toPx())
                )
            }
            .background(metalBrush, shape = RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isDarkTheme) "Deep Obsidian" else "Pure Crystal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // THE INNOVATIVE BUTTON: AN ECLIPSE ANIMATION
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .drawBehind {
                        drawCircle(
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val iconRotation by animateFloatAsState(
                    targetValue = if (isDarkTheme) 0f else 180f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy),
                    label = "Rotation"
                )

                Box(modifier = Modifier.graphicsLayer { rotationZ = iconRotation }) {
                    AnimatedContent(
                        targetState = isDarkTheme,
                        transitionSpec = {
                            (scaleIn(initialScale = 0.4f) + fadeIn()).togetherWith(scaleOut(targetScale = 0.4f) + fadeOut())
                        },
                        label = "IconSwitch"
                    ) { dark ->
                        Icon(
                            imageVector = if (dark) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val metalBrush = if (isDark) {
        Brush.verticalGradient(colors = listOf(Color(0xFF1C1C1C), Color(0xFF0F0F0F)))
    } else {
        Brush.verticalGradient(colors = listOf(Color(0xFFF9F9F9), Color(0xFFEBEBEB)))
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current

    Surface(
        onClick = {
            if (PreferenceManager.isHapticsEnabled(context)) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
            onClick()
        },
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(72.dp)
            .graphicsLayer {
                shape = RoundedCornerShape(20.dp)
                clip = true
                if (isDark) {
                    shadowElevation = 8.dp.toPx()
                    spotShadowColor = Color.White.copy(alpha = 0.15f)
                } else {
                    shadowElevation = 6.dp.toPx()
                    spotShadowColor = Color.Black.copy(alpha = 0.1f)
                }
            }
            .drawBehind {
                val rimColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
                drawRoundRect(
                    color = rimColor,
                    style = Stroke(width = 1.2.dp.toPx()),
                    cornerRadius = CornerRadius(20.dp.toPx())
                )
            }
            .background(metalBrush, shape = RoundedCornerShape(20.dp))
    ) {
        SettingsItemContent(icon, title, subtitle, trailing)
    }
}

@Composable
fun StaticSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val metalBrush = if (isDark) {
        Brush.verticalGradient(colors = listOf(Color(0xFF1C1C1C), Color(0xFF0F0F0F)))
    } else {
        Brush.verticalGradient(colors = listOf(Color(0xFFF9F9F9), Color(0xFFEBEBEB)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(72.dp)
            .graphicsLayer {
                shape = RoundedCornerShape(20.dp)
                clip = true
            }
            .drawBehind {
                val rimColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
                drawRoundRect(
                    color = rimColor,
                    style = Stroke(width = 1.2.dp.toPx()),
                    cornerRadius = CornerRadius(20.dp.toPx())
                )
            }
            .background(metalBrush, shape = RoundedCornerShape(20.dp))
    ) {
        SettingsItemContent(icon, title, subtitle, null)
    }
}

@Composable
fun SettingsItemContent(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun EditNameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Update Name", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                shape = RoundedCornerShape(12.dp)
            ) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
