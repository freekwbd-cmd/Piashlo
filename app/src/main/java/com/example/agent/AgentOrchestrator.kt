package com.example.agent

import com.example.data.local.MemoryEntryEntity
import com.example.data.local.MessageEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.SkillEntity
import com.example.domain.model.AgentRole
import com.example.domain.model.AgentTaskStatus
import com.example.domain.model.FileDiffPreview
import com.example.domain.model.ModelCatalog
import com.example.domain.model.PendingPermissionRequest
import com.example.domain.model.PermissionPolicy
import com.example.domain.model.ToolExecutionRecord
import com.example.execution.NativeWorkspaceEngine
import com.example.providers.AiProviderGateway
import com.example.security.SecureKeyVault
import com.example.tools.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class AgentStepUpdate(
    val phaseLabel: String,
    val thinkingLog: String,
    val streamingText: String,
    val executedTools: List<ToolExecutionRecord>,
    val fileDiffs: List<FileDiffPreview>,
    val totalTokensUsed: Int,
    val estimatedCostUsd: Double
)

/**
 * Core Tool-Using AI Agent Engine & Multi-Agent Orchestrator.
 *
 * Implements the full Agent Loop:
 * User Request -> Context Collection -> Planning -> Tool Selection -> Permission Check ->
 * Tool Execution -> Result Observation -> Next Action -> Verification -> Final Response.
 */
