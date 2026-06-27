package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.ScriptEntity
import com.example.service.AutoClickerService
import com.example.OverlayService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val scripts by viewModel.allScripts.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val context = LocalContext.current
    
    var showPremiumDialog by remember { mutableStateOf(false) }
    
    if (showPremiumDialog) {
        AlertDialog(
            onDismissRequest = { showPremiumDialog = false },
            title = { Text("Upgrade to Premium 💎") },
            text = { Text("Unlock unlimited scripts, the powerful Macro Recorder, and remove all ads for $4.99!") },
            confirmButton = {
                Button(onClick = {
                    viewModel.upgradeToPremium()
                    showPremiumDialog = false
                    Toast.makeText(context, "Welcome to Premium!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Upgrade Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPremiumDialog = false }) { Text("Cancel") }
            }
        )
    }
    
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportPendingScript(it, context)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importScript(it, context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto Clicker") },
                actions = {
                    IconButton(onClick = { importLauncher.launch("application/json") }) {
                        Icon(Icons.Default.FileCopy, contentDescription = "Import Script")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (!isPremium && scripts.size >= 3) {
                    showPremiumDialog = true
                    return@FloatingActionButton
                }
                OverlayService.startManualBuilder(context, isPremium)
            }) {
                Icon(Icons.Default.Add, contentDescription = "New Script")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Quick Tools
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Quick Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { OverlayService.startSinglePointMode(context, isPremium) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Single-Point Click")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { OverlayService.startMultiPointMode(context, isPremium) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Multi-Point Click")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { OverlayService.startSwipeMode(context, isPremium) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Auto Swipe")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { 
                            if (!isPremium) {
                                showPremiumDialog = true
                            } else {
                                OverlayService.startRecorder(context, isPremium)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text(if (isPremium) "Macro Recorder" else "Macro Recorder (Premium 💎)")
                    }
                }
            }

            Text(
                text = "Saved Scripts",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (scripts.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No scripts saved. Create one!")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scripts) { script ->
                        ScriptItem(
                            script = script,
                            onPlay = { OverlayService.startScriptPlayback(context, script, isPremium) },
                            onDelete = { viewModel.delete(script) },
                            onRename = { newName -> viewModel.update(script.copy(name = newName)) },
                            onDuplicate = { 
                                if (!isPremium && scripts.size >= 3) {
                                    showPremiumDialog = true
                                } else {
                                    viewModel.insert(script.copy(id = java.util.UUID.randomUUID().toString(), name = script.name + " (Copy)")) 
                                }
                            },
                            onExport = { 
                                viewModel.setPendingExport(script)
                                exportLauncher.launch("${script.name}.json") 
                            }
                        )
                    }
                }
            }
            
            if (!isPremium) {
                BannerAdPlaceholder()
            }
        }
    }
}

@Composable
fun ScriptItem(
    script: ScriptEntity, 
    onPlay: () -> Unit, 
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(script.name) }
    
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Script") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) onRename(renameText)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(script.name, fontWeight = FontWeight.Bold)
            }
            Row {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                expanded = false
                                renameText = script.name
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Default.FileCopy, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onExport()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                expanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
