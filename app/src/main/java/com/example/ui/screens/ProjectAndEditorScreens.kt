package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ProjectEntity
import com.example.domain.model.FileDiffPreview
import com.example.execution.TaskExecutionResult
import com.example.ui.OpenEditorTab
import com.example.ui.PiashAgentViewModel
import com.example.ui.WorkspaceFileItem
import com.example.ui.components.UnifiedDiffCard
import com.example.ui.components.highlightCodeSyntax
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElevatedSurfaceDark
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.VioletAccent

@Composable
fun ProjectWorkspaceScreen(
    viewModel: PiashAgentViewModel,
    projects: List<ProjectEntity>,
    activeProject: ProjectEntity?,
    workspaceFiles: List<WorkspaceFileItem>,
    gitStatusReport: String,
    gitDiffReport: String,
    gitLogReport: String,
    taskConsoleHistory: List<TaskExecutionResult>,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0=Files, 1=Git VCS, 2=Task Console, 3=Instructions
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<String?>(null) }

    var commitMsgInput by remember { mutableStateOf("") }
    var branchInput by remember { mutableStateOf("") }
    var consoleCommandInput by remember { mutableStateOf("verify") }
    var instructionsText by remember(activeProject?.id) {
        mutableStateOf(activeProject?.instructions.orEmpty())
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Project Header & Switcher
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeProject?.name ?: "Project Workspace",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Branch: ${activeProject?.gitBranch ?: "main"} • ${workspaceFiles.count { !it.isDirectory }} files",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { showCreateFileDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("new_file_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New File", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = { showCreateProjectDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("new_project_button")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Project", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Project Switcher Chips
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    projects.forEach { proj ->
                        FilterChip(
                            selected = proj.id == activeProject?.id,
                            onClick = { viewModel.selectProject(proj) },
                            label = { Text(proj.name) }
                        )
                    }
                }
            }
        }

        // Sub-navigation Tabs (Files / Git VCS / Task Console / Instructions)
        ScrollableTabRow(
            selectedTabIndex = selectedSection,
            edgePadding = 12.dp,
            containerColor = Color(0xFF08080C)
        ) {
            listOf("Files (${workspaceFiles.size})", "Git VCS", "Task Console", "Instructions").forEachIndexed { idx, title ->
                Tab(
                    selected = selectedSection == idx,
                    onClick = { selectedSection = idx },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                )
            }
        }

        when (selectedSection) {
            0 -> {
                // File Explorer Tree
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(workspaceFiles, key = { it.relativePath }) { item ->
                        Surface(
                            color = if (item.isDirectory) Color(0xFF0D0D12) else SurfaceColorCard,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !item.isDirectory) {
                                    viewModel.openFileInEditor(item.relativePath)
                                    viewModel.selectTab(2)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (item.isDirectory) VioletAccent else CyanAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = item.relativePath,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color.White
                                        )
                                        if (!item.isDirectory) {
                                            Text(
                                                text = "${item.sizeBytes} bytes • Tap to open in Code Editor",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                if (!item.isDirectory) {
                                    IconButton(
                                        onClick = { fileToDelete = item.relativePath },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete file",
                                            tint = ErrorRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Native Git VCS Panel
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Branch Switcher & Commit Box
                    Surface(
                        color = ElevatedSurfaceDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Native Git Version Control", style = MaterialTheme.typography.titleMedium)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = branchInput,
                                    onValueChange = { branchInput = it },
                                    placeholder = { Text("New or existing branch name…") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        viewModel.switchOrCreateGitBranch(branchInput)
                                        branchInput = ""
                                    }
                                ) {
                                    Text("Checkout")
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = commitMsgInput,
                                    onValueChange = { commitMsgInput = it },
                                    placeholder = { Text("Commit message (e.g. feat: add module)…") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        viewModel.commitProjectChanges(commitMsgInput)
                                        commitMsgInput = ""
                                    },
                                    modifier = Modifier.testTag("git_commit_button")
                                ) {
                                    Text("Commit")
                                }
                            }
                        }
                    }

                    GitReportCard("Working Tree Status (git status)", gitStatusReport)
                    GitReportCard("Uncommitted Diff (git diff)", gitDiffReport)
                    GitReportCard("Commit History (git log)", gitLogReport)
                }
            }

            2 -> {
                // Native Isolated Task Console
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = Color(0xFF0D0D14),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Native Android Sandbox Task Runner (Zero Termux / Zero Root)",
                                style = MaterialTheme.typography.titleSmall,
                                color = CyanAccent
                            )
                            Text(
                                text = "Quick tasks: verify • analyze • test • status • diff • log • ls • env • help",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("verify", "analyze", "test", "status", "diff", "env", "help").forEach { preset ->
                                    Surface(
                                        color = ElevatedSurfaceDark,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.clickable {
                                            viewModel.runTaskConsoleCommand(preset)
                                        }
                                    ) {
                                        Text(
                                            text = preset,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = consoleCommandInput,
                            onValueChange = { consoleCommandInput = it },
                            placeholder = { Text("Enter supported task command…") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("task_console_input")
                        )
                        Button(
                            onClick = { viewModel.runTaskConsoleCommand(consoleCommandInput) },
                            modifier = Modifier.testTag("run_task_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Run")
                        }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(taskConsoleHistory.reversed()) { res ->
                            Surface(
                                color = Color.Black,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (res.exitCode == 0) EmeraldAccent.copy(alpha = 0.4f) else ErrorRed.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "$ ${res.command}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = CyanAccent
                                        )
                                        Text(
                                            text = "exit=${res.exitCode} (${res.durationMs}ms)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (res.exitCode == 0) EmeraldAccent else ErrorRed
                                        )
                                    }
                                    if (res.stdout.isNotBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = res.stdout,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    }
                                    if (res.stderr.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = res.stderr,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 11.sp,
                                            color = ErrorRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // Project Instructions Editor
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Project-Specific Agent Instructions (.piashrules)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Automatically injected into every AI agent prompt when working inside '${activeProject?.name}'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = instructionsText,
                        onValueChange = { instructionsText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    Button(
                        onClick = { viewModel.updateProjectInstructions(instructionsText) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Project Instructions")
                    }
                }
            }
        }
    }

    // Create Project Modal
    if (showCreateProjectDialog) {
        var newName by remember { mutableStateOf("") }
        var newDesc by remember { mutableStateOf("") }
        var newTemplate by remember { mutableStateOf("kotlin") }

        AlertDialog(
            onDismissRequest = { showCreateProjectDialog = false },
            title = { Text("Create New Project Workspace") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Project Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Starter Template:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("kotlin", "python", "react").forEach { tpl ->
                            FilterChip(
                                selected = newTemplate == tpl,
                                onClick = { newTemplate = tpl },
                                label = { Text(tpl.uppercase()) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.createNewProject(newName, newDesc, "Follow Clean Architecture.", newTemplate)
                            showCreateProjectDialog = false
                        }
                    }
                ) {
                    Text("Create Project")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateProjectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create File Modal
    if (showCreateFileDialog) {
        var filePath by remember { mutableStateOf("src/main/kotlin/NewModule.kt") }
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create New Workspace File") },
            text = {
                OutlinedTextField(
                    value = filePath,
                    onValueChange = { filePath = it },
                    label = { Text("Relative File Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createNewWorkspaceFile(filePath, "package workspace\n\n// Created in PiashAgent\n")
                        showCreateFileDialog = false
                    }
                ) {
                    Text("Create & Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirm File Delete Modal
    fileToDelete?.let { path ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete File?") },
            text = { Text("Permanently delete '$path' from the project workspace?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWorkspaceFile(path)
                        fileToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private val SurfaceColorCard = Color(0xFF111116)

@Composable
private fun GitReportCard(title: String, content: String) {
    Surface(
        color = Color(0xFF0A0A0E),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = CyanAccent)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = content.ifBlank { "(No output)" },
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                color = Color(0xFFD4D4D8)
            )
        }
    }
}

/**
 * Integrated Multi-Tab Code Editor with Syntax Highlighting, Line Numbers,
 * Search & Replace, Undo/Redo, and Unified Diff Preview before saving.
 */
@Composable
fun CodeEditorScreen(
    viewModel: PiashAgentViewModel,
    openTabs: List<OpenEditorTab>,
    selectedTabIndex: Int,
    diffPreview: FileDiffPreview?,
    modifier: Modifier = Modifier
) {
    var showFindReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    val activeTab = openTabs.getOrNull(selectedTabIndex)

    Column(modifier = modifier.fillMaxSize()) {
        // Editor Action Toolbar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = activeTab?.relativePath ?: "No File Open",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    if (activeTab?.isDirty == true) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = VioletAccent.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "UNSAVED",
                                style = MaterialTheme.typography.labelSmall,
                                color = VioletAccent,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { viewModel.undoEditorChange() },
                        enabled = activeTab?.undoStack?.isNotEmpty() == true,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = { viewModel.redoEditorChange() },
                        enabled = activeTab?.redoStack?.isNotEmpty() == true,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = { showFindReplace = !showFindReplace },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.FindReplace, contentDescription = "Search and replace", modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = { viewModel.previewActiveEditorDiff() },
                        enabled = activeTab != null,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("editor_diff_preview_button")
                    ) {
                        Icon(Icons.Default.CompareArrows, contentDescription = "Preview Diff", tint = CyanAccent, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { viewModel.saveActiveEditorTab() },
                        enabled = activeTab != null,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("editor_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save File",
                            tint = if (activeTab?.isDirty == true) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Open File Tabs Strip
        if (openTabs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF09090D))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                openTabs.forEachIndexed { idx, tab ->
                    val selected = idx == selectedTabIndex
                    Surface(
                        color = if (selected) ElevatedSurfaceDark else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) CyanAccent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.clickable { viewModel.selectEditorTab(idx) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = (if (tab.isDirty) "● " else "") + tab.relativePath.substringAfterLast('/'),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close tab",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { viewModel.closeEditorTab(idx) }
                            )
                        }
                    }
                }
            }
        }

        // Search & Replace Bar
        AnimatedVisibility(visible = showFindReplace) {
            Surface(
                color = ElevatedSurfaceDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = { findQuery = it },
                        placeholder = { Text("Find…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = replaceQuery,
                        onValueChange = { replaceQuery = it },
                        placeholder = { Text("Replace…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { viewModel.searchAndReplaceInEditor(findQuery, replaceQuery) }) {
                        Text("Replace All")
                    }
                }
            }
        }

        // Diff Preview Modal Banner if requested
        if (diffPreview != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117))
                    .padding(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unified Diff Preview Before Save", style = MaterialTheme.typography.titleSmall, color = CyanAccent)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.dismissEditorDiffPreview() }) {
                            Text("Close Preview")
                        }
                        Button(onClick = { viewModel.saveActiveEditorTab() }) {
                            Text("Apply & Save")
                        }
                    }
                }
                UnifiedDiffCard(diff = diffPreview)
            }
        }

        // Main Code Editor Surface with Line Numbers
        if (activeTab == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No file open in editor. Select a file from the Project tab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val vScroll = rememberScrollState()
            val lines = remember(activeTab.currentContent) { activeTab.currentContent.lines() }
            val highlighted = remember(activeTab.currentContent) {
                highlightCodeSyntax(activeTab.currentContent)
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050508))
                    .verticalScroll(vScroll)
                    .padding(vertical = 10.dp)
            ) {
                // Gutter Line Numbers
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .width(44.dp)
                        .padding(end = 8.dp)
                ) {
                    for (i in 1..lines.size.coerceAtLeast(1)) {
                        Text(
                            text = i.toString(),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFF52525B)
                        )
                    }
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background( Color(0xFF1F1F26))
                )

                // Editable Code Canvas with live syntax overlay
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = highlighted,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 19.sp
                    )
                    BasicTextField(
                        value = activeTab.currentContent,
                        onValueChange = { viewModel.updateActiveEditorContent(it) },
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            color = Color.Transparent
                        ),
                        cursorBrush = SolidColor(CyanAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("code_editor_text_field")
                    )
                }
            }
        }
    }
}