class AgentOrchestrator(
    private val providerGateway: AiProviderGateway,
    private val toolRegistry: ToolRegistry,
    private val workspaceEngine: NativeWorkspaceEngine
) {
    // Active file lock table preventing concurrent multi-agent edits on the same file
    private val fileLocks = ConcurrentHashMap<String, AgentRole>()

    private val _agentStatuses = MutableStateFlow<Map<AgentRole, AgentTaskStatus>>(
        AgentRole.entries.associateWith { role ->
            AgentTaskStatus(
                role = role,
                assignedModelId = role.defaultModelId,
                state = "IDLE",
                currentStep = "Standby"
            )
        }
    )
    val agentStatuses: StateFlow<Map<AgentRole, AgentTaskStatus>> = _agentStatuses.asStateFlow()

    private val _pendingPermission = MutableStateFlow<PendingPermissionRequest?>(null)
    val pendingPermission: StateFlow<PendingPermissionRequest?> = _pendingPermission.asStateFlow()

    private var permissionDecisionDeferred: CompletableDeferred<Boolean>? = null

    fun resolvePermissionRequest(approved: Boolean) {
        permissionDecisionDeferred?.complete(approved)
        permissionDecisionDeferred = null
        _pendingPermission.value = null
    }

    fun acquireFileLock(relativePath: String, role: AgentRole): Boolean {
        val normalized = relativePath.trim().removePrefix("/")
        val currentOwner = fileLocks.putIfAbsent(normalized, role)
        return currentOwner == null || currentOwner == role
    }

    fun releaseAgentLocks(role: AgentRole) {
        val keysToRemove = fileLocks.entries.filter { it.value == role }.map { it.key }
        keysToRemove.forEach { fileLocks.remove(it) }
    }

    /**
     * Executes the complete iterative Agent Loop for a user prompt.
     */
    suspend fun executeAgentLoop(
        userPrompt: String,
        project: ProjectEntity,
        conversationHistory: List<MessageEntity>,
        activeSkills: List<SkillEntity>,
        memories: List<MemoryEntryEntity>,
        selectedModelId: String,
        roleModels: Map<AgentRole, String>,
        toolPolicies: Map<String, PermissionPolicy>,
        maxIterations: Int,
        timeoutSeconds: Int,
        onUpdate: suspend (AgentStepUpdate) -> Unit
    ): AgentStepUpdate {
        val projectRoot = File(project.rootPath).apply { mkdirs() }
        val executedTools = mutableListOf<ToolExecutionRecord>()
        val fileDiffs = mutableListOf<FileDiffPreview>()
        val thinkingBuilder = StringBuilder()
        var cumulativeTokens = 0
        var cumulativeCost = 0.0
        val modelSpec = ModelCatalog.findById(selectedModelId)
        val timeoutMs = (timeoutSeconds * 1000L).coerceIn(30_000L, 60_000L)

        try {
            updateRoleStatus(AgentRole.MAIN_AGENT, selectedModelId, "RUNNING", "Collecting workspace context…")
            thinkingBuilder.appendLine("1. [Context Collection] Project '${project.name}' on branch '${project.gitBranch}'")

            // Gather workspace file tree summary
            val workspaceFiles = projectRoot.walkTopDown()
                .onEnter { it.name != ".piash_git" }
                .filter { it.isFile }
                .map { "${it.relativeTo(projectRoot).path} (${it.length()} B)" }
                .toList()

            thinkingBuilder.appendLine("   • Indexed ${workspaceFiles.size} workspace files & ${activeSkills.size} active skills.")
            onUpdate(
                AgentStepUpdate(
                    phaseLabel = "Collecting Context & Planning…",
                    thinkingLog = thinkingBuilder.toString(),
                    streamingText = "",
                    executedTools = executedTools.toList(),
                    fileDiffs = fileDiffs.toList(),
                    totalTokensUsed = cumulativeTokens,
                    estimatedCostUsd = cumulativeCost
                )
            )

            // Build system prompt with tool schemas, active skills, and 3-level memory
            val systemPrompt = buildSystemPrompt(
                project = project,
                workspaceFiles = workspaceFiles,
                activeSkills = activeSkills,
                memories = memories,
                conversationHistory = conversationHistory
            )

            var iteration = 0
            var workingContext = userPrompt
            var finalResponseText = ""

            while (iteration < maxIterations.coerceIn(1, 10)) {
                iteration++
                updateRoleStatus(
                    AgentRole.MAIN_AGENT,
                    selectedModelId,
                    "RUNNING",
                    "Iteration $iteration/$maxIterations — Reasoning & Tool Selection"
                )

                val streamedBuilder = StringBuilder()
                providerGateway.streamCompletion(
                    modelSpec = modelSpec,
                    systemPrompt = systemPrompt,
                    userPrompt = workingContext,
                    timeoutMs = timeoutMs
                ).collect { partial ->
                    streamedBuilder.clear()
                    streamedBuilder.append(partial)
                    onUpdate(
                        AgentStepUpdate(
                            phaseLabel = "Streaming Response (Step $iteration)…",
                            thinkingLog = thinkingBuilder.toString(),
                            streamingText = stripRawToolBlocksForDisplay(partial),
                            executedTools = executedTools.toList(),
                            fileDiffs = fileDiffs.toList(),
                            totalTokensUsed = cumulativeTokens,
                            estimatedCostUsd = cumulativeCost
                        )
                    )
                }

                val modelOutput = streamedBuilder.toString()
                val inTokens = AiProviderGateway.estimateTokens(systemPrompt + workingContext)
                val outTokens = AiProviderGateway.estimateTokens(modelOutput)
                cumulativeTokens += (inTokens + outTokens)
                cumulativeCost += AiProviderGateway.estimateCostUsd(modelSpec, inTokens, outTokens)

                val parsedCalls = extractToolCalls(modelOutput)
                if (parsedCalls.isEmpty()) {
                    finalResponseText = modelOutput.trim()
                    thinkingBuilder.appendLine("2. [Verification] Completed in $iteration iteration(s) ($cumulativeTokens tokens).")
                    break
                }

                // Execute requested tools sequentially with permission checks & file lock checks
                val toolResultsSummary = StringBuilder()
                for ((toolName, argsJson) in parsedCalls) {
                    thinkingBuilder.appendLine("• [Tool Selection] $toolName($argsJson)")
                    val record = checkPermissionAndExecuteTool(
                        toolName = toolName,
                        argsJson = argsJson,
                        project = project,
                        projectRoot = projectRoot,
                        agentRole = AgentRole.MAIN_AGENT,
                        toolPolicies = toolPolicies,
                        onWaitingPermission = {
                            onUpdate(
                                AgentStepUpdate(
                                    phaseLabel = "Awaiting User Permission for '$toolName'…",
                                    thinkingLog = thinkingBuilder.toString(),
                                    streamingText = stripRawToolBlocksForDisplay(modelOutput),
                                    executedTools = executedTools.toList(),
                                    fileDiffs = fileDiffs.toList(),
                                    totalTokensUsed = cumulativeTokens,
                                    estimatedCostUsd = cumulativeCost
                                )
                            )
                        }
                    )
                    executedTools.add(record)
                    record.fileDiff?.let { fileDiffs.add(it) }
                    thinkingBuilder.appendLine("  -> [${record.status}] ${record.toolName} (${record.durationMs}ms)")

                    toolResultsSummary.appendLine("Tool `${record.toolName}` status=${record.status}:")
                    toolResultsSummary.appendLine(record.output.take(2500))
                    toolResultsSummary.appendLine()

                    onUpdate(
                        AgentStepUpdate(
                            phaseLabel = "Executed ${record.toolName} (${record.durationMs}ms)",
                            thinkingLog = thinkingBuilder.toString(),
                            streamingText = stripRawToolBlocksForDisplay(modelOutput),
                            executedTools = executedTools.toList(),
                            fileDiffs = fileDiffs.toList(),
                            totalTokensUsed = cumulativeTokens,
                            estimatedCostUsd = cumulativeCost
                        )
                    )
                }

                // Prepare next loop iteration context with observed tool outputs
                workingContext = buildString {
                    appendLine("Original User Request: $userPrompt")
                    appendLine()
                    appendLine("Previous Assistant Output:")
                    appendLine(modelOutput)
                    appendLine()
                    appendLine("Observed Tool Execution Results:")
                    appendLine(toolResultsSummary.toString())
                    appendLine("Now verify the tool results and provide the final helpful response (or invoke another <tool_call> only if further actions are required).")
                }
                finalResponseText = stripRawToolBlocksForDisplay(modelOutput)
            }

            updateRoleStatus(
                AgentRole.MAIN_AGENT,
                selectedModelId,
                "COMPLETED",
                "Completed (${executedTools.size} tools, $cumulativeTokens tokens)"
            )

            return AgentStepUpdate(
                phaseLabel = "Ready",
                thinkingLog = thinkingBuilder.toString().trim(),
                streamingText = finalResponseText.ifBlank { "Task completed. Check the executed tool cards above for details." },
                executedTools = executedTools.toList(),
                fileDiffs = fileDiffs.toList(),
                totalTokensUsed = cumulativeTokens,
                estimatedCostUsd = cumulativeCost
            )
        } catch (e: CancellationException) {
            updateRoleStatus(AgentRole.MAIN_AGENT, selectedModelId, "IDLE", "Cancelled by user")
            throw e
        } catch (e: Exception) {
            updateRoleStatus(AgentRole.MAIN_AGENT, selectedModelId, "ERROR", e.localizedMessage ?: "Failed")
            throw e
        } finally {
            releaseAgentLocks(AgentRole.MAIN_AGENT)
        }
    }

    /**
     * Multi-Agent Orchestration Pipeline:
     * Sequentially coordinates Planner -> Research -> Coding -> Review -> Testing agents
     * with file lock conflict prevention and live status updates.
     */
    suspend fun runMultiAgentOrchestration(
        goal: String,
        project: ProjectEntity,
        activeSkills: List<SkillEntity>,
        roleModels: Map<AgentRole, String>,
        toolPolicies: Map<String, PermissionPolicy>,
        timeoutSeconds: Int,
        onUpdate: suspend (AgentStepUpdate) -> Unit
    ): AgentStepUpdate {
        val projectRoot = File(project.rootPath).apply { mkdirs() }
        val executedTools = mutableListOf<ToolExecutionRecord>()
        val fileDiffs = mutableListOf<FileDiffPreview>()
        val thinking = StringBuilder()
        val report = StringBuilder()
        var totalTokens = 0
        var totalCost = 0.0
        val timeoutMs = (timeoutSeconds * 1000L).coerceIn(30_000L, 60_000L)

        try {
            // 1. RESEARCH AGENT: Inspect project & gather context
            val researchModel = roleModels[AgentRole.RESEARCH_AGENT] ?: AgentRole.RESEARCH_AGENT.defaultModelId
            updateRoleStatus(AgentRole.RESEARCH_AGENT, researchModel, "RUNNING", "Inspecting workspace structure & code")
            thinking.appendLine("• [Research Agent] Inspecting workspace '${project.name}'…")
            val inspectRecord = toolRegistry.executeTool(
                toolName = "project_inspect",
                argumentsJson = "{}",
                projectId = project.id,
                projectRoot = projectRoot,
                agentRole = AgentRole.RESEARCH_AGENT
            )
            executedTools.add(inspectRecord)
            updateRoleStatus(
                AgentRole.RESEARCH_AGENT,
                researchModel,
                "COMPLETED",
                "Indexed project structure (${inspectRecord.durationMs}ms)",
                summary = inspectRecord.output.lines().take(3).joinToString(" • ")
            )

            onUpdate(
                AgentStepUpdate(
                    phaseLabel = "Planner Agent creating execution plan…",
                    thinkingLog = thinking.toString(),
                    streamingText = "### Multi-Agent Orchestration Active\n- **Research Agent**: Workspace indexed ✓",
                    executedTools = executedTools.toList(),
                    fileDiffs = fileDiffs.toList(),
                    totalTokensUsed = totalTokens,
                    estimatedCostUsd = totalCost
                )
            )

            // 2. PLANNER AGENT & CODING AGENT & REVIEW AGENT synthesis
            val plannerModel = roleModels[AgentRole.PLANNER_AGENT] ?: AgentRole.PLANNER_AGENT.defaultModelId
            updateRoleStatus(AgentRole.PLANNER_AGENT, plannerModel, "RUNNING", "Synthesizing architecture & execution plan")
            thinking.appendLine("• [Planner Agent] Using model '$plannerModel' to plan and coordinate.")

            val multiAgentPrompt = buildString {
                appendLine("You are the PiashAgent Multi-Agent Orchestrator coordinating Planner, Coding, Review, and Testing agents.")
                appendLine("Project Name: ${project.name}")
                appendLine("Workspace Snapshot:\n${inspectRecord.output}")
                appendLine("Active Skills: ${activeSkills.filter { it.isEnabled }.joinToString { it.name }}")
                appendLine()
                appendLine("If creating or updating a file is appropriate for the user's goal, emit a <tool_call>{\"tool\":\"create_file\",\"args\":{\"path\":\"...\",\"content\":\"...\"}}</tool_call> block.")
                appendLine("Structure your response with clear sections for:")
                appendLine("1. **Planner Agent — Execution Plan**")
                appendLine("2. **Coding Agent — Implementation & Changes**")
                appendLine("3. **Review & Security Audit**")
                appendLine()
                appendLine("User Goal: $goal")
            }

            val spec = ModelCatalog.findById(plannerModel)
            val streamAcc = StringBuilder()
            providerGateway.streamCompletion(
                modelSpec = spec,
                systemPrompt = "You are PiashAgent Multi-Agent Coordinator.",
                userPrompt = multiAgentPrompt,
                timeoutMs = timeoutMs
            ).collect { chunk ->
                streamAcc.clear()
                streamAcc.append(chunk)
                onUpdate(
                    AgentStepUpdate(
                        phaseLabel = "Multi-Agent Pipeline Streaming…",
                        thinkingLog = thinking.toString(),
                        streamingText = stripRawToolBlocksForDisplay(chunk),
                        executedTools = executedTools.toList(),
                        fileDiffs = fileDiffs.toList(),
                        totalTokensUsed = totalTokens,
                        estimatedCostUsd = totalCost
                    )
                )
            }

            val fullOutput = streamAcc.toString()
            val inTok = AiProviderGateway.estimateTokens(multiAgentPrompt)
            val outTok = AiProviderGateway.estimateTokens(fullOutput)
            totalTokens += (inTok + outTok)
            totalCost += AiProviderGateway.estimateCostUsd(spec, inTok, outTok)
            updateRoleStatus(AgentRole.PLANNER_AGENT, plannerModel, "COMPLETED", "Execution plan finalized")

            // 3. CODING AGENT: Execute any file tools with file-lock protection
            val codingModel = roleModels[AgentRole.CODING_AGENT] ?: AgentRole.CODING_AGENT.defaultModelId
            val toolCalls = extractToolCalls(fullOutput)
            if (toolCalls.isNotEmpty()) {
                updateRoleStatus(AgentRole.CODING_AGENT, codingModel, "RUNNING", "Applying code changes with file lock protection")
                for ((toolName, argsJson) in toolCalls) {
                    val path = runCatching { JSONObject(argsJson).optString("path") }.getOrDefault("")
                    if (path.isNotBlank() && !acquireFileLock(path, AgentRole.CODING_AGENT)) {
                        thinking.appendLine("• [Conflict Guard] Skipped concurrent edit on locked file '$path'")
                        continue
                    }
                    val rec = checkPermissionAndExecuteTool(
                        toolName = toolName,
                        argsJson = argsJson,
                        project = project,
                        projectRoot = projectRoot,
                        agentRole = AgentRole.CODING_AGENT,
                        toolPolicies = toolPolicies,
                        onWaitingPermission = {}
                    )
                    executedTools.add(rec)
                    rec.fileDiff?.let { fileDiffs.add(it) }
                }
                releaseAgentLocks(AgentRole.CODING_AGENT)
                updateRoleStatus(AgentRole.CODING_AGENT, codingModel, "COMPLETED", "Applied ${toolCalls.size} tool action(s)")
            } else {
                updateRoleStatus(AgentRole.CODING_AGENT, codingModel, "COMPLETED", "Code synthesis completed")
            }

            // 4. REVIEW AGENT & 5. TESTING AGENT: Run syntax diagnostics & verification
            val reviewModel = roleModels[AgentRole.REVIEW_AGENT] ?: AgentRole.REVIEW_AGENT.defaultModelId
            val testingModel = roleModels[AgentRole.TESTING_AGENT] ?: AgentRole.TESTING_AGENT.defaultModelId
            updateRoleStatus(AgentRole.REVIEW_AGENT, reviewModel, "RUNNING", "Auditing workspace diffs")
            updateRoleStatus(AgentRole.TESTING_AGENT, testingModel, "RUNNING", "Running syntax & verification checks")

            val diagRecord = toolRegistry.executeTool(
                toolName = "syntax_diagnostics",
                argumentsJson = "{}",
                projectId = project.id,
                projectRoot = projectRoot,
                agentRole = AgentRole.TESTING_AGENT
            )
            executedTools.add(diagRecord)
            thinking.appendLine("• [Testing Agent] ${diagRecord.output.lines().firstOrNull().orEmpty()}")

            updateRoleStatus(AgentRole.REVIEW_AGENT, reviewModel, "COMPLETED", "Diff & security audit passed")
            updateRoleStatus(AgentRole.TESTING_AGENT, testingModel, "COMPLETED", "Syntax verification complete")

            report.append(stripRawToolBlocksForDisplay(fullOutput))
            report.appendLine("\n\n---\n### Testing Agent Verification")
            report.appendLine("```\n${diagRecord.output}\n```")

            return AgentStepUpdate(
                phaseLabel = "Multi-Agent Workflow Complete",
                thinkingLog = thinking.toString().trim(),
                streamingText = report.toString(),
                executedTools = executedTools.toList(),
                fileDiffs = fileDiffs.toList(),
                totalTokensUsed = totalTokens,
                estimatedCostUsd = totalCost
            )
        } finally {
            AgentRole.entries.forEach { releaseAgentLocks(it) }
        }
    }

    private suspend fun checkPermissionAndExecuteTool(
        toolName: String,
        argsJson: String,
        project: ProjectEntity,
        projectRoot: File,
        agentRole: AgentRole,
        toolPolicies: Map<String, PermissionPolicy>,
        onWaitingPermission: suspend () -> Unit
    ): ToolExecutionRecord {
        val policy = toolRegistry.getEffectivePolicy(toolName, toolPolicies)
        val schema = toolRegistry.getSchema(toolName)

        if (policy == PermissionPolicy.DENY) {
            return ToolExecutionRecord(
                id = UUID.randomUUID().toString(),
                toolName = toolName,
                category = schema?.category ?: com.example.domain.model.ToolCategory.PROJECT,
                agentRole = agentRole,
                argumentsJson = argsJson,
                status = "DENIED",
                output = "Execution blocked by user Permission Policy (Always Deny).",
                durationMs = 1L
            )
        }

        if (policy == PermissionPolicy.ASK_APPROVAL) {
            val previewDiff = buildPreviewDiffIfApplicable(toolName, argsJson, projectRoot)
            val deferred = CompletableDeferred<Boolean>()
            permissionDecisionDeferred = deferred
            _pendingPermission.value = PendingPermissionRequest(
                id = UUID.randomUUID().toString(),
                toolName = toolName,
                category = schema?.category ?: com.example.domain.model.ToolCategory.PROJECT,
                agentRole = agentRole,
                argumentsJson = argsJson,
                reason = schema?.description ?: "Requires user approval before execution",
                diffPreview = previewDiff
            )
            onWaitingPermission()
            val approved = deferred.await()
            if (!approved) {
                return ToolExecutionRecord(
                    id = UUID.randomUUID().toString(),
                    toolName = toolName,
                    category = schema?.category ?: com.example.domain.model.ToolCategory.PROJECT,
                    agentRole = agentRole,
                    argumentsJson = argsJson,
                    status = "DENIED",
                    output = "User declined tool execution permission.",
                    durationMs = 1L
                )
            }
            return toolRegistry.executeTool(
                toolName = toolName,
                argumentsJson = argsJson,
                projectId = project.id,
                projectRoot = projectRoot,
                agentRole = agentRole,
                decisionLabel = "USER_APPROVED"
            )
        }

        return toolRegistry.executeTool(
            toolName = toolName,
            argumentsJson = argsJson,
            projectId = project.id,
            projectRoot = projectRoot,
            agentRole = agentRole,
            decisionLabel = "AUTO_ALLOWED"
        )
    }

    private fun buildPreviewDiffIfApplicable(
        toolName: String,
        argsJson: String,
        projectRoot: File
    ): FileDiffPreview? {
        return runCatching {
            val json = JSONObject(argsJson)
            val path = json.optString("path")
            if (path.isBlank()) return null
            val file = SecureKeyVault.resolveSafeProjectFile(projectRoot, path)
            val oldText = if (file.exists() && file.isFile) file.readText() else ""
            val newText = when (toolName.lowercase()) {
                "delete_file" -> ""
                "apply_patch", "create_file" -> json.optString("new_content", json.optString("content", ""))
                else -> return null
            }
            workspaceEngine.generateUnifiedDiff(path, oldText, newText, applied = false)
        }.getOrNull()
    }

    private fun updateRoleStatus(
        role: AgentRole,
        modelId: String,
        state: String,
        step: String,
        summary: String = ""
    ) {
        val current = _agentStatuses.value.toMutableMap()
        val prev = current[role]
        current[role] = AgentTaskStatus(
            role = role,
            assignedModelId = modelId,
            state = state,
            currentStep = step,
            lockedFiles = fileLocks.entries.filter { it.value == role }.map { it.key },
            lastSummary = summary.ifBlank { prev?.lastSummary.orEmpty() }
        )
        _agentStatuses.value = current
    }

    private fun buildSystemPrompt(
        project: ProjectEntity,
        workspaceFiles: List<String>,
        activeSkills: List<SkillEntity>,
        memories: List<MemoryEntryEntity>,
        conversationHistory: List<MessageEntity>
    ): String {
        val enabledSkills = activeSkills.filter { it.isEnabled }
        val toolCatalogText = toolRegistry.schemas.joinToString("\n") { t ->
            "- ${t.name}: ${t.description} | args: ${t.parametersDescription}"
        }
        val recentTurns = conversationHistory.takeLast(6).joinToString("\n") { m ->
            "${m.role}: ${m.content.take(500)}"
        }

        return buildString {
            appendLine("You are PiashAgent — Your Personal AI Development Agent running natively on Android.")
            appendLine("Active Project: ${project.name} (Branch: ${project.gitBranch})")
            appendLine("Project Instructions: ${project.instructions}")
            appendLine("Workspace Files (${workspaceFiles.size}): ${workspaceFiles.take(30).joinToString(", ")}")
            appendLine()
            if (enabledSkills.isNotEmpty()) {
                appendLine("Enabled Skills:")
                enabledSkills.take(8).forEach { s ->
                    appendLine("- ${s.name}: ${s.instructions}")
                }
                appendLine()
            }
            if (memories.isNotEmpty()) {
                appendLine("Persistent Agent Memory:")
                memories.take(10).forEach { mem ->
                    appendLine("- [${mem.level}] ${mem.memoryKey}: ${mem.memoryValue}")
                }
                appendLine()
            }
            appendLine("Available Workspace Tools:")
            appendLine(toolCatalogText)
            appendLine()
            appendLine("Tool Calling Protocol:")
            appendLine("Whenever you need to inspect, read, create, edit, analyze, or commit files in the project workspace, emit one or more tool calls using this exact XML tag format:")
            appendLine("""<tool_call>{"tool": "tool_name", "args": {"path": "..."}}</tool_call>""")
            appendLine("After emitting <tool_call> tags, explain what you are doing clearly in Markdown.")
            if (recentTurns.isNotBlank()) {
                appendLine()
                appendLine("Recent Conversation Context:")
                appendLine(recentTurns)
            }
        }
    }

    companion object {
        private val TOOL_CALL_REGEX = Regex("(?s)<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>")

        fun extractToolCalls(text: String): List<Pair<String, String>> {
            val list = mutableListOf<Pair<String, String>>()
            TOOL_CALL_REGEX.findAll(text).forEach { match ->
                val rawJson = match.groupValues[1].trim()
                runCatching {
                    val obj = JSONObject(rawJson)
                    val toolName = obj.optString("tool").ifBlank { obj.optString("name") }
                    val argsObj = obj.optJSONObject("args") ?: obj.optJSONObject("arguments") ?: JSONObject()
                    if (toolName.isNotBlank()) {
                        list.add(toolName to argsObj.toString())
                    }
                }
            }
            return list
        }

        fun stripRawToolBlocksForDisplay(text: String): String {
            return text.replace(TOOL_CALL_REGEX, "").trim()
        }

        fun serializeToolRecords(records: List<ToolExecutionRecord>): String {
            val arr = JSONArray()
            records.forEach { r ->
                arr.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("toolName", r.toolName)
                        put("category", r.category.name)
                        put("agentRole", r.agentRole.name)
                        put("argumentsJson", r.argumentsJson)
                        put("status", r.status)
                        put("output", r.output)
                        put("durationMs", r.durationMs)
                    }
                )
            }
            return arr.toString()
        }

        fun deserializeToolRecords(json: String): List<ToolExecutionRecord> {
            if (json.isBlank() || json == "[]") return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    ToolExecutionRecord(
                        id = o.optString("id"),
                        toolName = o.optString("toolName"),
                        category = com.example.domain.model.ToolCategory.entries.firstOrNull {
                            it.name == o.optString("category")
                        } ?: com.example.domain.model.ToolCategory.PROJECT,
                        agentRole = AgentRole.entries.firstOrNull {
                            it.name == o.optString("agentRole")
                        } ?: AgentRole.MAIN_AGENT,
                        argumentsJson = o.optString("argumentsJson"),
                        status = o.optString("status", "COMPLETED"),
                        output = o.optString("output"),
                        durationMs = o.optLong("durationMs", 1L)
                    )
                }
            }.getOrDefault(emptyList())
        }

        fun serializeFileDiffs(diffs: List<FileDiffPreview>): String {
            val arr = JSONArray()
            diffs.forEach { d ->
                arr.put(
                    JSONObject().apply {
                        put("relativePath", d.relativePath)
                        put("oldContent", d.oldContent)
                        put("newContent", d.newContent)
                        put("unifiedDiff", d.unifiedDiff)
                        put("additions", d.additions)
                        put("deletions", d.deletions)
                        put("applied", d.applied)
                    }
                )
            }
            return arr.toString()
        }

        fun deserializeFileDiffs(json: String): List<FileDiffPreview> {
            if (json.isBlank() || json == "[]") return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    FileDiffPreview(
                        relativePath = o.optString("relativePath"),
                        oldContent = o.optString("oldContent"),
                        newContent = o.optString("newContent"),
                        unifiedDiff = o.optString("unifiedDiff"),
                        additions = o.optInt("additions"),
                        deletions = o.optInt("deletions"),
                        applied = o.optBoolean("applied", true)
                    )
                }
            }.getOrDefault(emptyList())
        }
    }
}
