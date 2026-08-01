package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExportFps
import com.example.data.model.ExportOptions
import com.example.data.model.ExportQuality
import com.example.data.model.ExportResolution
import com.example.ui.theme.*

@Composable
fun ExportModal(
    currentOptions: ExportOptions,
    onDismiss: () -> Unit,
    onConfirmExport: (ExportOptions) -> Unit
) {
    var selectedRes by remember { mutableStateOf(currentOptions.resolution) }
    var selectedQuality by remember { mutableStateOf(currentOptions.quality) }
    var selectedFps by remember { mutableStateOf(currentOptions.fps) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = "Exportar", tint = SecondaryCyan)
                Text("Exportar Vídeo (FFmpeg Kit / Codec)")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Resolution Selector
                Column {
                    Text(
                        text = "Resolução:",
                        style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ExportResolution.entries.forEach { res ->
                            val isSel = res == selectedRes
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedRes = res },
                                label = { Text(res.label.split(" ").first(), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryPurple,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Quality Selector
                Column {
                    Text(
                        text = "Qualidade / Bitrate:",
                        style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ExportQuality.entries.forEach { qual ->
                            val isSel = qual == selectedQuality
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedQuality = qual }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = isSel,
                                    onClick = { selectedQuality = qual },
                                    colors = RadioButtonDefaults.colors(selectedColor = SecondaryCyan)
                                )
                                Text(
                                    text = qual.label,
                                    style = MaterialTheme.typography.bodySmall.copy(color = if (isSel) Color.White else TextSecondary)
                                )
                            }
                        }
                    }
                }

                // FPS Selector
                Column {
                    Text(
                        text = "Taxa de Quadros (FPS):",
                        style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ExportFps.entries.forEach { fps ->
                            val isSel = fps == selectedFps
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedFps = fps },
                                label = { Text("${fps.fps} FPS") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SecondaryCyan,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmExport(ExportOptions(selectedRes, selectedQuality, selectedFps))
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                modifier = Modifier.testTag("confirm_export_button")
            ) {
                Text("Iniciar Exportação")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        containerColor = DarkSurface
    )
}
