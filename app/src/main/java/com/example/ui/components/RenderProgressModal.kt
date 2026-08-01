package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.RenderProgressState
import com.example.ui.theme.*

@Composable
fun RenderProgressModal(
    state: RenderProgressState,
    onCancelRequested: () -> Unit,
    onDismissSuccess: () -> Unit
) {
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    val logsListState = rememberLazyListState()

    if (state is RenderProgressState.Processing || state is RenderProgressState.Success || state is RenderProgressState.Error) {
        Dialog(
            onDismissRequest = { /* Block dismissal during encoding */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("render_progress_modal")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    when (state) {
                        is RenderProgressState.Processing -> {
                            Text(
                                text = "Edição Automática em Andamento",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // Circular Progress with percentage text inside
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(110.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = { state.progressPercent / 100f },
                                    modifier = Modifier.fillMaxSize(),
                                    color = SecondaryCyan,
                                    strokeWidth = 8.dp,
                                    trackColor = BorderDark
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${state.progressPercent}%",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                    Text(
                                        text = "concluído",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TextSecondary,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = state.currentStep,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = SecondaryCyan,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Logs Terminal Area
                            Text(
                                text = "Logs de Processamento:",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Surface(
                                color = DarkBackground,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            ) {
                                LazyColumn(
                                    state = logsListState,
                                    contentPadding = PaddingValues(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(state.logs) { log ->
                                        Text(
                                            text = log.text,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF00FFCC),
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            OutlinedButton(
                                onClick = { showCancelConfirmDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningRed),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("cancel_render_button")
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancelar")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancelar Renderização")
                            }
                        }

                        is RenderProgressState.Success -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Sucesso",
                                tint = SecondaryCyan,
                                modifier = Modifier.size(64.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Vídeo Renderizado!",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "O vídeo final foi encodado com sucesso e salvo na galeria pública do dispositivo (MediaStore).",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = onDismissSuccess,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Concluir")
                            }
                        }

                        is RenderProgressState.Error -> {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Erro",
                                tint = WarningRed,
                                modifier = Modifier.size(64.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Erro no Processamento",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = WarningRed
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = onDismissSuccess,
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Fechar")
                            }
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = { Text("Cancelar Edição?") },
            text = { Text("Tem certeza que deseja cancelar a renderização do vídeo em andamento?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirmDialog = false
                        onCancelRequested()
                    }
                ) {
                    Text("Sim, Cancelar", color = WarningRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = false }) {
                    Text("Continuar")
                }
            }
        )
    }
}
