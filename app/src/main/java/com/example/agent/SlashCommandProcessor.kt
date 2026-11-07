package com.example.agent

import com.example.data.local.ConversationEntity
import com.example.data.local.MemoryEntryEntity
import com.example.data.local.McpServerEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.SkillEntity
import com.example.domain.model.AgentRole
import com.example.domain.model.ModelCatalog
import com.example.domain.model.ProviderType

sealed interface SlashCommandAction {
    data class ImmediateReply(val markdown: String) : SlashCommandAction
    data class SwitchModel(val modelId: String, val message: String) : SlashCommandAction
    data class NavigateTab(val tabIndex: Int, val message: String) : SlashCommandAction
    data class CompactConversation(val message: String) : SlashCommandAction
    data class ClearConversation(val message: String) : SlashCommandAction
    data class ExportConversation(val message: String) : SlashCommandAction
    data class TriggerMultiAgentWorkflow(val goal: String) : SlashCommandAction
    data class RunWorkspaceTask(val taskCommand: String) : SlashCommandAction
}

/**
 * Claude Code-style slash command parser and executor.
 * Supports all 17 specified slash commands plus helpful fallback guidance.
 */
object SlashCommandProcessor {

    val SUPPORTED_COMMANDS = listOf(
        "/help" to "Display all available slash commands and agent shortcuts",
        "/model" to "Switch active model (e.g., /model gemini-3.8-flash, /model gemini-4-argon)",
        "/provider" to "Show configured AI providers and connection status",
        "/skills" to "List active skills or jump to Skills Manager",
        "/agents" to "Inspect Multi-Agent Orchestrator roles or launch /agents <goal>",
        "/plan" to "Generate an explicit multi-step engineering plan for a task",
        "/compact" to "Compact conversation history to save context window tokens",
        "/cost" to "Display token usage and estimated USD cost for current session",
        "/session" to "Inspect current session ID, branch, and message stats",
        "/project" to "Show active project root, Git branch, and file metrics",
        "/permissions" to "Inspect tool permission policies and open Security Settings",
        "/mcp" to "Show connected Model Context Protocol (MCP) servers and tools",
        "/memory" to "View Conversation, Project, and User Preference memory entries",
        "/review" to "Run automated code review & syntax diagnostics on workspace",
        "/test" to "Execute native workspace verification and syntax test suite",
        "/export" to "Export full conversation markdown to Android clipboard / share",
        "/clear" to "Clear messages in the current conversation session"
    )

