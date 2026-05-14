package cn.ticos.stardust.sample.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import cn.ticos.stardust.sample.model.VoicePhase
import cn.ticos.stardust.sample.ui.theme.AppColors

private val OrbSize = 210.dp

@Composable
fun VoiceHub(
    phase: VoicePhase,
    isConfigured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = phase != VoicePhase.Ready && phase != VoicePhase.Idle && phase != VoicePhase.Error
    val isStopState = isActive || phase == VoicePhase.Idle
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !isConfigured -> AppColors.Gray100
            isStopState -> AppColors.Red500
            else -> AppColors.Blue600
        },
        animationSpec = tween(500),
        label = "backgroundColor"
    )

    val iconColor = if (!isConfigured) AppColors.Gray400 else Color.White
    
    Box(
        modifier = modifier.size(OrbSize),
        contentAlignment = Alignment.Center
    ) {
        // 1. 纯扁平脉冲圆环 (仅在活跃状态下显示)
        // 使用线条(border)扩散代替色块，保持极致简约
        if (isActive) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "scale"
            )
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "alpha"
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .border(2.dp, backgroundColor.copy(alpha = alpha), CircleShape)
            )
        }

        // 2. 静态装饰外环 (利用透明度营造扁平化层次感)
        Box(
            modifier = Modifier
                .size(OrbSize * 0.9f)
                .background(backgroundColor.copy(alpha = 0.1f), CircleShape)
        )

        // 3. 主按钮主体 (纯色块，无阴影，无渐变)
        Box(
            modifier = Modifier
                .size(OrbSize * 0.75f)
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(
                    onClick = onClick,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isStopState) {
                // 核心改动：更精致的圆角扁平色块
                Box(
                        modifier = Modifier
                                .size(40.dp)
                                .background(iconColor, RoundedCornerShape(8.dp))
                )
            } else {
                // 麦克风图标
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "开始对话",
                    modifier = Modifier.size(52.dp),
                    tint = iconColor
                )
            }
        }
    }
}
