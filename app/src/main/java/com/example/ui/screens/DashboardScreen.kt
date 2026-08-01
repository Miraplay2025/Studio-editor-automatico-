package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.db.ProjectWithMedia
import com.example.ui.theme.*
import com.example.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenProject: (String) -> Unit
) {
    val projects by viewModel.projectsState.collectAsStateWithLifecycle()
    var projectToDeleteId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // SAF Multiple Document Picker
    val multipleFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.createNewProjectFromUris(uris, onProjectCreated = onOpenProject)
        }
    }

    // SAF Folder Tree Picker
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            // Import sample files or folder contents
            val sampleUris = listOf(treeUri)
            viewModel.createNewProjectFromUris(sampleUris, onProjectCreated = onOpenProject)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = PrimaryPurple,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = "Logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "CineCut",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "Editor de Vídeo Offline",
                                style = MaterialTheme.typography.labelSmall.copy(color = SecondaryCyan, fontSize = 10.sp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { multipleFilesLauncher.launch(arrayOf("image/*", "video/*")) },
                containerColor = PrimaryPurple,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = "Criar") },
                text = { Text("Criar Novo Projeto", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("create_new_project_fab")
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Action Row: Select Files or Select Folder
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { multipleFilesLauncher.launch(arrayOf("image/*", "video/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("select_files_button")
                ) {
                    Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = "Mídias", tint = SecondaryCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Selecionar Mídias", fontSize = 12.sp)
                }

                Button(
                    onClick = { folderLauncher.launch(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("select_folder_button")
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = "Pasta", tint = PrimaryPurple)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Selecionar Pasta", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Meus Projetos",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (projects.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Vazio",
                            tint = TextSecondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Ainda não existe nenhum projeto",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Clique em 'Criar Novo Projeto' ou escolha arquivos/pastas para iniciar.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(projects, key = { it.project.id }) { item ->
                        ProjectCardItem(
                            projectWithMedia = item,
                            onClick = { onOpenProject(item.project.id) },
                            onDelete = { projectToDeleteId = item.project.id }
                        )
                    }
                }
            }
        }
    }

    if (projectToDeleteId != null) {
        AlertDialog(
            onDismissRequest = { projectToDeleteId = null },
            title = { Text("Excluir Projeto") },
            text = { Text("Tem certeza que deseja excluir este projeto permanentemente?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        projectToDeleteId?.let { viewModel.deleteProject(it) }
                        projectToDeleteId = null
                    }
                ) {
                    Text("Excluir", color = WarningRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDeleteId = null }) {
                    Text("Cancelar")
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun ProjectCardItem(
    projectWithMedia: ProjectWithMedia,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val project = projectWithMedia.project
    val formattedDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(project.updatedAt))

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("project_card_${project.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (project.thumbnailUri != null) {
                    AsyncImage(
                        model = project.thumbnailUri,
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = "Thumbnail Placeholder",
                        tint = SecondaryCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${project.mediaCount} mídias • ${String.format("%.1fs", project.totalDurationSeconds)}",
                    style = MaterialTheme.typography.bodySmall.copy(color = SecondaryCyan)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Atualizado em $formattedDate",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontSize = 10.sp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_project_button_${project.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Excluir Projeto",
                    tint = WarningRed
                )
            }
        }
    }
}