    fun process(
        rawInput: String,
        currentProject: ProjectEntity?,
        currentConversation: ConversationEntity?,
        skills: List<SkillEntity>,
        mcpServers: List<McpServerEntity>,
        memories: List<MemoryEntryEntity>,
        roleModels: Map<AgentRole, String>
    ): SlashCommandAction? {
        val trimmed = rawInput.trim()
        if (!trimmed.startsWith("/")) return null

        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        val cmd = parts[0].lowercase()
        val arg = parts.getOrNull(1)?.trim().orEmpty()

        return when (cmd) {
            "/help" -> SlashCommandAction.ImmediateReply(
                buildString {
                    appendLine("### PiashAgent Slash Commands")
                    appendLine("Execute native workspace and agent actions directly from chat:")
                    appendLine()
                    SUPPORTED_COMMANDS.forEach { (c, desc) ->
                        appendLine("- **`$c`** — $desc")
                    }
                    appendLine()
                    appendLine("> Tip: Type `/plan <your feature request>` or `/agents <complex task>` to trigger multi-agent orchestration.")
                }
            )

            "/model" -> {
                if (arg.isBlank()) {
                    val listMd = ModelCatalog.models.joinToString("\n") { m ->
                        "- **`${m.id}`** (${m.displayName} • ${m.provider.badge}) — $${m.inputCostPerMillion}/1M in"
                    }
                    SlashCommandAction.ImmediateReply(
                        "### Available Models\nCurrent model: **`${currentConversation?.modelId ?: "gemini-3.8-flash"}`**\n\n$listMd\n\nUsage: `/model <model_id>`"
                    )
                } else {
                    val match = ModelCatalog.models.firstOrNull {
                        it.id.equals(arg, ignoreCase = true) || it.displayName.contains(arg, ignoreCase = true)
                    }
                    if (match != null) {
                        SlashCommandAction.SwitchModel(
                            modelId = match.id,
                            message = "Switched active session model to **${match.displayName}** (`${match.id}` via ${match.provider.displayName})."
                        )
                    } else {
                        SlashCommandAction.ImmediateReply(
                            "Unknown model `$arg`. Available IDs: ${ModelCatalog.models.joinToString { "`${it.id}`" }}"
                        )
                    }
                }
            }

            "/provider" -> {
                val md = ProviderType.entries.joinToString("\n") { p ->
                    "- **${p.displayName}** (`${p.badge}`) • Default Endpoint: `${p.defaultBaseUrl}`"
                }
                SlashCommandAction.ImmediateReply(
                    "### Configured AI Providers\n$md\n\nManage API keys in **Settings > AI Providers**."
                )
            }

            "/skills" -> {
                val enabled = skills.filter { it.isEnabled }
                val md = enabled.joinToString("\n") { s ->
                    "- **${s.name}** (`v${s.version}`) — ${s.description}"
                }
                SlashCommandAction.ImmediateReply(
                    "### Active Skills (${enabled.size} / ${skills.size} installed)\n$md\n\nOpen the **Skills** tab to enable, disable, import, or export `SKILL.md` packages."
                )
            }

            "/agents" -> {
                if (arg.isNotBlank()) {
                    SlashCommandAction.TriggerMultiAgentWorkflow(arg)
                } else {
                    val md = AgentRole.entries.joinToString("\n") { role ->
                        val assigned = roleModels[role] ?: role.defaultModelId
                        "- **${role.title}** (`${role.badge}`) → Model: `$assigned`\n  _${role.description}_"
                    }
                    SlashCommandAction.ImmediateReply(
                        "### Multi-Agent System Roles\n$md\n\nRun `/agents <task description>` to launch a coordinated multi-agent workflow."
                    )
                }
            }

            "/plan" -> {
                val goal = arg.ifBlank { "Inspect current workspace architecture and propose improvements" }
                SlashCommandAction.TriggerMultiAgentWorkflow("Create a detailed step-by-step execution plan for: $goal")
            }

            "/compact" -> SlashCommandAction.CompactConversation(
                "Compacted conversation context history to optimize token usage."
            )

            "/cost" -> {
                val tokens = currentConversation?.totalTokens ?: 0
                val cost = currentConversation?.estimatedCostUsd ?: 0.0
                val model = ModelCatalog.findById(currentConversation?.modelId ?: "gemini-3.8-flash")
                SlashCommandAction.ImmediateReply(
                    """
                    ### Session Token & Cost Telemetry
                    - **Session Title**: ${currentConversation?.title ?: "Active Session"}
                    - **Active Model**: ${model.displayName} (`${model.id}`)
                    - **Cumulative Tokens**: $tokens tokens
                    - **Estimated Session Cost**: $${"%.5f".format(cost)} USD
                    - **Model Pricing**: $${model.inputCostPerMillion}/1M input • $${model.outputCostPerMillion}/1M output
                    """.trimIndent()
                )
            }

            "/session" -> SlashCommandAction.ImmediateReply(
                """
                ### Session Diagnostics
                - **Session ID**: `${currentConversation?.id ?: "none"}`
                - **Parent Branch ID**: `${currentConversation?.parentConversationId ?: "root"}`
                - **Project ID**: `${currentProject?.id ?: "default"}`
                - **Active Model**: `${currentConversation?.modelId ?: "gemini-3.8-flash"}`
                - **Total Tokens**: ${currentConversation?.totalTokens ?: 0}
                """.trimIndent()
            )

            "/project" -> SlashCommandAction.ImmediateReply(
                """
                ### Active Project Workspace
                - **Project Name**: ${currentProject?.name ?: "Default Workspace"}
                - **Sandbox Path**: `${currentProject?.rootPath ?: ""}`
                - **Git Branch**: `${currentProject?.gitBranch ?: "main"}`
                - **Active Skills**: `${currentProject?.activeSkillsCsv ?: ""}`
                - **Project Instructions**: ${currentProject?.instructions ?: "Standard Clean Architecture"}
                """.trimIndent()
            )

            "/permissions" -> SlashCommandAction.NavigateTab(
                tabIndex = 5,
                message = "Opened **Settings & Security** to configure per-tool permission policies and inspect the audit log."
            )

            "/mcp" -> {
                val md = mcpServers.joinToString("\n") { s ->
                    "- **${s.name}** (`${s.endpointUrl}`) — Status: **${s.statusMessage}** • Trusted: `${s.isTrusted}`"
                }.ifEmpty { "No MCP servers registered." }
                SlashCommandAction.ImmediateReply(
                    "### Model Context Protocol (MCP) Servers\n$md"
                )
            }

            "/memory" -> {
                val byLevel = memories.groupBy { it.level }
                val md = buildString {
                    appendLine("### Agent Memory Hierarchy (${memories.size} entries)")
                    listOf("CONVERSATION", "PROJECT", "USER_PREFERENCE").forEach { lvl ->
                        val items = byLevel[lvl].orEmpty()
                        appendLine("#### $lvl (${items.size})")
                        if (items.isEmpty()) {
                            appendLine("_No entries_")
                        } else {
                            items.take(8).forEach { m ->
                                appendLine("- **`${m.memoryKey}`**: ${m.memoryValue}")
                            }
                        }
                        appendLine()
                    }
                }
                SlashCommandAction.ImmediateReply(md)
            }

            "/review" -> SlashCommandAction.RunWorkspaceTask("verify")

            "/test" -> SlashCommandAction.RunWorkspaceTask("test")

            "/export" -> SlashCommandAction.ExportConversation(
                "Exported full conversation transcript to Android clipboard."
            )

            "/clear" -> SlashCommandAction.ClearConversation(
                "Cleared current conversation history."
            )

            else -> SlashCommandAction.ImmediateReply(
                "Unknown command **`$cmd`**. Type **`/help`** to view all 17 supported slash commands (`/model`, `/provider`, `/skills`, `/agents`, `/plan`, `/compact`, `/cost`, `/session`, `/project`, `/permissions`, `/mcp`, `/memory`, `/review`, `/test`, `/export`, `/clear`)."
            )
        }
    }
}
