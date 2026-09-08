package com.example.myapplication

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    onComplete: (String) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    var userName by remember { mutableStateOf("") }
    
    val isDark = MaterialTheme.colorScheme.background == Color.Black

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                }.using(SizeTransform(clip = false))
            },
            label = "StepTransition"
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
    }
}

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated Icon
        val infiniteTransition = rememberInfiniteTransition(label = "IconPulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "IconScale"
        )

        Box(
            modifier = Modifier
                .size(140.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.2f), Color.Transparent)
                        ),
                        radius = size.maxDimension * 0.8f
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = primaryColor
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            "Welcome to Music",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Let's dive inside the world of hi-res unlimited music offline",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            lineHeight = 28.sp
        )

        Spacer(modifier = Modifier.height(64.dp))

        ModernOnboardingButton(
            text = "LET'S GET STARTED",
            onClick = onNext
        )
    }
}

@Composable
fun NameInputStep(
    name: String,
    onNameChange: (String) -> Unit,
    onFinish: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "What's your name?",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "You can always change it later in settings",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text("Enter your name...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                cursorColor = primaryColor
            )
        )

        Spacer(modifier = Modifier.height(48.dp))

        ModernOnboardingButton(
            text = "LET'S ENJOY",
            enabled = name.isNotBlank(),
            onClick = onFinish
        )
    }
}

@Composable
fun ModernOnboardingButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black
    val primaryColor = if (enabled) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)
    val contentColor = if (isDark) Color.Black else Color.White

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                shadowElevation = 12.dp.toPx()
                shape = RoundedCornerShape(28.dp)
            },
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = primaryColor,
            contentColor = contentColor,
            disabledContainerColor = Color.Gray.copy(alpha = 0.2f),
            disabledContentColor = Color.Gray.copy(alpha = 0.5f)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}
