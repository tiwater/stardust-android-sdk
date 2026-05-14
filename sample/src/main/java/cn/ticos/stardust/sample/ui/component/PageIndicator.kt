package cn.ticos.stardust.sample.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cn.ticos.stardust.sample.ui.theme.AppColors

@Composable
fun PageIndicator(
    count: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(if (isSelected) 8.dp else 6.dp)
                    .then(
                        if (isSelected) Modifier.width(16.dp).height(6.dp) else Modifier.size(6.dp)
                    )
                    .clip(CircleShape)
                    .background(
                        if (isSelected) AppColors.Blue600.copy(alpha = 0.4f) else AppColors.Blue600
                    )
            )
        }
    }
}
