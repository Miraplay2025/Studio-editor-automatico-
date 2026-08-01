package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransitionConfigItem
import com.example.data.model.TransitionType
import com.example.ui.theme.*

@Composable
fun TransitionPanel(
    transitionItems: List<TransitionConfigItem>,
    transitionDurationSec: Float,
    onToggleActive: (TransitionType) -> Unit,
    onDurationChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeCount = transitionItems.count { it.isActive }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "20 Transições Suaves",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "O motor escolherá aleatoriamente entre as ativas",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                )
            }

            Surface(
                color = if (activeCount > 0) PrimaryPurple.copy(alpha = 0.3f) else WarningRed.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "$activeCount/20 Ativas",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (activeCount > 0) SecondaryCyan else WarningRed,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Duration Slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Duração da Transição:",
                style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary)
            )
            Text(
                text = String.format("%.1fs", transitionDurationSec),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = SecondaryCyan,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Slider(
            value = transitionDurationSec,
            onValueChange = onDurationChanged,
            valueRange = 0.5f..2.0f,
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = SecondaryCyan,
                activeTrackColor = PrimaryPurple,
                inactiveTrackColor = BorderDark
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("transition_duration_slider")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Grid of 20 Transitions
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(transitionItems) { item ->
                val isSelected = item.isActive

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) DarkSurfaceVariant else DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) SecondaryCyan else BorderDark
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clickable { onToggleActive(item.type) }
                        .testTag("transition_toggle_${item.type.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = item.type.label,
                                tint = if (isSelected) SecondaryCyan else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = item.type.label,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else TextSecondary
                                ),
                                maxLines = 1
                            )
                        }

                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Ativa" else "Inativa",
                            tint = if (isSelected) SecondaryCyan else TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
