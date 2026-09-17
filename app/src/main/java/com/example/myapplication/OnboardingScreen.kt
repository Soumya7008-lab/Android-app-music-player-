package com.example.myapplication

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

@Composable
fun OnboardingScreen(
    onComplete: (String) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var userName by remember { mutableStateOf("") }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- HIGH-VISIBILITY SCATTERED QUADRANT ORBS ---
        PremiumBackgroundOrbs()

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    (fadeIn(animationSpec = tween(600)) + scaleIn(initialScale = 0.92f, animationSpec = spring(Spring.DampingRatioLowBouncy)))
                        .togetherWith(fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 1.05f))
                } else {
                    (fadeIn(animationSpec = tween(600)) + scaleIn(initialScale = 1.05f))
                        .togetherWith(fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 0.92f))
                }.using(SizeTransform(clip = false))
            },
            label = "StepTransition",
            modifier = Modifier.fillMaxSize()
        ) { step ->
            when (step) {
                0 -> WelcomeStep(onNext = { currentStep = 1 })
                1 -> NameInputStep(
                    name = userName,
                    onNameChange = { userName = it },
                    onFinish = { onComplete(userName) }
                )
            }
        }
        
        PremiumStepIndicator(currentStep = currentStep, totalSteps = 2)
    }
}

@Composable
fun PremiumBackgroundOrbs() {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbMotion")
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val primaryColor = MaterialTheme.colorScheme.primary
    
    // INCREASED ALPHA FOR MAXIMUM VISIBILITY
    val orbAlpha = if (isDark) 0.5f else 0.25f

    // TOP ORBS (Stay in top quadrant)
    val orbT1X by infiniteTransition.animateFloat(0.1f, 0.4f, infiniteRepeatable(tween(15000), RepeatMode.Reverse), "T1X")
    val orbT1Y by infiniteTransition.animateFloat(0.05f, 0.3f, infiniteRepeatable(tween(22000), RepeatMode.Reverse), "T1Y")
    val orbT2X by infiniteTransition.animateFloat(0.6f, 0.9f, infiniteRepeatable(tween(18000), RepeatMode.Reverse), "T2X")
    val orbT2Y by infiniteTransition.animateFloat(0.05f, 0.3f, infiniteRepeatable(tween(14000), RepeatMode.Reverse), "T2Y")
    
    // BOTTOM ORBS (Stay in bottom quadrant)
    val orbB1X by infiniteTransition.animateFloat(0.1f, 0.4f, infiniteRepeatable(tween(20000), RepeatMode.Reverse), "B1X")
    val orbB1Y by infiniteTransition.animateFloat(0.7f, 0.95f, infiniteRepeatable(tween(16000), RepeatMode.Reverse), "B1Y")
    val orbB2X by infiniteTransition.animateFloat(0.6f, 0.9f, infiniteRepeatable(tween(12000), RepeatMode.Reverse), "B2X")
    val orbB2Y by infiniteTransition.animateFloat(0.7f, 0.95f, infiniteRepeatable(tween(25000), RepeatMode.Reverse), "B2Y")
    
    // MIDDLE SMALL ORBS (Stay in mid lane)
    val orbM1X by infiniteTransition.animateFloat(0.3f, 0.7f, infiniteRepeatable(tween(25000), RepeatMode.Reverse), "M1X")
    val orbM1Y by infiniteTransition.animateFloat(0.4f, 0.6f, infiniteRepeatable(tween(18000), RepeatMode.Reverse), "M1Y")
    val orbM2X by infiniteTransition.animateFloat(0.2f, 0.8f, infiniteRepeatable(tween(20000), RepeatMode.Reverse), "M2X")
    val orbM2Y by infiniteTransition.animateFloat(0.45f, 0.55f, infiniteRepeatable(tween(22000), RepeatMode.Reverse), "M2Y")

    Canvas(modifier = Modifier.fillMaxSize().blur(40.dp)) { // REDUCED BLUR TO BE MORE VISIBLE
        val largeRadius = size.width * 0.35f
        val smallRadius = size.width * 0.15f

        // Top Orbs
        listOf(Offset(orbT1X, orbT1Y), Offset(orbT2X, orbT2Y)).forEach { p ->
            drawCircle(
                brush = Brush.radialGradient(listOf(primaryColor.copy(alpha = orbAlpha), Color.Transparent), center = Offset(p.x * size.width, p.y * size.height)),
                radius = largeRadius, center = Offset(p.x * size.width, p.y * size.height)
            )
        }
        // Bottom Orbs
        listOf(Offset(orbB1X, orbB1Y), Offset(orbB2X, orbB2Y)).forEach { p ->
            drawCircle(
                brush = Brush.radialGradient(listOf(primaryColor.copy(alpha = orbAlpha), Color.Transparent), center = Offset(p.x * size.width, p.y * size.height)),
                radius = largeRadius, center = Offset(p.x * size.width, p.y * size.height)
            )
        }
        // Mid Orbs
        listOf(Offset(orbM1X, orbM1Y), Offset(orbM2X, orbM2Y)).forEach { p ->
            drawCircle(
                brush = Brush.radialGradient(listOf(primaryColor.copy(alpha = orbAlpha * 1.2f), Color.Transparent), center = Offset(p.x * size.width, p.y * size.height)),
                radius = smallRadius, center = Offset(p.x * size.width, p.y * size.height)
            )
        }
    }
}

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val chromeBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFF9E9E9E))
    )

    Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SonicOrb(primaryColor)

            Spacer(modifier = Modifier.height(48.dp))

            // FIXED TEXT WRAPPING
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Welcome to",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Music",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp,
                        brush = chromeBrush
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Dive into high-fidelity unlimited music, optimized for pure offline enjoyment.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 28.sp,
                    letterSpacing = 0.5.sp
                ),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {
            PremiumFloatingButton(
                text = "GET STARTED",
                onClick = onNext
            )
        }
    }
}

