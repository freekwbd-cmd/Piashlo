package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.configureAppCheck
import com.example.ui.PiashAgentViewModel
import com.example.ui.components.PermissionApprovalDialog
import com.example.ui.screens.ChatWorkspaceScreen
import com.example.ui.screens.CodeEditorScreen
import com.example.ui.screens.MultiAgentAndToolsScreen
import com.example.ui.screens.ProjectWorkspaceScreen
import com.example.ui.screens.SettingsAndSecurityScreen
import com.example.ui.screens.SkillsAndMcpScreen
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PiashAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or ViewModel creation
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val agentViewModel: PiashAgentViewModel = viewModel()
            val settings by agentViewModel.settingsState.collectAsStateWithLifecycle()

            PiashAgentTheme(
                darkTheme = settings.darkTheme,
                amoledMode = settings.amoledMode,
                accentPreset = settings.accentPreset
            ) {
                PiashAgentAppShell(viewModel = agentViewModel)
            }
        }
    }
}

private data class WorkspaceNavItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val NAV_ITEMS = listOf(
    WorkspaceNavItem(0, "Chat", Icons.Default.Chat, "nav_tab_chat"),
    WorkspaceNavItem(1, "Project", Icons.Default.Folder, "nav_tab_project"),
    WorkspaceNavItem(2, "Editor", Icons.Default.Code, "nav_tab_editor"),
    WorkspaceNavItem(3, "Agents", Icons.Default.SmartToy, "nav_tab_agents"),
    WorkspaceNavItem(4, "Skills", Icons.Default.Extension, "nav_tab_skills"),
    WorkspaceNavItem(5, "Settings", Icons.Default.Settings, "nav_tab_settings")
)

@Composable
fun PiashAgentAppShell(
    viewModel: PiashAgentViewModel
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val conversations by viewModel.allConversations.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.currentMessages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val agentStatusText by viewModel.agentStatusText.collectAsStateWithLifecycle()
    val streamingContent by viewModel.streamingContent.collectAsStateWithLifecycle()
    val streamingThinking by viewModel.streamingThinking.collectAsStateWithLifecycle()
    val streamingTools by viewModel.streamingTools.collectAsStateWithLifecycle()
    val streamingDiffs by viewModel.streamingDiffs.collectAsStateWithLifecycle()
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val workspaceFiles by viewModel.workspaceFiles.collectAsStateWithLifecycle()
    val gitStatusReport by viewModel.gitStatusReport.collectAsStateWithLifecycle()
    val gitDiffReport by viewModel.gitDiffReport.collectAsStateWithLifecycle()
    val gitLogReport by viewModel.gitLogReport.collectAsStateWithLifecycle()
    val taskConsoleHistory by viewModel.taskConsoleHistory.collectAsStateWithLifecycle()
    val openTabs by viewModel.openTabs.collectAsStateWithLifecycle()
    val selectedEditorTabIndex by viewModel.selectedEditorTabIndex.collectAsStateWithLifecycle()
    val editorDiffPreview by viewModel.editorDiffPreview.collectAsStateWithLifecycle()
    val agentStatuses by viewModel.agentStatuses.collectAsStateWithLifecycle()
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val mcpServers by viewModel.mcpServers.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val pendingPermission by viewModel.pendingPermission.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    // BackHandler to return to Home/Chat screen when on secondary tabs
    BackHandler(enabled = selectedTab != 0) {
        viewModel.selectTab(0)
    }

    // Permission Approval Dialog
    pendingPermission?.let { req ->
        PermissionApprovalDialog(
            request = req,
            onApprove = { viewModel.resolvePermission(true) },
            onDeny = { viewModel.resolvePermission(false) }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = Color(0xFF08080C),
                        tonalElevation = 0.dp
                    ) {
                        NAV_ITEMS.forEach { item ->
                            NavigationBarItem(
                                selected = selectedTab == item.index,
                                onClick = { viewModel.selectTab(item.index) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = Color(0xFF08080C),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        NAV_ITEMS.forEach { item ->
                            NavigationRailItem(
                                selected = selectedTab == item.index,
                                onClick = { viewModel.selectTab(item.index) },
                                icon = {
                                    Icon(imageVector = item.icon, contentDescription = item.label)
                                },
                                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Dismissible Status / Error Notification Banner
                    AnimatedVisibility(visible = errorBanner != null || statusBanner != null) {
                        val isError = errorBanner != null
                        val text = errorBanner ?: statusBanner.orEmpty()
                        Surface(
                            color = if (isError) ErrorRed.copy(alpha = 0.16f) else EmeraldAccent.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, if (isError) ErrorRed else EmeraldAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isError) ErrorRed else EmeraldAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.dismissBanners() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss notification",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            0 -> ChatWorkspaceScreen(
                                viewModel = viewModel,
                                activeProject = activeProject,
                                conversations = conversations,
                                activeConversationId = activeConversationId,
                                messages = messages,
                                selectedModelId = settings.selectedModelId,
                                isGenerating = isGenerating,
                                agentStatusText = agentStatusText,
                                streamingContent = streamingContent,
                                streamingThinking = streamingThinking,
                                streamingTools = streamingTools,
                                streamingDiffs = streamingDiffs,
                                pendingAttachments = pendingAttachments,
                                searchQuery = searchQuery
                            )
                            1 -> ProjectWorkspaceScreen(
                                viewModel = viewModel,
                                projects = projects,
                                activeProject = activeProject,
                                workspaceFiles = workspaceFiles,
                                gitStatusReport = gitStatusReport,
                                gitDiffReport = gitDiffReport,
                                gitLogReport = gitLogReport,
                                taskConsoleHistory = taskConsoleHistory
                            )
                            2 -> CodeEditorScreen(
                                viewModel = viewModel,
                                openTabs = openTabs,
                                selectedTabIndex = selectedEditorTabIndex,
                                diffPreview = editorDiffPreview
                            )
                            3 -> MultiAgentAndToolsScreen(
                                viewModel = viewModel,
                                agentStatuses = agentStatuses,
                                roleModels = settings.agentModels
                            )
                            4 -> SkillsAndMcpScreen(
                                viewModel = viewModel,
                                skills = skills,
                                mcpServers = mcpServers
                            )
                            5 -> SettingsAndSecurityScreen(
                                viewModel = viewModel,
                                settings = settings,
                                memories = memories,
                                auditLogs = auditLogs,
                                conversations = conversations
                            )
                        }
                    }
                }
            }
        }
    }
}
