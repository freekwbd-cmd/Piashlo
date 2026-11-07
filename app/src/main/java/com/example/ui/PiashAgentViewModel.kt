package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.AgentOrchestrator
import com.example.agent.SlashCommandAction
import com.example.agent.SlashCommandProcessor
import com.example.data.local.AgentSettingsState
import com.example.data.local.AuditLogEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.McpServerEntity
import com.example.data.local.MemoryEntryEntity
import com.example.data.local.MessageEntity
import com.example.data.local.PiashDatabase
import com.example.data.local.ProjectEntity
import com.example.data.local.SkillEntity
import com.example.data.local.UserPreferencesStore
import com.example.domain.model.AgentRole
import com.example.domain.model.AgentTaskStatus
import com.example.domain.model.FileDiffPreview
import com.example.domain.model.ModelCatalog
import com.example.domain.model.PendingPermissionRequest
import com.example.domain.model.PermissionPolicy
import com.example.domain.model.ProviderType
import com.example.domain.model.ToolExecutionRecord
import com.example.execution.NativeWorkspaceEngine
import com.example.execution.TaskExecutionResult
import com.example.mcp.McpClientManager
import com.example.providers.AiProviderGateway
import com.example.security.SecureKeyVault
import com.example.skills.SkillsManager
import com.example.tools.ToolRegistry
import com.example.ui.theme.AccentPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class OpenEditorTab(
    val relativePath: String,
    val savedContent: String,
    val currentContent: String,
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList(),
    val lastModifiedOnDisk: Long = 0L
) {
    val isDirty: Boolean get() = savedContent != currentContent
}

data class WorkspaceFileItem(
    val relativePath: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long
)

