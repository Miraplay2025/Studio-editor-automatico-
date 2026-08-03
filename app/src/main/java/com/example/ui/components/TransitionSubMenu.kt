package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TransitionType

@Composable
fun TransitionSubMenu(
    selectedTransitionsPool: Set<TransitionType>,
    isSelectionModeActive: Boolean,
    onToggleSelectionMode: () -> Unit,
    onToggleTransitionInPool: (TransitionType) -> Unit,
    onPreviewTransition: (TransitionType) -> Unit,
    onClose: () -> Unit
) {
    val allTransitions = TransitionType.values()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .background(Color(0xFF161824))
            .padding(16.dp)
    ) {
        // Header with Back Chevron
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF282C40))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar para Timeline",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "Sistema de Transições Suaves",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Clique em uma transição para pré-visualizar ou ative o modo seleção",
                        fontSize = 11.sp,
                        color = Color(0xFFA0A5C8)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mode Toggle Button: "Selecionar Transições"
        Button(
            onClick = onToggleSelectionMode,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSelectionModeActive) Color(0xFF00E676) else Color(0xFF6200EE)
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isSelectionModeActive) Icons.Default.CheckCircle else Icons.Default.Transform,
                    contentDescription = null,
                    tint = if (isSelectionModeActive) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSelectionModeActive)
                        "Modo Seleção Ativo: Clique nas transições para marcar (${selectedTransitionsPool.size}/20)"
                    else
                        "Ativar Seleção de Transições (${selectedTransitionsPool.size} Ativas)",
                    color = if (isSelectionModeActive) Color.Black else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 20 Transitions Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(allTransitions) { transition ->
                val isChecked = selectedTransitionsPool.contains(transition)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSelectionModeActive) {
                                onToggleTransitionInPool(transition)
                            } else {
                                onPreviewTransition(transition)
                            }
                        },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isChecked) Color(0xFF252A42) else Color(0xFF1E2130)
                    ),
                    border = if (isChecked) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E676)) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = transition.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = transition.category,
                                fontSize = 10.sp,
                                color = Color(0xFF8E95C0)
                            )
                        }

                        if (isChecked) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Marcada",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.RadioButtonUnchecked,
                                contentDescription = "Desmarcada",
                                tint = Color(0xFF5E6488),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF323652))
        ) {
            Text("Concluído", color = Color.White)
        }
    }
}
