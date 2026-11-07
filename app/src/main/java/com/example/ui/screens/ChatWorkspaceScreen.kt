package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.agent.AgentOrchestrator
import com.example.agent.SlashCommandProcessor
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.ProjectEntity
import com.example.domain.model.ModelCatalog
import com.example.ui.PiashAgentViewModel
import com.example.ui.components.RichMarkdownContent
import com.example.ui.components.ToolExecutionCard
import com.example.ui.components.UnifiedDiffCard
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElevatedSurfaceDark
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.VioletAccent

@Composable
fun ChatWorkspaceScreen(
    viewModel: PiashAgentViewModel,
    activeProject: ProjectEntity?,
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    messages: List<MessageEntity>,
    selectedModelId: String,
    isGenerating: Boolean,
    agentStatusText: String,
    streamingContent: String,
    streamingThinking: String,
    streamingTools: List<com.example.domain.model.ToolExecutionRecord>,
    streamingDiffs: List<com.example.domain.model.FileDiffPreview>,
    pendingAttachments: List<String>,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    var promptInput by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }
    var showSessionsDrawer by remember { mutableStateOf(false) }
    var messageToEdit by remember { mutableStateOf<MessageEntity?>(null) }
    var editedMessageText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val currentModel = remember(selectedModelId) { ModelCatalog.findById(selectedModelId) }
    val activeConv = remember(conversations, activeConversationId) {
        conversations.firstOrNull { it.id == activeConversationId }
    }

    // SAF File Picker Launcher
    val safPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.attachFileFromSafUri(uri)
        }
    }

    // Native Android Voice Input Launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                promptInput = if (promptInput.isBlank()) spoken else "$promptInput $spoken"
            }
        }
    }

    // Auto-scroll when new messages or streaming chunks arrive
    LaunchedEffect(messages.size, streamingContent.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.content.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 1. Top Workspace Control Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Sessions History Button & Active Project Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { showSessionsDrawer = !showSessionsDrawer },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("toggle_sessions_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Recent Conversations",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(
                                text = activeConv?.title ?: "PiashAgent Workspace",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isGenerating) CyanAccent else EmeraldAccent)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "${activeProject?.name ?: "Project"} (${activeProject?.gitBranch ?: "main"}) • $agentStatusText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Model Selector Pill + New Chat + Export
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box {
                            Surface(
                                color = ElevatedSurfaceDark,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .clickable { showModelMenu = true }
                                    .testTag("model_selector_pill")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = currentModel.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false }
                            ) {
                                ModelCatalog.models.forEach { spec ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = "${spec.displayName} (${spec.provider.badge})",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = spec.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectModel(spec.id)
                                            showModelMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { viewModel.createNewConversation() },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("new_conversation_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Conversation",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { viewModel.exportCurrentConversationToClipboard() },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("export_conversation_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export Session",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Expandable Recent Sessions & Conversation Search Drawer
                AnimatedVisibility(visible = showSessionsDrawer) {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0A0A0F))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            placeholder = { Text("Search messages or conversations…", style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("conversation_search_input")
                        )

                        Text(
                            text = "Recent Conversations (${conversations.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        conversations.take(6).forEach { conv ->
                            val isSelected = conv.id == activeConversationId
                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectConversation(conv.id)
                                        showSessionsDrawer = false
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = conv.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${conv.modelId} • ${conv.totalTokens} tok • $${"%.4f".format(conv.estimatedCostUsd)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (conversations.size > 1) {
                                        IconButton(
                                            onClick = { viewModel.deleteConversation(conv.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete conversation",
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
            }
        }

        if (isGenerating) {
            LinearProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. Main Conversation Stream
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Hero Workspace Card & Quick Action Starters
            item {
                WorkspaceHeroAndQuickActions(
                    projectName = activeProject?.name ?: "PiashCore Engine",
                    onQuickPrompt = { prompt -> viewModel.sendPrompt(prompt) }
                )
            }

            items(filteredMessages, key = { it.id }) { message ->
                ChatMessageCard(
                    message = message,
                    onCopyText = { viewModel.copyToClipboard("Message", it) },
                    onBranchHere = { viewModel.branchConversationFromMessage(message) },
                    onEditMessage = {
                        messageToEdit = message
                        editedMessageText = message.content
                    },
                    onRegenerate = { viewModel.regenerateLastResponse() },
                    onOpenDiffInEditor = { path ->
                        viewModel.openFileInEditor(path)
                        viewModel.selectTab(2)
                    },
                    onSaveSnippetToFile = { path, code ->
                        viewModel.createNewWorkspaceFile(path, code)
                    }
                )
            }

            // Live Streaming Agent Turn Card
            if (isGenerating) {
                item {
                    LiveStreamingTurnCard(
                        statusText = agentStatusText,
                        thinkingLog = streamingThinking,
                        streamingContent = streamingContent,
                        executedTools = streamingTools,
                        fileDiffs = streamingDiffs,
                        onCopyCode = { viewModel.copyToClipboard("Code Snippet", it) }
                    )
                }
            }
        }

        // 3. Slash Command Suggestions Bar when typing '/'
        AnimatedVisibility(visible = promptInput.startsWith("/")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D14))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SlashCommandProcessor.SUPPORTED_COMMANDS
                    .filter { it.first.startsWith(promptInput.substringBefore(" ").lowercase()) }
                    .forEach { (cmd, desc) ->
                        Surface(
                            color = ElevatedSurfaceDark,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.clickable {
                                promptInput = "$cmd "
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cmd,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyanAccent
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = desc.take(28),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
            }
        }

        // 4. Pending SAF Attachments Bar
        AnimatedVisibility(visible = pendingAttachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElevatedSurfaceDark)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pendingAttachments.forEach { rel ->
                    Surface(
                        color = CyanAccent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = rel,
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanAccent
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove attachment",
                                tint = CyanAccent,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { viewModel.removePendingAttachment(rel) }
                            )
                        }
                    }
                }
            }
        }

        // 5. Prompt Input Composer Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(
                    onClick = { safPickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("attach_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach file via SAF",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        runCatching {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak prompt to PiashAgent…")
                            }
                            voiceLauncher.launch(intent)
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("voice_input_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = {
                        Text(
                            text = "Ask PiashAgent or type /help…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = Color(0xFF07070A),
                        unfocusedContainerColor = Color(0xFF07070A)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .testTag("prompt_input_field")
                )

                if (isGenerating) {
                    IconButton(
                        onClick = { viewModel.cancelGeneration() },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("stop_generation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "Stop Generation",
                            tint = ErrorRed,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (promptInput.isNotBlank()) {
                                val toSend = promptInput
                                promptInput = ""
                                viewModel.sendPrompt(toSend)
                            }
                        },
                        enabled = promptInput.isNotBlank(),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("send_prompt_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Prompt",
                            tint = if (promptInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Edit & Resend Message Modal
    messageToEdit?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToEdit = null },
            title = { Text("Edit & Resend Prompt") },
            text = {
                OutlinedTextField(
                    value = editedMessageText,
                    onValueChange = { editedMessageText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = editedMessageText
                        messageToEdit = null
                        viewModel.editAndResendMessage(msg, updated)
                    }
                ) {
                    Text("Resend")
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun WorkspaceHeroAndQuickActions(
    projectName: String,
    onQuickPrompt: (String) -> Unit
) {
    Surface(
        color = Color(0xFF0B0B10),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_workspace_hero_1794043760854),
                    contentDescription = "PiashAgent AI Workspace",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xEE000000))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "PIASHAGENT • NATIVE AI WORKSPACE",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanAccent
                    )
                    Text(
                        text = "Your Personal AI Development Agent • $projectName",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickCards = listOf(
                    "Inspect Workspace" to "Use project_inspect and code_analysis to summarize the current project files and architecture.",
                    "Multi-Agent Plan" to "/plan Add a caching repository and unit tests to the project",
                    "Code Review & Diff" to "/review",
                    "Slash Commands" to "/help"
                )
                quickCards.forEach { (title, prompt) ->
                    Surface(
                        color = ElevatedSurfaceDark,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .clickable { onQuickPrompt(prompt) }
                            .testTag("quick_action_${title.lowercase().replace(" ", "_")}")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = prompt.take(34) + if (prompt.length > 34) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageCard(
    message: MessageEntity,
    onCopyText: (String) -> Unit,
    onBranchHere: () -> Unit,
    onEditMessage: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenDiffInEditor: (String) -> Unit,
    onSaveSnippetToFile: (String, String) -> Unit
) {
    val isUser = message.role == "USER"
    val toolRecords = remember(message.toolCallsJson) {
        AgentOrchestrator.deserializeToolRecords(message.toolCallsJson)
    }
    val fileDiffs = remember(message.fileDiffsJson) {
        AgentOrchestrator.deserializeFileDiffs(message.fileDiffsJson)
    }
    var showThinking by remember { mutableStateOf(false) }

    Surface(
        color = if (isUser) Color(0xFF13131C) else Color(0xFF0A0A0E),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isUser) VioletAccent.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Message Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (isUser) VioletAccent.copy(alpha = 0.2f) else CyanAccent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isUser) "YOU" else "PIASHAGENT",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isUser) VioletAccent else CyanAccent,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = message.modelUsed,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Message Actions (Copy, Branch, Edit / Regenerate)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { onCopyText(message.content) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy response",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = onBranchHere,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallSplit,
                            contentDescription = "Branch conversation",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (isUser) {
                        IconButton(
                            onClick = onEditMessage,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit and resend",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate response",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Expandable Thinking Log if present
            if (message.thinkingSummary.isNotBlank()) {
                Surface(
                    color = Color(0xFF101018),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThinking = !showThinking }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = VioletAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Agent Reasoning & Execution Trace",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VioletAccent
                                )
                            }
                            Icon(
                                imageVector = if (showThinking) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = VioletAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        AnimatedVisibility(visible = showThinking) {
                            Text(
                                text = message.thinkingSummary,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }

            // Executed Tool Cards
            if (toolRecords.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    toolRecords.forEach { rec ->
                        ToolExecutionCard(record = rec)
                    }
                }
            }

            // Unified File Diff Previews
            if (fileDiffs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    fileDiffs.forEach { diff ->
                        UnifiedDiffCard(
                            diff = diff,
                            onOpenInEditor = onOpenDiffInEditor
                        )
                    }
                }
            }

            // Main Markdown Content
            RichMarkdownContent(
                markdown = message.content,
                onCopyCode = onCopyText,
                onSaveSnippetToFile = onSaveSnippetToFile
            )
        }
    }
}

@Composable
private fun LiveStreamingTurnCard(
    statusText: String,
    thinkingLog: String,
    streamingContent: String,
    executedTools: List<com.example.domain.model.ToolExecutionRecord>,
    fileDiffs: List<com.example.domain.model.FileDiffPreview>,
    onCopyCode: (String) -> Unit
) {
    Surface(
        color = Color(0xFF0B0F17),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanAccent
                )
            }

            if (thinkingLog.isNotBlank()) {
                Text(
                    text = thinkingLog,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            executedTools.forEach { rec ->
                ToolExecutionCard(record = rec)
            }

            fileDiffs.forEach { diff ->
                UnifiedDiffCard(diff = diff)
            }

            if (streamingContent.isNotBlank()) {
                RichMarkdownContent(
                    markdown = streamingContent,
                    onCopyCode = onCopyCode
                )
            }
        }
    }
}