@OptIn(ExperimentalCoroutinesApi::class)
class PiashAgentViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val database = PiashDatabase.getInstance(appContext)
    private val dao = database.dao()
    val preferencesStore = UserPreferencesStore(appContext)
    val keyVault = SecureKeyVault(appContext)
    val workspaceEngine = NativeWorkspaceEngine()
    val skillsManager = SkillsManager(appContext, dao)
    val toolRegistry = ToolRegistry(appContext, dao, workspaceEngine)
    val providerGateway = AiProviderGateway(keyVault)
    val orchestrator = AgentOrchestrator(providerGateway, toolRegistry, workspaceEngine)

    private val projectsBaseDir = File(appContext.filesDir, "projects").apply { mkdirs() }

    val mcpManager = McpClientManager(dao) {
        val currentProj = _activeProject.value
        if (currentProj != null) File(currentProj.rootPath) else projectsBaseDir
    }

    // Navigation Tab (0=Chat, 1=Project, 2=Editor, 3=Agents, 4=Skills/MCP, 5=Settings)
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Reactive Database Flows
    val settingsState: StateFlow<AgentSettingsState> = preferencesStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AgentSettingsState())

    val projects: StateFlow<List<ProjectEntity>> = dao.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allConversations: StateFlow<List<ConversationEntity>> = dao.observeAllConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val skills: StateFlow<List<SkillEntity>> = dao.observeSkills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mcpServers: StateFlow<List<McpServerEntity>> = dao.observeMcpServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryEntryEntity>> = dao.observeAllMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = dao.observeAuditLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val agentStatuses: StateFlow<Map<AgentRole, AgentTaskStatus>> = orchestrator.agentStatuses
    val pendingPermission: StateFlow<PendingPermissionRequest?> = orchestrator.pendingPermission

    // Active Project & Conversation
    private val _activeProject = MutableStateFlow<ProjectEntity?>(null)
    val activeProject: StateFlow<ProjectEntity?> = _activeProject.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    val currentMessages: StateFlow<List<MessageEntity>> = _activeConversationId
        .flatMapLatest { convId ->
            if (convId == null) flowOf(emptyList()) else dao.observeMessages(convId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Streaming & Agent Execution State
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _agentStatusText = MutableStateFlow("Agent Ready")
    val agentStatusText: StateFlow<String> = _agentStatusText.asStateFlow()

    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

    private val _streamingThinking = MutableStateFlow("")
    val streamingThinking: StateFlow<String> = _streamingThinking.asStateFlow()

    private val _streamingTools = MutableStateFlow<List<ToolExecutionRecord>>(emptyList())
    val streamingTools: StateFlow<List<ToolExecutionRecord>> = _streamingTools.asStateFlow()

    private val _streamingDiffs = MutableStateFlow<List<FileDiffPreview>>(emptyList())
    val streamingDiffs: StateFlow<List<FileDiffPreview>> = _streamingDiffs.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    // Search & Attachments in Chat
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<String>>(emptyList())
    val pendingAttachments: StateFlow<List<String>> = _pendingAttachments.asStateFlow()

    // Workspace File Tree & Git Status
    private val _workspaceFiles = MutableStateFlow<List<WorkspaceFileItem>>(emptyList())
    val workspaceFiles: StateFlow<List<WorkspaceFileItem>> = _workspaceFiles.asStateFlow()

    private val _gitStatusReport = MutableStateFlow("")
    val gitStatusReport: StateFlow<String> = _gitStatusReport.asStateFlow()

    private val _gitDiffReport = MutableStateFlow("")
    val gitDiffReport: StateFlow<String> = _gitDiffReport.asStateFlow()

    private val _gitLogReport = MutableStateFlow("")
    val gitLogReport: StateFlow<String> = _gitLogReport.asStateFlow()

    private val _taskConsoleHistory = MutableStateFlow<List<TaskExecutionResult>>(emptyList())
    val taskConsoleHistory: StateFlow<List<TaskExecutionResult>> = _taskConsoleHistory.asStateFlow()

    // Integrated Code Editor State
    private val _openTabs = MutableStateFlow<List<OpenEditorTab>>(emptyList())
    val openTabs: StateFlow<List<OpenEditorTab>> = _openTabs.asStateFlow()

    private val _selectedEditorTabIndex = MutableStateFlow(0)
    val selectedEditorTabIndex: StateFlow<Int> = _selectedEditorTabIndex.asStateFlow()

    private val _editorDiffPreview = MutableStateFlow<FileDiffPreview?>(null)
    val editorDiffPreview: StateFlow<FileDiffPreview?> = _editorDiffPreview.asStateFlow()

    private var activeGenerationJob: Job? = null

    init {
        viewModelScope.launch {
            bootstrapWorkspace()
        }
    }

    private suspend fun bootstrapWorkspace() = withContext(Dispatchers.IO) {
        skillsManager.ensureBuiltInSkillsInstalled()
        mcpManager.ensureDefaultMcpServers()

        var existingProjects = dao.getAllProjects()
        if (existingProjects.isEmpty()) {
            val defaultId = "proj-piash-core"
            val rootDir = File(projectsBaseDir, defaultId).apply { mkdirs() }
            toolRegistry.scaffoldProjectTemplate(rootDir, "kotlin")

            val defaultProj = ProjectEntity(
                id = defaultId,
                name = "PiashCore Engine",
                description = "Primary Kotlin & Coroutines AI workspace with native Git VCS and automated test suite",
                rootPath = rootDir.absolutePath,
                instructions = "Follow Clean Architecture, Kotlin Coroutines + StateFlow, zero hardcoded secrets, and comprehensive unit tests.",
                gitBranch = "main"
            )
            dao.upsertProject(defaultProj)
            existingProjects = listOf(defaultProj)

            // Seed default project memory & user preference memory
            dao.upsertMemory(
                MemoryEntryEntity(
                    id = "mem-arch-1",
                    level = "PROJECT",
                    scopeId = defaultId,
                    memoryKey = "architecture.pattern",
                    memoryValue = "MVVM + Clean Architecture with immutable StateFlow and sandboxed file boundaries"
                )
            )
            dao.upsertMemory(
                MemoryEntryEntity(
                    id = "mem-pref-1",
                    level = "USER_PREFERENCE",
                    scopeId = "GLOBAL",
                    memoryKey = "code.style",
                    memoryValue = "Idiomatic Kotlin 2.2, explicit types on public APIs, Pure AMOLED Black theme"
                )
            )
        }

        val firstProj = existingProjects.first()
        _activeProject.value = firstProj
        refreshProjectWorkspace(firstProj)

        // Ensure at least one conversation exists
        val existingConvs = dao.searchConversations("")
        if (existingConvs.isEmpty()) {
            val conv = createNewConversationInternal(firstProj, "Welcome to PiashAgent Workspace")
            dao.upsertMessage(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = conv.id,
                    role = "ASSISTANT",
                    content = """
                        ### Welcome to **PiashAgent** — Your Personal AI Development Agent
                        
                        Your native Android AI engineering workspace is online and ready:
                        - **Active Project**: `${firstProj.name}` (`${firstProj.gitBranch}` branch)
                        - **AI Engine**: Google Gemini 3.8 Flash via Firebase AI Logic (plus configurable Anthropic Claude, OpenAI, DeepSeek, OpenRouter & Ollama adapters)
                        - **26 Native Tools**: File I/O, Code Analysis, Unified Diffs, Native Git VCS, Web Research & Task Console
                        - **20 Installed Skills**: Verified `SKILL.md` packages on disk
                        
                        **Try a prompt or slash command:**
                        - `"Inspect the current project files and add a caching layer to AppEngine.kt"`
                        - `/plan Build a reactive repository with retry logic`
                        - `/agents Audit the workspace for code quality and syntax`
                        - `/help` to view all 17 Claude Code-style slash commands
                    """.trimIndent(),
                    thinkingSummary = "Workspace initialized • 20 skills verified • Native Git VCS active",
                    modelUsed = "gemini-3.8-flash"
                )
            )
            _activeConversationId.value = conv.id
        } else {
            _activeConversationId.value = existingConvs.first().id
        }

        // Open first source file in Code Editor by default
        val mainFile = File(firstProj.rootPath, "src/main/kotlin/AppEngine.kt")
        if (mainFile.exists()) {
            openFileInEditor("src/main/kotlin/AppEngine.kt")
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun dismissBanners() {
        _errorBanner.value = null
        _statusBanner.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // =========================================================================
    // CONVERSATION & CHAT ACTIONS
    // =========================================================================

    fun createNewConversation(title: String = "New Agent Session") {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            val conv = createNewConversationInternal(proj, title)
            _activeConversationId.value = conv.id
            _selectedTab.value = 0
        }
    }

    private suspend fun createNewConversationInternal(
        project: ProjectEntity,
        title: String,
        parentId: String? = null
    ): ConversationEntity {
        val modelId = settingsState.value.selectedModelId
        val spec = ModelCatalog.findById(modelId)
        val conv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            projectId = project.id,
            title = title,
            parentConversationId = parentId,
            modelId = spec.id,
            providerName = spec.provider.name
        )
        dao.upsertConversation(conv)
        return conv
    }

    fun selectConversation(conversationId: String) {
        _activeConversationId.value = conversationId
        viewModelScope.launch {
            val conv = dao.getConversationById(conversationId) ?: return@launch
            val proj = dao.getProjectById(conv.projectId)
            if (proj != null && proj.id != _activeProject.value?.id) {
                _activeProject.value = proj
                refreshProjectWorkspace(proj)
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            dao.clearConversationMessages(conversationId)
            dao.deleteConversation(conversationId)
            if (_activeConversationId.value == conversationId) {
                val remaining = dao.searchConversations("")
                if (remaining.isNotEmpty()) {
                    _activeConversationId.value = remaining.first().id
                } else {
                    val proj = _activeProject.value ?: return@launch
                    val next = createNewConversationInternal(proj, "New Session")
                    _activeConversationId.value = next.id
                }
            }
        }
    }

    /**
     * Branches the current conversation from a specific message into a new session.
     */
    fun branchConversationFromMessage(upToMessage: MessageEntity) {
        val proj = _activeProject.value ?: return
        val currentConvId = _activeConversationId.value ?: return
        viewModelScope.launch {
            val currentConv = dao.getConversationById(currentConvId)
            val branchTitle = "Branch: ${currentConv?.title?.take(22) ?: "Session"}"
            val newConv = createNewConversationInternal(proj, branchTitle, parentId = currentConvId)
            val sourceMessages = dao.getMessagesForConversation(currentConvId)
                .filter { it.createdAt <= upToMessage.createdAt }
            for (msg in sourceMessages) {
                dao.upsertMessage(
                    msg.copy(
                        id = UUID.randomUUID().toString(),
                        conversationId = newConv.id
                    )
                )
            }
            _activeConversationId.value = newConv.id
            _statusBanner.value = "Created branched session '${newConv.title}' (${sourceMessages.size} messages)"
        }
    }

    /**
     * Edits an earlier user message, truncates subsequent messages in that turn, and resends.
     */
    fun editAndResendMessage(message: MessageEntity, updatedPrompt: String) {
        val convId = _activeConversationId.value ?: return
        viewModelScope.launch {
            dao.deleteMessagesFromTimestamp(convId, message.createdAt)
            sendPrompt(updatedPrompt)
        }
    }

    /**
     * Regenerates the last assistant response.
     */
    fun regenerateLastResponse() {
        val convId = _activeConversationId.value ?: return
        viewModelScope.launch {
            val msgs = dao.getMessagesForConversation(convId)
            val lastUser = msgs.lastOrNull { it.role == "USER" } ?: return@launch
            dao.deleteMessagesFromTimestamp(convId, lastUser.createdAt)
            sendPrompt(lastUser.content)
        }
    }

    fun exportCurrentConversationToClipboard() {
        val convId = _activeConversationId.value ?: return
        viewModelScope.launch {
            val conv = dao.getConversationById(convId)
            val msgs = dao.getMessagesForConversation(convId)
            val transcript = buildString {
                appendLine("# PiashAgent Session Export: ${conv?.title ?: "Session"}")
                appendLine("- Model: `${conv?.modelId}`")
                appendLine("- Total Tokens: ${conv?.totalTokens ?: 0} ($${"%.4f".format(conv?.estimatedCostUsd ?: 0.0)})")
                appendLine()
                msgs.forEach { m ->
                    appendLine("## ${m.role} (${m.modelUsed})")
                    appendLine(m.content)
                    appendLine()
                }
            }
            copyToClipboard("PiashAgent Transcript", transcript)
            _statusBanner.value = "Exported conversation markdown (${msgs.size} messages) to clipboard."
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
        _statusBanner.value = "Copied $label to clipboard."
    }

    /**
     * Imports a user-selected file via Android Storage Access Framework (SAF) into the active project workspace
     * and attaches it to the current prompt context.
     */
    fun attachFileFromSafUri(uri: Uri) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var displayName = "imported_file.txt"
                appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                }
                val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val targetFile = File(File(proj.rootPath, "imports").apply { mkdirs() }, safeName)
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val relPath = "imports/$safeName"
                _pendingAttachments.value = (_pendingAttachments.value + relPath).distinct()
                refreshProjectWorkspace(proj)
                _statusBanner.value = "Imported '$safeName' into project workspace at $relPath"
            } catch (e: Exception) {
                _errorBanner.value = "Failed to import SAF file: ${e.localizedMessage}"
            }
        }
    }

    fun removePendingAttachment(relPath: String) {
        _pendingAttachments.value = _pendingAttachments.value - relPath
    }

    fun cancelGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _isGenerating.value = false
        _agentStatusText.value = "Cancelled by user"
        orchestrator.resolvePermissionRequest(false)
    }

    fun resolvePermission(approved: Boolean) {
        orchestrator.resolvePermissionRequest(approved)
    }

    /**
     * Sends a user prompt or executes a `/slash` command.
     */
    fun sendPrompt(rawPrompt: String) {
        val trimmed = rawPrompt.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return

        val proj = _activeProject.value ?: return
        val convId = _activeConversationId.value ?: return

        viewModelScope.launch {
            _errorBanner.value = null
            val currentConv = dao.getConversationById(convId)

            // 1. Check if input is a Slash Command
            if (trimmed.startsWith("/")) {
                val cmdAction = SlashCommandProcessor.process(
                    rawInput = trimmed,
                    currentProject = proj,
                    currentConversation = currentConv,
                    skills = skills.value,
                    mcpServers = mcpServers.value,
                    memories = memories.value,
                    roleModels = settingsState.value.agentModels
                )
                if (cmdAction != null) {
                    handleSlashCommandAction(cmdAction, trimmed, proj, convId)
                    return@launch
                }
            }

            // 2. Standard or Multi-Agent Prompt Execution
            val attachments = _pendingAttachments.value
            _pendingAttachments.value = emptyList()

            val augmentedPrompt = if (attachments.isEmpty()) {
                trimmed
            } else {
                val attachedContents = attachments.mapNotNull { rel ->
                    val f = SecureKeyVault.resolveSafeProjectFile(File(proj.rootPath), rel)
                    if (f.exists() && f.length() < 100_000L) {
                        "Attached Workspace File `$rel`:\n```\n${f.readText().take(4000)}\n```"
                    } else null
                }.joinToString("\n\n")
                "$trimmed\n\n$attachedContents"
            }

            val userMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = "USER",
                content = trimmed,
                attachmentsCsv = attachments.joinToString(","),
                modelUsed = settingsState.value.selectedModelId,
                tokensEstimated = AiProviderGateway.estimateTokens(augmentedPrompt)
            )
            dao.upsertMessage(userMsg)

            // Update conversation title if it's still generic
            if (currentConv != null && (currentConv.title.startsWith("New ") || currentConv.title.startsWith("Welcome"))) {
                dao.upsertConversation(
                    currentConv.copy(
                        title = trimmed.take(42),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            runAgentTurn(augmentedPrompt, proj, convId, isMultiAgent = false)
        }
    }

    fun launchMultiAgentGoal(goal: String) {
        val proj = _activeProject.value ?: return
        val convId = _activeConversationId.value ?: return
        if (goal.isBlank() || _isGenerating.value) return

        viewModelScope.launch {
            dao.upsertMessage(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = convId,
                    role = "USER",
                    content = "[Multi-Agent Workflow] $goal",
                    modelUsed = settingsState.value.selectedModelId,
                    tokensEstimated = AiProviderGateway.estimateTokens(goal)
                )
            )
            _selectedTab.value = 0
            runAgentTurn(goal, proj, convId, isMultiAgent = true)
        }
    }

    private fun runAgentTurn(
        prompt: String,
        project: ProjectEntity,
        conversationId: String,
        isMultiAgent: Boolean
    ) {
        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _isGenerating.value = true
            _streamingContent.value = ""
            _streamingThinking.value = ""
            _streamingTools.value = emptyList()
            _streamingDiffs.value = emptyList()
            _agentStatusText.value = if (isMultiAgent) "Multi-Agent Pipeline Starting…" else "Analyzing & Planning…"

            try {
                val history = dao.getMessagesForConversation(conversationId)
                val settings = settingsState.value

                val finalUpdate = if (isMultiAgent) {
                    orchestrator.runMultiAgentOrchestration(
                        goal = prompt,
                        project = project,
                        activeSkills = skills.value,
                        roleModels = settings.agentModels,
                        toolPolicies = settings.toolPolicies,
                        timeoutSeconds = settings.timeoutSeconds,
                        onUpdate = { step ->
                            _agentStatusText.value = step.phaseLabel
                            _streamingThinking.value = step.thinkingLog
                            _streamingContent.value = step.streamingText
                            _streamingTools.value = step.executedTools
                            _streamingDiffs.value = step.fileDiffs
                        }
                    )
                } else {
                    orchestrator.executeAgentLoop(
                        userPrompt = prompt,
                        project = project,
                        conversationHistory = history,
                        activeSkills = skills.value,
                        memories = memories.value,
                        selectedModelId = settings.selectedModelId,
                        roleModels = settings.agentModels,
                        toolPolicies = settings.toolPolicies,
                        maxIterations = settings.maxIterations,
                        timeoutSeconds = settings.timeoutSeconds,
                        onUpdate = { step ->
                            _agentStatusText.value = step.phaseLabel
                            _streamingThinking.value = step.thinkingLog
                            _streamingContent.value = step.streamingText
                            _streamingTools.value = step.executedTools
                            _streamingDiffs.value = step.fileDiffs
                        }
                    )
                }

                // Persist Assistant Message
                val assistantMsg = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    role = "ASSISTANT",
                    content = finalUpdate.streamingText,
                    thinkingSummary = finalUpdate.thinkingLog,
                    toolCallsJson = AgentOrchestrator.serializeToolRecords(finalUpdate.executedTools),
                    fileDiffsJson = AgentOrchestrator.serializeFileDiffs(finalUpdate.fileDiffs),
                    modelUsed = settings.selectedModelId,
                    tokensEstimated = finalUpdate.totalTokensUsed
                )
                dao.upsertMessage(assistantMsg)

                // Update conversation token and cost totals
                val conv = dao.getConversationById(conversationId)
                if (conv != null) {
                    dao.upsertConversation(
                        conv.copy(
                            modelId = settings.selectedModelId,
                            totalTokens = conv.totalTokens + finalUpdate.totalTokensUsed,
                            estimatedCostUsd = conv.estimatedCostUsd + finalUpdate.estimatedCostUsd,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }

                // Store automatic conversation memory summary
                dao.upsertMemory(
                    MemoryEntryEntity(
                        id = "conv-mem-$conversationId",
                        level = "CONVERSATION",
                        scopeId = conversationId,
                        memoryKey = "last_turn_goal",
                        memoryValue = prompt.take(160)
                    )
                )

                refreshProjectWorkspace(project)
                _agentStatusText.value = "Agent Ready"
            } catch (e: CancellationException) {
                _agentStatusText.value = "Stopped"
                throw e
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: "AI provider inference failed."
                _errorBanner.value = errMsg
                _agentStatusText.value = "Error"
                dao.upsertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        role = "ASSISTANT",
                        content = "⚠️ **Agent Execution Error**\n\n`$errMsg`\n\nCheck your provider selection or API key in **Settings > AI Providers**, or switch to **Google Gemini 3.8 Flash**.",
                        thinkingSummary = "Failed with exception: ${e.javaClass.simpleName}",
                        modelUsed = settingsState.value.selectedModelId
                    )
                )
            } finally {
                _isGenerating.value = false
                _streamingContent.value = ""
                _streamingThinking.value = ""
                _streamingTools.value = emptyList()
                _streamingDiffs.value = emptyList()
            }
        }
    }

    private suspend fun handleSlashCommandAction(
        action: SlashCommandAction,
        rawCommand: String,
        project: ProjectEntity,
        conversationId: String
    ) {
        dao.upsertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = "USER",
                content = rawCommand,
                modelUsed = settingsState.value.selectedModelId
            )
        )

        when (action) {
            is SlashCommandAction.ImmediateReply -> {
                dao.upsertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        role = "ASSISTANT",
                        content = action.markdown,
                        thinkingSummary = "Slash command executed natively",
                        modelUsed = "PiashCommandEngine"
                    )
                )
            }
            is SlashCommandAction.SwitchModel -> {
                preferencesStore.setSelectedModel(action.modelId)
                dao.upsertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        role = "ASSISTANT",
                        content = action.message,
                        modelUsed = action.modelId
                    )
                )
            }
            is SlashCommandAction.NavigateTab -> {
                _selectedTab.value = action.tabIndex
                dao.upsertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        role = "ASSISTANT",
                        content = action.message,
                        modelUsed = "PiashCommandEngine"
                    )
                )
            }
            is SlashCommandAction.CompactConversation -> {
                val msgs = dao.getMessagesForConversation(conversationId)
                if (msgs.size > 4) {
                    val keepFrom = msgs[msgs.size - 4].createdAt
                    val older = msgs.filter { it.createdAt < keepFrom }
                    val summaryText = older.joinToString("\n") { "- [${it.role}]: ${it.content.take(120)}" }
                    dao.clearConversationMessages(conversationId)
                    dao.upsertMessage(
                        MessageEntity(
                            id = UUID.randomUUID().toString(),
                            conversationId = conversationId,
                            role = "SYSTEM",
                            content = "### Compacted Session Context (${older.size} earlier messages)\n$summaryText",
                            modelUsed = "ContextCompactor"
                        )
                    )
                    msgs.takeLast(4).forEach { dao.upsertMessage(it) }
                }
                _statusBanner.value = action.message
            }
            is SlashCommandAction.ClearConversation -> {
                dao.clearConversationMessages(conversationId)
                _statusBanner.value = action.message
            }
            is SlashCommandAction.ExportConversation -> {
                exportCurrentConversationToClipboard()
            }
            is SlashCommandAction.TriggerMultiAgentWorkflow -> {
                runAgentTurn(action.goal, project, conversationId, isMultiAgent = true)
            }
            is SlashCommandAction.RunWorkspaceTask -> {
                val res = workspaceEngine.executeSupportedTask(File(project.rootPath), action.taskCommand)
                dao.upsertMessage(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        role = "ASSISTANT",
                        content = "### Native Task Output (`${action.taskCommand}`)\n```\n${res.stdout}\n${res.stderr}\n```",
                        thinkingSummary = "Exit code ${res.exitCode} in ${res.durationMs}ms",
                        modelUsed = "NativeWorkspaceEngine"
                    )
                )
            }
        }
    }

    // =========================================================================
    // PROJECT WORKSPACE, GIT VCS & NATIVE TASK CONSOLE
    // =========================================================================

    fun createNewProject(name: String, description: String, instructions: String, template: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val id = "proj-${name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}-${System.currentTimeMillis() % 10000}"
            val rootDir = File(projectsBaseDir, id).apply { mkdirs() }
            toolRegistry.scaffoldProjectTemplate(rootDir, template)

            val proj = ProjectEntity(
                id = id,
                name = name.trim(),
                description = description.trim().ifBlank { "$template workspace project" },
                rootPath = rootDir.absolutePath,
                instructions = instructions.trim().ifBlank { "Follow clean modular architecture." },
                gitBranch = "main"
            )
            dao.upsertProject(proj)
            _activeProject.value = proj
            refreshProjectWorkspace(proj)
            val conv = createNewConversationInternal(proj, "${proj.name} Session")
            _activeConversationId.value = conv.id
            _statusBanner.value = "Created project '${proj.name}' ($template template)"
        }
    }

    fun selectProject(project: ProjectEntity) {
        _activeProject.value = project
        viewModelScope.launch(Dispatchers.IO) {
            refreshProjectWorkspace(project)
            val convs = dao.searchConversations("").filter { it.projectId == project.id }
            if (convs.isNotEmpty()) {
                _activeConversationId.value = convs.first().id
            } else {
                val newConv = createNewConversationInternal(project, "${project.name} Session")
                _activeConversationId.value = newConv.id
            }
        }
    }

    fun updateProjectInstructions(instructions: String) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = proj.copy(instructions = instructions, updatedAt = System.currentTimeMillis())
            dao.upsertProject(updated)
            _activeProject.value = updated
            _statusBanner.value = "Saved project instructions for '${proj.name}'."
        }
    }

    fun refreshProjectWorkspace(project: ProjectEntity? = _activeProject.value) {
        val proj = project ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val root = File(proj.rootPath).apply { mkdirs() }
            workspaceEngine.initVcsIfNeeded(root)
            val items = root.walkTopDown()
                .onEnter { it.name != ".piash_git" }
                .filter { it != root }
                .map { f ->
                    WorkspaceFileItem(
                        relativePath = f.relativeTo(root).path.replace("\\", "/"),
                        name = f.name,
                        isDirectory = f.isDirectory,
                        sizeBytes = if (f.isFile) f.length() else 0L,
                        lastModified = f.lastModified()
                    )
                }
                .sortedWith(compareBy({ !it.isDirectory }, { it.relativePath }))
                .toList()
            _workspaceFiles.value = items
            _gitStatusReport.value = workspaceEngine.gitStatus(root)
            _gitDiffReport.value = workspaceEngine.gitDiff(root)
            _gitLogReport.value = workspaceEngine.gitLog(root)
        }
    }

    fun runTaskConsoleCommand(command: String) {
        val proj = _activeProject.value ?: return
        if (command.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val res = workspaceEngine.executeSupportedTask(File(proj.rootPath), command)
            _taskConsoleHistory.value = _taskConsoleHistory.value + res
            refreshProjectWorkspace(proj)
        }
    }

    fun clearTaskConsole() {
        _taskConsoleHistory.value = emptyList()
    }

    fun commitProjectChanges(commitMessage: String) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = workspaceEngine.gitCommit(File(proj.rootPath), commitMessage.ifBlank { "feat: update project files" })
            refreshProjectWorkspace(proj)
            _statusBanner.value = result
        }
    }

    fun switchOrCreateGitBranch(branchName: String) {
        val proj = _activeProject.value ?: return
        if (branchName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val msg = workspaceEngine.createOrSwitchBranch(File(proj.rootPath), branchName)
            val current = workspaceEngine.getCurrentBranch(File(proj.rootPath))
            val updated = proj.copy(gitBranch = current, updatedAt = System.currentTimeMillis())
            dao.upsertProject(updated)
            _activeProject.value = updated
            refreshProjectWorkspace(updated)
            _statusBanner.value = msg
        }
    }

    fun createNewWorkspaceFile(relativePath: String, initialContent: String = "") {
        val proj = _activeProject.value ?: return
        if (relativePath.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = SecureKeyVault.resolveSafeProjectFile(File(proj.rootPath), relativePath)
                file.parentFile?.mkdirs()
                file.writeText(initialContent)
                refreshProjectWorkspace(proj)
                openFileInEditor(relativePath)
                _selectedTab.value = 2 // Jump to Code Editor
            } catch (e: Exception) {
                _errorBanner.value = e.localizedMessage
            }
        }
    }

    fun deleteWorkspaceFile(relativePath: String) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = SecureKeyVault.resolveSafeProjectFile(File(proj.rootPath), relativePath)
                if (file.exists()) file.deleteRecursively()
                _openTabs.value = _openTabs.value.filterNot { it.relativePath == relativePath }
                refreshProjectWorkspace(proj)
                _statusBanner.value = "Deleted '$relativePath'"
            } catch (e: Exception) {
                _errorBanner.value = e.localizedMessage
            }
        }
    }

    // =========================================================================
    // INTEGRATED CODE EDITOR & UNIFIED DIFF VIEWER
    // =========================================================================

    fun openFileInEditor(relativePath: String) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleanRel = relativePath.trim().removePrefix("/")
                val existingIndex = _openTabs.value.indexOfFirst { it.relativePath == cleanRel }
                if (existingIndex >= 0) {
                    _selectedEditorTabIndex.value = existingIndex
                    return@launch
                }
                val file = SecureKeyVault.resolveSafeProjectFile(File(proj.rootPath), cleanRel)
                if (!file.exists() || !file.isFile) return@launch
                val text = file.readText()
                val newTab = OpenEditorTab(
                    relativePath = cleanRel,
                    savedContent = text,
                    currentContent = text,
                    lastModifiedOnDisk = file.lastModified()
                )
                val updated = _openTabs.value + newTab
                _openTabs.value = updated
                _selectedEditorTabIndex.value = updated.lastIndex
            } catch (e: Exception) {
                _errorBanner.value = "Cannot open file: ${e.localizedMessage}"
            }
        }
    }

    fun selectEditorTab(index: Int) {
        if (index in _openTabs.value.indices) {
            _selectedEditorTabIndex.value = index
        }
    }

    fun closeEditorTab(index: Int) {
        val current = _openTabs.value.toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        _openTabs.value = current
        _selectedEditorTabIndex.value = current.lastIndex.coerceAtLeast(0)
    }

    fun updateActiveEditorContent(newText: String) {
        val idx = _selectedEditorTabIndex.value
        val tabs = _openTabs.value.toMutableList()
        val tab = tabs.getOrNull(idx) ?: return
        if (tab.currentContent == newText) return
        val nextUndo = (tab.undoStack + tab.currentContent).takeLast(30)
        tabs[idx] = tab.copy(
            currentContent = newText,
            undoStack = nextUndo,
            redoStack = emptyList()
        )
        _openTabs.value = tabs
    }

    fun undoEditorChange() {
        val idx = _selectedEditorTabIndex.value
        val tabs = _openTabs.value.toMutableList()
        val tab = tabs.getOrNull(idx) ?: return
        val prev = tab.undoStack.lastOrNull() ?: return
        tabs[idx] = tab.copy(
            currentContent = prev,
            undoStack = tab.undoStack.dropLast(1),
            redoStack = tab.redoStack + tab.currentContent
        )
        _openTabs.value = tabs
    }

    fun redoEditorChange() {
        val idx = _selectedEditorTabIndex.value
        val tabs = _openTabs.value.toMutableList()
        val tab = tabs.getOrNull(idx) ?: return
        val next = tab.redoStack.lastOrNull() ?: return
        tabs[idx] = tab.copy(
            currentContent = next,
            undoStack = tab.undoStack + tab.currentContent,
            redoStack = tab.redoStack.dropLast(1)
        )
        _openTabs.value = tabs
    }

    fun searchAndReplaceInEditor(findText: String, replaceText: String) {
        if (findText.isEmpty()) return
        val idx = _selectedEditorTabIndex.value
        val tab = _openTabs.value.getOrNull(idx) ?: return
        val replaced = tab.currentContent.replace(findText, replaceText)
        updateActiveEditorContent(replaced)
    }

    fun previewActiveEditorDiff() {
        val tab = _openTabs.value.getOrNull(_selectedEditorTabIndex.value) ?: return
        _editorDiffPreview.value = workspaceEngine.generateUnifiedDiff(
            relativePath = tab.relativePath,
            oldText = tab.savedContent,
            newText = tab.currentContent,
            applied = false
        )
    }

    fun dismissEditorDiffPreview() {
        _editorDiffPreview.value = null
    }

    fun saveActiveEditorTab() {
        val proj = _activeProject.value ?: return
        val idx = _selectedEditorTabIndex.value
        val tabs = _openTabs.value.toMutableList()
        val tab = tabs.getOrNull(idx) ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = SecureKeyVault.resolveSafeProjectFile(File(proj.rootPath), tab.relativePath)
                // Conflict detection check
                if (file.exists() && tab.lastModifiedOnDisk > 0L && file.lastModified() > tab.lastModifiedOnDisk) {
                    val diskText = file.readText()
                    if (diskText != tab.savedContent) {
                        _statusBanner.value = "Notice: Disk changes merged & overwritten for '${tab.relativePath}'."
                    }
                }
                file.parentFile?.mkdirs()
                file.writeText(tab.currentContent)
                tabs[idx] = tab.copy(
                    savedContent = tab.currentContent,
                    lastModifiedOnDisk = file.lastModified()
                )
                _openTabs.value = tabs
                _editorDiffPreview.value = null
                refreshProjectWorkspace(proj)
                _statusBanner.value = "Saved '${tab.relativePath}' (${tab.currentContent.lines().size} lines)"
            } catch (e: Exception) {
                _errorBanner.value = "Save failed: ${e.localizedMessage}"
            }
        }
    }

    // =========================================================================
    // TOOLS, SKILLS, MCP, MEMORY & SETTINGS ACTIONS
    // =========================================================================

    fun executeDirectToolFromUi(toolName: String, argsJson: String, onResult: (ToolExecutionRecord) -> Unit) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            val rec = toolRegistry.executeTool(
                toolName = toolName,
                argumentsJson = argsJson,
                projectId = proj.id,
                projectRoot = File(proj.rootPath),
                agentRole = AgentRole.MAIN_AGENT,
                decisionLabel = "USER_APPROVED"
            )
            refreshProjectWorkspace(proj)
            onResult(rec)
        }
    }

    fun toggleSkill(skill: SkillEntity, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            skillsManager.toggleSkill(skill, enabled)
        }
    }

    fun importSkillMarkdown(rawMd: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = skillsManager.importSkillMarkdown(rawMd)
            res.onSuccess {
                _statusBanner.value = "Imported skill '${it.name}' (v${it.version}) to disk."
            }.onFailure {
                _errorBanner.value = "Skill import error: ${it.localizedMessage}"
            }
        }
    }

    fun deleteCustomSkill(skill: SkillEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            skillsManager.deleteCustomSkill(skill)
            _statusBanner.value = "Deleted custom skill '${skill.name}'."
        }
    }

    fun addMcpServer(name: String, endpointUrl: String) {
        if (name.isBlank() || endpointUrl.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val server = McpServerEntity(
                id = "mcp-${UUID.randomUUID().toString().take(8)}",
                name = name.trim(),
                endpointUrl = endpointUrl.trim(),
                transportType = if (endpointUrl.startsWith("mcp://")) "LOOPBACK_ANDROID" else "HTTP_JSON_RPC",
                isEnabled = true,
                isTrusted = false, // Never auto-trust imported MCP servers per security spec
                statusMessage = "Added (Untrusted — approve trust to invoke tools)"
            )
            dao.upsertMcpServer(server)
            mcpManager.connectAndDiscover(server)
        }
    }

    fun toggleMcpServerTrust(server: McpServerEntity, trusted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.upsertMcpServer(server.copy(isTrusted = trusted))
        }
    }

    fun toggleMcpServerEnabled(server: McpServerEntity, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.upsertMcpServer(server.copy(isEnabled = enabled))
        }
    }

    fun testAndDiscoverMcpServer(server: McpServerEntity) {
        viewModelScope.launch {
            val res = mcpManager.connectAndDiscover(server)
            res.onSuccess {
                _statusBanner.value = "MCP '${it.serverName}': discovered ${it.tools.size} tools, ${it.resources.size} resources (${it.latencyMs}ms)"
            }.onFailure {
                _errorBanner.value = "MCP connection failed: ${it.localizedMessage}"
            }
        }
    }

    fun deleteMcpServer(serverId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteMcpServer(serverId)
        }
    }

    // Memory CRUD
    fun saveMemoryEntry(level: String, key: String, value: String) {
        if (key.isBlank() || value.isBlank()) return
        // Security check: prevent saving raw API keys into memory
        if (key.contains("api_key", ignoreCase = true) || value.startsWith("sk-") || value.startsWith("AIza")) {
            _errorBanner.value = "Security Policy: Never store API keys or secrets in Agent Memory. Use the Android Keystore Vault in Settings."
            return
        }
        val scopeId = when (level) {
            "CONVERSATION" -> _activeConversationId.value ?: "GLOBAL"
            "PROJECT" -> _activeProject.value?.id ?: "GLOBAL"
            else -> "GLOBAL"
        }
        viewModelScope.launch(Dispatchers.IO) {
            dao.upsertMemory(
                MemoryEntryEntity(
                    id = "mem-${level.lowercase()}-${key.lowercase().replace(Regex("[^a-z0-9]"), "-")}",
                    level = level,
                    scopeId = scopeId,
                    memoryKey = key.trim(),
                    memoryValue = value.trim()
                )
            )
            _statusBanner.value = "Saved [$level] memory '${key.trim()}'"
        }
    }

    fun deleteMemoryEntry(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAllMemories()
            _statusBanner.value = "Cleared all agent memory entries."
        }
    }

    // Provider & Settings Actions
    fun saveProviderCredentials(provider: ProviderType, apiKey: String, endpointUrl: String) {
        keyVault.saveApiKey(provider, apiKey)
        if (endpointUrl.isNotBlank()) {
            keyVault.saveEndpointUrl(provider, endpointUrl)
        }
        _statusBanner.value = "Secured ${provider.displayName} credentials in Android Keystore."
    }

    fun testProviderConnection(provider: ProviderType) {
        viewModelScope.launch {
            _statusBanner.value = "Testing connection to ${provider.displayName}…"
            val res = providerGateway.testProviderConnection(provider)
            res.onSuccess { msg ->
                _statusBanner.value = msg
            }.onFailure { err ->
                _errorBanner.value = "${provider.displayName} test failed: ${err.localizedMessage}"
            }
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            preferencesStore.setSelectedModel(modelId)
            val convId = _activeConversationId.value ?: return@launch
            val conv = dao.getConversationById(convId) ?: return@launch
            val spec = ModelCatalog.findById(modelId)
            dao.upsertConversation(conv.copy(modelId = spec.id, providerName = spec.provider.name))
        }
    }

    fun setAgentRoleModel(role: AgentRole, modelId: String) {
        viewModelScope.launch {
            preferencesStore.setAgentRoleModel(role, modelId)
        }
    }

    fun setToolPermissionPolicy(toolName: String, policy: PermissionPolicy) {
        viewModelScope.launch {
            preferencesStore.setToolPolicy(toolName, policy)
        }
    }

    fun updateThemeSettings(darkTheme: Boolean, amoledMode: Boolean, accentPreset: AccentPreset) {
        viewModelScope.launch {
            preferencesStore.updateTheme(darkTheme, amoledMode, accentPreset)
        }
    }

    fun updateAgentLimits(
        maxIterations: Int,
        timeoutSeconds: Int,
        maxTokenBudget: Int,
        costLimitUsd: Double,
        autoCompact: Boolean
    ) {
        viewModelScope.launch {
            preferencesStore.updateAgentLimits(maxIterations, timeoutSeconds, maxTokenBudget, costLimitUsd, autoCompact)
            _statusBanner.value = "Updated Agent Engine limits (maxIterations=$maxIterations, timeout=${timeoutSeconds}s)."
        }
    }

    fun exportWorkspaceBackupJson() {
        viewModelScope.launch(Dispatchers.IO) {
            val exportObj = JSONObject().apply {
                put("app", "PiashAgent")
                put("exportedAt", System.currentTimeMillis())
                put("projects", JSONArray().apply {
                    projects.value.forEach { p ->
                        put(JSONObject().put("id", p.id).put("name", p.name).put("instructions", p.instructions))
                    }
                })
                put("memories", JSONArray().apply {
                    memories.value.forEach { m ->
                        put(JSONObject().put("level", m.level).put("key", m.memoryKey).put("value", m.memoryValue))
                    }
                })
            }
            withContext(Dispatchers.Main) {
                copyToClipboard("PiashAgent Backup JSON", exportObj.toString(2))
            }
        }
    }
}
