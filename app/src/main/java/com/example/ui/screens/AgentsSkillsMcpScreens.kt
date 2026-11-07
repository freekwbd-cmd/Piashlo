package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.McpServerEntity
import com.example.data.local.SkillEntity
import com.example.domain.model.AgentRole
import com.example.domain.model.AgentTaskStatus
import com.example.domain.model.ModelCatalog
import com.example.domain.model.ToolExecutionRecord
import com.example.ui.PiashAgentViewModel
import com.example.ui.components.ToolExecutionCard
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElevatedSurfaceDark
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.VioletAccent

@Composable
fun MultiAgentAndToolsScreen(
    viewModel: PiashAgentViewModel,
    agentStatuses: Map<AgentRole, AgentTaskStatus>,
    roleModels: Map<AgentRole, String>,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0=Multi-Agent Orchestrator, 1=26 Tool Registry
    var multiAgentGoal by remember {
        mutableStateOf("Analyze project architecture, verify syntax across all files, and propose performance improvements")
    }
    var lastDirectToolResult by remember { mutableStateOf<ToolExecutionRecord?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            edgePadding = 12.dp,
            containerColor = Color(0xFF08080C)
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("Multi-Agent Orchestrator (6 Roles)") }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("Tool Registry (${viewModel.toolRegistry.schemas.size} Tools)") }
            )
        }

        if (selectedSubTab == 0) {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Surface(
                        color = Color(0xFF0C0D14),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SmartToy, contentDescription = null, tint = CyanAccent)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Launch Multi-Agent Pipeline",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "Coordinates Research -> Planner -> Coding -> Review -> Testing agents with concurrent file-lock protection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = multiAgentGoal,
                                onValueChange = { multiAgentGoal = it },
                                label = { Text("Engineering Goal") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("multi_agent_goal_input")
                            )
                            Button(
                                onClick = { viewModel.launchMultiAgentGoal(multiAgentGoal) },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("launch_multi_agent_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Run Coordinated Workflow")
                            }
                        }
                    }
                }

                items(AgentRole.entries) { role ->
                    val status = agentStatuses[role]
                    val assignedModel = roleModels[role] ?: role.defaultModelId
                    var showRoleModelMenu by remember { mutableStateOf(false) }

                    val badgeColor = when (status?.state) {
                        "RUNNING" -> CyanAccent
                        "COMPLETED" -> EmeraldAccent
                        "ERROR" -> ErrorRed
                        else -> VioletAccent
                    }

                    Surface(
                        color = Color(0xFF101016),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = badgeColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = role.badge,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = role.title,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                Box {
                                    OutlinedButton(
                                        onClick = { showRoleModelMenu = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(assignedModel, style = MaterialTheme.typography.labelSmall)
                                    }
                                    DropdownMenu(
                                        expanded = showRoleModelMenu,
                                        onDismissRequest = { showRoleModelMenu = false }
                                    ) {
                                        ModelCatalog.models.forEach { m ->
                                            DropdownMenuItem(
                                                text = { Text("${m.displayName} (${m.id})") },
                                                onClick = {
                                                    viewModel.setAgentRoleModel(role, m.id)
                                                    showRoleModelMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = role.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(badgeColor)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "${status?.state ?: "IDLE"}: ${status?.currentStep ?: "Standby"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor
                                    )
                                }
                                if (!status?.lockedFiles.isNullOrEmpty()) {
                                    Text(
                                        text = "Locked: ${status?.lockedFiles?.joinToString()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberAccent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Tool Registry Interactive Explorer
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                lastDirectToolResult?.let { rec ->
                    item {
                        Text("Last Executed Tool Result:", style = MaterialTheme.typography.labelMedium, color = CyanAccent)
                        Spacer(Modifier.height(4.dp))
                        ToolExecutionCard(record = rec)
                    }
                }

                items(viewModel.toolRegistry.schemas, key = { it.name }) { schema ->
                    Surface(
                        color = Color(0xFF101016),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = schema.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = ElevatedSurfaceDark,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = schema.category.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VioletAccent,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = schema.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Schema: ${schema.parametersDescription}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF71717A)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.executeDirectToolFromUi(
                                        toolName = schema.name,
                                        argsJson = schema.parametersDescription
                                    ) { result ->
                                        lastDirectToolResult = result
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Run", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Skills Manager (20 Verified Built-in Skills + Custom SKILL.md Import/Export) & MCP Protocol Hub.
 */
@Composable
fun SkillsAndMcpScreen(
    viewModel: PiashAgentViewModel,
    skills: List<SkillEntity>,
    mcpServers: List<McpServerEntity>,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0=Skills Manager, 1=MCP Servers
    var selectedSkillForDetail by remember { mutableStateOf<SkillEntity?>(null) }
    var showImportSkillDialog by remember { mutableStateOf(false) }
    var showAddMcpDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            edgePadding = 12.dp,
            containerColor = Color(0xFF08080C)
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("Skills Manager (${skills.count { it.isEnabled }}/${skills.size} Active)") }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("MCP Protocol (${mcpServers.size} Servers)") }
            )
        }

        if (selectedSubTab == 0) {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SKILL.md Packages on Disk",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "All 20 built-in skills and custom packages are verified on app-private storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { showImportSkillDialog = true },
                            modifier = Modifier.testTag("import_skill_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Import SKILL.md")
                        }
                    }
                }

                items(skills, key = { it.id }) { skill ->
                    val verifiedOnDisk = remember(skill.filePath) {
                        viewModel.skillsManager.verifySkillFilePresent(skill)
                    }
                    Surface(
                        color = Color(0xFF101016),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (skill.isEnabled) CyanAccent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSkillForDetail = skill }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Extension,
                                        contentDescription = null,
                                        tint = if (skill.isEnabled) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = skill.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "v${skill.version}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VioletAccent
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = skill.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (verifiedOnDisk) "✓ Verified on disk" else "⚠ Missing file",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (verifiedOnDisk) EmeraldAccent else ErrorRed
                                    )
                                    Text(
                                        text = "Tools: ${skill.requiredToolsCsv.take(40)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF71717A)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!skill.isBuiltIn) {
                                    IconButton(onClick = { viewModel.deleteCustomSkill(skill) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete custom skill", tint = ErrorRed)
                                    }
                                }
                                Switch(
                                    checked = skill.isEnabled,
                                    onCheckedChange = { viewModel.toggleSkill(skill, it) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // MCP Protocol Client Screen
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Model Context Protocol (MCP) Servers",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "JSON-RPC 2.0 tool, resource & prompt discovery with zero-trust approval controls.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { showAddMcpDialog = true },
                            modifier = Modifier.testTag("add_mcp_server_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Server")
                        }
                    }
                }

                items(mcpServers, key = { it.id }) { server ->
                    Surface(
                        color = Color(0xFF101016),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Hub, contentDescription = null, tint = CyanAccent)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(server.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = server.endpointUrl,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyanAccent
                                        )
                                    }
                                }
                                Switch(
                                    checked = server.isEnabled,
                                    onCheckedChange = { viewModel.toggleMcpServerEnabled(server, it) }
                                )
                            }

                            Text(
                                text = "Status: ${server.statusMessage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (server.statusMessage.startsWith("Connected")) EmeraldAccent else AmberAccent
                            )

                            Text(
                                text = "Discovered Tools: ${server.discoveredToolsJson}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Discovered Resources: ${server.discoveredResourcesJson}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Discovered Prompts: ${server.discoveredPromptsJson}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        viewModel.toggleMcpServerTrust(server, !server.isTrusted)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = if (server.isTrusted) EmeraldAccent else AmberAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (server.isTrusted) "Trusted Server" else "Untrusted (Tap to Approve Trust)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (server.isTrusted) EmeraldAccent else AmberAccent
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.testAndDiscoverMcpServer(server) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Discover", style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (!server.id.startsWith("mcp-local")) {
                                        IconButton(
                                            onClick = { viewModel.deleteMcpServer(server.id) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete MCP", tint = ErrorRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Skill Details & Export Modal
    selectedSkillForDetail?.let { skill ->
        AlertDialog(
            onDismissRequest = { selectedSkillForDetail = null },
            title = { Text("${skill.name} (v${skill.version})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Disk Path: ${skill.filePath}", style = MaterialTheme.typography.labelSmall, color = CyanAccent)
                    Text("Permissions: ${skill.permissionsCsv}", style = MaterialTheme.typography.labelSmall)
                    Surface(
                        color = Color.Black,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = skill.rawSkillMd,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.copyToClipboard("SKILL.md (${skill.name})", skill.rawSkillMd)
                        selectedSkillForDetail = null
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export SKILL.md")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSkillForDetail = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Import Custom SKILL.md Modal
    if (showImportSkillDialog) {
        var rawMd by remember {
            mutableStateOf(
                """
                ---
                name: "GraphQL Architect"
                description: "Designs type-safe GraphQL schemas, resolvers, and Apollo Kotlin clients."
                version: "1.0.0"
                required_tools: ["read_file", "create_file", "edit_file"]
                permissions: ["workspace.read", "workspace.write"]
                references: "graphql.org"
                ---
                
                # GraphQL Architect
                Design normalized GraphQL schemas with strict pagination and error unions.
                """.trimIndent()
            )
        }
        AlertDialog(
            onDismissRequest = { showImportSkillDialog = false },
            title = { Text("Import Custom SKILL.md Package") },
            text = {
                OutlinedTextField(
                    value = rawMd,
                    onValueChange = { rawMd = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importSkillMarkdown(rawMd)
                        showImportSkillDialog = false
                    }
                ) {
                    Text("Validate & Install")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportSkillDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add MCP Server Modal
    if (showAddMcpDialog) {
        var name by remember { mutableStateOf("Custom JSON-RPC MCP Server") }
        var url by remember { mutableStateOf("http://127.0.0.1:8080/mcp") }
        AlertDialog(
            onDismissRequest = { showAddMcpDialog = false },
            title = { Text("Add MCP Server Endpoint") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Server Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("JSON-RPC / Loopback Endpoint URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addMcpServer(name, url)
                        showAddMcpDialog = false
                    }
                ) {
                    Text("Add Server")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMcpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
