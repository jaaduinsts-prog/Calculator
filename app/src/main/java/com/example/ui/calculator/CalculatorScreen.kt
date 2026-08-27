package com.example.ui.calculator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CalcHistoryEntity
import com.example.ui.components.ArcReactorCoreView
import com.example.ui.theme.ArcReactorCyan
import com.example.ui.theme.ArcReactorDim
import com.example.ui.theme.ArcReactorGlow
import com.example.ui.theme.NanotechBlack
import com.example.ui.theme.StarkCrimson
import com.example.ui.theme.StarkGold
import com.example.ui.theme.StarkTextDim
import com.example.ui.theme.StarkTextPrimary
import com.example.ui.theme.StarkTextSecondary
import com.example.ui.theme.TitaniumBorder
import com.example.ui.theme.TitaniumCard
import com.example.ui.theme.TitaniumDark
import com.example.ui.theme.TitaniumElevated
import com.example.util.ShareUtil
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    displayExpression: String,
    liveResult: String,
    historyList: List<CalcHistoryEntity>,
    isUnlocking: Boolean,
    onButtonClick: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    var showScientific by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    val exprScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NanotechBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar (Standard Calculator look)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = "Calculator",
                        tint = StarkTextDim,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Calculator",
                        color = StarkTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { ShareUtil.shareApk(context) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("calc_share_apk_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share APK",
                            tint = StarkTextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = { showScientific = !showScientific },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Functions,
                            contentDescription = "Scientific Functions",
                            tint = if (showScientific) ArcReactorCyan else StarkTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History",
                            tint = StarkTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Display Screen Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                // Expression display
                Text(
                    text = displayExpression.ifEmpty { "0" },
                    color = if (displayExpression.isEmpty()) StarkTextDim else StarkTextPrimary,
                    fontSize = when {
                        displayExpression.length > 18 -> 24.sp
                        displayExpression.length > 10 -> 34.sp
                        else -> 46.sp
                    },
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(exprScrollState)
                        .testTag("calc_display_expression")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Evaluated result preview
                if (liveResult.isNotBlank() && liveResult != displayExpression) {
                    Text(
                        text = "= $liveResult",
                        color = ArcReactorCyan,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("calc_live_result")
                    )
                }
            }

            // Keypad area
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Optional Scientific row
                AnimatedVisibility(visible = showScientific) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CalcKeyButton(label = "sin", type = KeyType.FUNCTION, modifier = Modifier.weight(1f)) { onButtonClick("sin(") }
                        CalcKeyButton(label = "cos", type = KeyType.FUNCTION, modifier = Modifier.weight(1f)) { onButtonClick("cos(") }
                        CalcKeyButton(label = "tan", type = KeyType.FUNCTION, modifier = Modifier.weight(1f)) { onButtonClick("tan(") }
                        CalcKeyButton(label = "√", type = KeyType.FUNCTION, modifier = Modifier.weight(1f)) { onButtonClick("√(") }
                        CalcKeyButton(label = "^", type = KeyType.FUNCTION, modifier = Modifier.weight(1f)) { onButtonClick("^") }
                    }
                }

                // Row 1: AC, ( ), %, ÷
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalcKeyButton(label = "AC", type = KeyType.ACTION, modifier = Modifier.weight(1f)) { onButtonClick("AC") }
                    CalcKeyButton(label = "( )", type = KeyType.FUNCTION, modifier = Modifier.weight(1f)) { onButtonClick("()") }
                    CalcKeyButton(label = "%", type = KeyType.FUNCTION, modifier = Modifier.weight(1f)) { onButtonClick("%") }
                    CalcKeyButton(label = "÷", type = KeyType.OPERATOR, modifier = Modifier.weight(1f)) { onButtonClick("÷") }
                }

                // Row 2: 7, 8, 9, ×
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalcKeyButton(label = "7", type = KeyType.NUMBER, modifier = Modifier.weight(1f)) { onButtonClick("7") }
                    CalcKeyButton(label = "8", type = KeyType.NUMBER, modifier = Modifier.weight(1f)) { onButtonClick("8") }
                    CalcKeyButton(label = "9", type = KeyType.NUMBER, modifier = Modifier.weight(1f)) { onButtonClick("9") }
                    CalcKeyButton(label = "×", type = KeyType.OPERATOR, modifier = Modifier.weight(1f)) { onButtonClick("×") }
                }

                // Row 3: 4, 5, 6, -
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalcKeyButton(label = "4", type = KeyType.NUMBER, modifier = Modifier.weight(1f)) { onButtonClick("4") }
                    CalcKeyButton(label = "5", type = KeyType.NUMBER, modifier = Modifier.weight(1f)) { onButtonClick("5") }
                    CalcKeyButton(label = "6", type = KeyType.NUMBER, modifier = Modifier.weight(1f)) { onButtonClick("6") }
                    CalcKeyButton(label = "−", type = KeyType.OPERATOR, modifier = Modifier.weight(1f)) { onButtonClick("-") }
                }

                // Row 4: 1, 2, 3, +
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalcKeyButton(label = "1", type = KeyType.NUMBER, modifier = Modifier.weight(1f)) { onButtonClick("1") }
                    CalcKeyButton(label = "2", type = KeyType.NUMBER, modifier = Modifier.weight(1f)) { onButtonClick("2") }
                    CalcKeyButton(label = "3", type = KeyType.NUMBER, modifier = Modifier.weight(1f)) { onButtonClick("3") }
                    CalcKeyButton(label = "+", type = KeyType.OPERATOR, modifier = Modifier.weight(1f)) { onButtonClick("+") }
                }

                // Row 5: 0, ., ⌫, =
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalcKeyButton(label = "0", type = KeyType.NUMBER, modifier = Modifier.weight(1f)) { onButtonClick("0") }
                    CalcKeyButton(label = ".", type = KeyType.NUMBER, modifier = Modifier.weight(1f)) { onButtonClick(".") }
                    CalcKeyButton(label = "⌫", type = KeyType.FUNCTION, modifier = Modifier.weight(1f)) { onButtonClick("BACK") }
                    CalcKeyButton(label = "=", type = KeyType.EQUALS, modifier = Modifier.weight(1f)) { onButtonClick("=") }
                }
            }
        }

        // Secret Unlocking Holographic Arc Reactor HUD Surge Overlay
        AnimatedVisibility(
            visible = isUnlocking,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "unlock_surge")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "unlock_scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NanotechBlack.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.scale(pulseScale)) {
                        ArcReactorCoreView(sizeDp = 120, isPulsing = true)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "PROTOCOL 3000 AUTHENTICATED",
                        color = ArcReactorCyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "J.A.R.V.I.S.: Welcome back, Mr. Stark.",
                        color = StarkGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "DECRYPTING STARK SECURE SUBNET...",
                        color = StarkTextDim,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }

    // Calculation History Sheet
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = TitaniumDark,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CALCULATION LOGS",
                        color = StarkTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = { showHistorySheet = false }, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = StarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (historyList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No history recorded yet.", color = StarkTextDim, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        items(historyList) { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = TitaniumElevated),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(text = item.expression, color = StarkTextSecondary, fontSize = 12.sp)
                                    Text(
                                        text = "= ${item.result}",
                                        color = ArcReactorCyan,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(TitaniumElevated)
                            .clickable { onClearHistory() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Clear History", color = StarkCrimson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

enum class KeyType {
    NUMBER, OPERATOR, FUNCTION, ACTION, EQUALS
}

@Composable
fun CalcKeyButton(
    label: String,
    type: KeyType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val bgColor = when (type) {
        KeyType.NUMBER -> TitaniumElevated
        KeyType.OPERATOR -> TitaniumCard
        KeyType.FUNCTION -> TitaniumCard
        KeyType.ACTION -> StarkCrimson.copy(alpha = 0.2f)
        KeyType.EQUALS -> ArcReactorCyan
    }

    val textColor = when (type) {
        KeyType.NUMBER -> StarkTextPrimary
        KeyType.OPERATOR -> ArcReactorCyan
        KeyType.FUNCTION -> StarkGold
        KeyType.ACTION -> StarkCrimson
        KeyType.EQUALS -> NanotechBlack
    }

    val borderColor = when (type) {
        KeyType.EQUALS -> ArcReactorGlow
        KeyType.ACTION -> StarkCrimson.copy(alpha = 0.4f)
        else -> TitaniumBorder
    }

    Box(
        modifier = modifier
            .aspectRatio(1.25f)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = ArcReactorCyan)
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .testTag("calc_key_$label"),
        contentAlignment = Alignment.Center
    ) {
        if (label == "BACK") {
            Icon(
                imageVector = Icons.Default.Backspace,
                contentDescription = "Backspace",
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = label,
                color = textColor,
                fontSize = if (label.length > 2) 16.sp else 22.sp,
                fontWeight = if (type == KeyType.EQUALS || type == KeyType.ACTION) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
