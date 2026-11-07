package com.example.ui.screens

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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AgentSettingsState
import com.example.data.local.AuditLogEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.MemoryEntryEntity
import com.example.domain.model.PermissionPolicy
import com.example.domain.model.ProviderType
import com.example.ui.PiashAgentViewModel
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.VioletAccent

@Composable
fun SettingsAndSecurityScreen(
    viewModel: PiashAgentViewModel,
    settings: AgentSettingsState,
    memories: List<MemoryEntryEntity>,
    auditLogs: List<AuditLogEntity>,
    conversations: List<ConversationEntity>,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    // 0 = AI Providers, 1 = Security & Permissions, 2 = Memory System, 3 = Theme & Telemetry

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            edgePadding = 12.dp,
            containerColor = Color(0xFF08080C)
        ) {
            listOf("AI Providers", "Permissions & Audit", "3-Level Memory", "Theme & Costs").forEachIndexed { idx, label ->
                Tab(
                    selected = selectedSubTab == idx,
                    onClick = { selectedSubTab = idx },
                    text = { Text(label) }
                )
            }
        }

        when (selectedSubTab) {
            0 -> {
                // Modular AI Providers & Android Keystore Credential Vault
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Surface(
                            color = Color(0xFF0C1218),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Android Keystore Encrypted Credential Vault",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CyanAccent
                                )
                                Text(
                                    text = "API keys are encrypted with AES-256-GCM inside Android Keystore. Secrets are never logged or exported in project backups.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(ProviderType.entries) { provider ->
                        var apiKeyInput by remember { mutableStateOf("") }
                        var endpointInput by remember(provider) {
                            mutableStateOf(viewModel.keyVault.getEndpointUrl(provider))
                        }
                        val hasKey = viewModel.keyVault.hasApiKey(provider)
                        val masked = remember(provider, apiKeyInput) {
                            if (provider == ProviderType.GOOGLE_GEMINI) {
                                "Provisioned via Firebase AI Logic & App Check"
                            } else {
                                viewModel.keyVault.maskKey(viewModel.keyVault.getApiKey(provider))
                            }
                        }

                        Surface(
                            color = Color(0xFF101016),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Key, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = "Key Status: $masked",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (hasKey) EmeraldAccent else AmberAccent
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.testProviderConnection(provider) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("test_provider_${provider.name.lowercase()}")
                                    ) {
                                        Text("Test Connection", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                if (provider != ProviderType.GOOGLE_GEMINI) {
                                    OutlinedTextField(
                                        value = endpointInput,
                                        onValueChange = { endpointInput = it },
                                        label = { Text("Base Endpoint URL") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (provider.requiresApiKey) {
                                        OutlinedTextField(
                                            value = apiKeyInput,
                                            onValueChange = { apiKeyInput = it },
                                            label = { Text("API Key (stored in Android Keystore)") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.saveProviderCredentials(provider, apiKeyInput, endpointInput)
                                            apiKeyInput = ""
                                        },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Save to Keystore")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Per-Tool Permission Controls & Live Security Audit Log
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text("Per-Tool Permission Policies", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Control whether each tool runs automatically, prompts for confirmation, or is blocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(viewModel.toolRegistry.schemas, key = { it.name }) { schema ->
                        val activePolicy = viewModel.toolRegistry.getEffectivePolicy(schema.name, settings.toolPolicies)
                        Surface(
                            color = Color(0xFF101016),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(schema.name, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                    Text(schema.category.label, style = MaterialTheme.typography.labelSmall, color = CyanAccent)
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 6.dp)
                                ) {
                                    PermissionPolicy.entries.forEach { policy ->
                                        FilterChip(
                                            selected = activePolicy == policy,
                                            onClick = { viewModel.setToolPermissionPolicy(schema.name, policy) },
                                            label = { Text(policy.label, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(10.dp))
                        Text("Security & Tool Execution Audit Log (${auditLogs.size})", style = MaterialTheme.typography.titleMedium)
                    }

                    items(auditLogs.take(25), key = { it.id }) { log ->
                        Surface(
                            color = Color(0xFF09090D),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${log.toolName} (${log.agentRole})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = CyanAccent
                                    )
                                    Text(
                                        text = "${log.decision} • ${log.durationMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (log.decision == "FAILED" || log.decision == "USER_DENIED") ErrorRed else EmeraldAccent
                                    )
                                }
                                Text(
                                    text = log.resultSummary,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            2 -> {
                // 3-Level Memory System (Conversation, Project, User Preferences)
                var newLevel by remember { mutableStateOf("PROJECT") }
                var newKey by remember { mutableStateOf("") }
                var newValue by remember { mutableStateOf("") }

                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Surface(
                            color = Color(0xFF101016),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Memory, contentDescription = null, tint = VioletAccent)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Add Memory Entry", style = MaterialTheme.typography.titleMedium)
                                    }
                                    OutlinedButton(onClick = { viewModel.clearAllMemories() }) {
                                        Text("Clear All", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("CONVERSATION", "PROJECT", "USER_PREFERENCE").forEach { lvl ->
                                        FilterChip(
                                            selected = newLevel == lvl,
                                            onClick = { newLevel = lvl },
                                            label = { Text(lvl, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = newKey,
                                    onValueChange = { newKey = it },
                                    label = { Text("Memory Key (e.g. coding.convention)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("memory_key_input")
                                )
                                OutlinedTextField(
                                    value = newValue,
                                    onValueChange = { newValue = it },
                                    label = { Text("Memory Value / Instruction") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("memory_value_input")
                                )
                                Button(
                                    onClick = {
                                        viewModel.saveMemoryEntry(newLevel, newKey, newValue)
                                        newKey = ""
                                        newValue = ""
                                    },
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .testTag("save_memory_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save Memory")
                                }
                            }
                        }
                    }

                    items(memories, key = { it.id }) { mem ->
                        Surface(
                            color = Color(0xFF0C0C12),
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
                                        Surface(
                                            color = VioletAccent.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = mem.level,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = VioletAccent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = mem.memoryKey,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = CyanAccent
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = mem.memoryValue,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteMemoryEntry(mem.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete memory", tint = ErrorRed)
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // Theme Customizer, Agent Limits, Usage Costs & Backup
                val totalTokensAll = conversations.sumOf { it.totalTokens }
                val totalCostAll = conversations.sumOf { it.estimatedCostUsd }

                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Surface(
                            color = Color(0xFF101016),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Palette, contentDescription = null, tint = CyanAccent)
                                    Spacer(Modifier.width(8.dp))
                                    Text("AMOLED Theme & Accent Customization", style = MaterialTheme.typography.titleMedium)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Dark Theme (Disable for Light Mode)", style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = settings.darkTheme,
                                        onCheckedChange = {
                                            viewModel.updateThemeSettings(it, settings.amoledMode, settings.accentPreset)
                                        },
                                        modifier = Modifier.testTag("dark_theme_switch")
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Pure AMOLED Black (#000000)", style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = settings.amoledMode,
                                        onCheckedChange = {
                                            viewModel.updateThemeSettings(settings.darkTheme, it, settings.accentPreset)
                                        }
                                    )
                                }

                                Text("Accent Color Preset:", style = MaterialTheme.typography.labelMedium)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AccentPreset.entries.forEach { preset ->
                                        val isSelected = settings.accentPreset == preset
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(preset.primary)
                                                .clickable {
                                                    viewModel.updateThemeSettings(
                                                        settings.darkTheme,
                                                        settings.amoledMode,
                                                        preset
                                                    )
                                                }
                                        ) {
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Black)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Usage & Token Cost Telemetry
                    item {
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
                                Text("Usage, Token Telemetry & Backup", style = MaterialTheme.typography.titleMedium)
                                Text("Total Conversations: ${conversations.size}", style = MaterialTheme.typography.bodySmall)
                                Text("Cumulative Tokens Processed: $totalTokensAll tokens", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "Cumulative Estimated Cost: $${"%.5f".format(totalCostAll)} USD",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EmeraldAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = { viewModel.exportWorkspaceBackupJson() },
                                    modifier = Modifier.testTag("export_backup_button")
                                ) {
                                    Text("Export Workspace Backup JSON (Zero Secrets)")
                                }
                            }
                        }
                    }

                    // About PiashAgent Card
                    item {
                        Surface(
                            color = Color(0xFF0A0A0F),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldAccent)
                                    Spacer(Modifier.width(8.dp))
                                    Text("About PiashAgent v1.0.0", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    text = "Tagline: Your Personal AI Development Agent",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyanAccent
                                )
                                Text(
                                    text = "Architecture: Pure Native Android (Kotlin + Jetpack Compose + Room + DataStore + Android Keystore + SAF + Firebase AI Logic). Strictly zero Termux, zero Proot, zero Chroot, zero Root dependencies.",
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
}