@Composable
fun SonicOrb(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "SonicWave")
    
    Box(contentAlignment = Alignment.Center) {
        repeat(2) { index ->
            val delay = index * 1500
            val scale by infiniteTransition.animateFloat(1f, 1.6f, infiniteRepeatable(tween(3000, delayMillis = delay, easing = FastOutSlowInEasing), RepeatMode.Restart), "Wave$index")
            val alpha by infiniteTransition.animateFloat(0.4f, 0f, infiniteRepeatable(tween(3000, delayMillis = delay, easing = FastOutSlowInEasing), RepeatMode.Restart), "Alpha$index")
            
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                    .drawBehind { drawCircle(color = color, style = Stroke(width = 2.dp.toPx())) }
            )
        }

        Box(
            modifier = Modifier
                .size(90.dp)
                .shadow(24.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(color, color.copy(alpha = 0.6f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.Black // JET BLACK SYMBOL
            )
        }
    }
}

@Composable
fun NameInputStep(
    name: String,
    onNameChange: (String) -> Unit,
    onFinish: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val chromeBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFF9E9E9E))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Column {
                Text(
                    text = "Personalize your",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "experience.",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        brush = chromeBrush
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "What should we call you? You can update this anytime in Settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = { Text("Enter your name", style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(32.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                    cursorColor = primaryColor,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            
            Spacer(modifier = Modifier.height(140.dp))
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {
            PremiumFloatingButton(
                text = "LET'S ENJOY",
                enabled = name.isNotBlank(),
                onClick = onFinish
            )
        }
    }
}

@Composable
fun PremiumFloatingButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "PressScale"
    )

    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val themeColor = MaterialTheme.colorScheme.primary
    val buttonColor = if (enabled) themeColor else Color.Gray.copy(alpha = 0.3f)
    val contentColor = if (isDark) Color.Black else Color.White

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                shadowElevation = if (enabled) 20.dp.toPx() else 0f
                shape = RoundedCornerShape(30.dp)
                clip = true
            }
            .height(60.dp)
            .wrapContentWidth()
            .background(buttonColor)
            .clickable(
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                interactionSource = interactionSource,
                indication = null
            )
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = contentColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
        }
    }
}

@Composable
fun PremiumStepIndicator(currentStep: Int, totalSteps: Int) {
    val themeColor = MaterialTheme.colorScheme.primary
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 24.dp, start = 32.dp, end = 32.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep
            val width by animateDpAsState(
                targetValue = if (isActive) 40.dp else 12.dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "IndicatorWidth"
            )
            
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .height(4.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (isActive) themeColor else themeColor.copy(alpha = 0.2f))
            )
        }
    }
}
