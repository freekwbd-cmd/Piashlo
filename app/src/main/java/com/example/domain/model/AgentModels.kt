package com.example.domain.model

enum class ProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val requiresApiKey: Boolean,
    val supportsStreaming: Boolean,
    val badge: String
) {
    GOOGLE_GEMINI(
        displayName = "Google Gemini (Firebase AI Logic)",
        defaultBaseUrl = "firebase://ai-logic/gemini",
        requiresApiKey = false,
        supportsStreaming = true,
        badge = "NATIVE"
    ),
    ANTHROPIC_CLAUDE(
        displayName = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        requiresApiKey = true,
        supportsStreaming = true,
        badge = "CLAUDE"
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        requiresApiKey = true,
        supportsStreaming = true,
        badge = "OPENAI"
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        requiresApiKey = true,
        supportsStreaming = true,
        badge = "DEEPSEEK"
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        requiresApiKey = true,
        supportsStreaming = true,
        badge = "ROUTER"
    ),
    OLLAMA(
        displayName = "Ollama (Local / LAN Endpoint)",
        defaultBaseUrl = "http://127.0.0.1:11434/api",
        requiresApiKey = false,
        supportsStreaming = true,
        badge = "OLLAMA"
    ),
    OPENAI_COMPATIBLE(
        displayName = "OpenAI-Compatible Endpoint",
        defaultBaseUrl = "https://api.together.xyz/v1",
        requiresApiKey = true,
        supportsStreaming = true,
        badge = "CUSTOM"
    )
}

data class AiModelSpec(
    val id: String,
    val displayName: String,
    val provider: ProviderType,
    val contextWindowTokens: Int,
    val supportsTools: Boolean = true,
    val supportsVision: Boolean = true,
    val inputCostPerMillion: Double = 0.15,
    val outputCostPerMillion: Double = 0.60,
    val description: String = ""
)

object ModelCatalog {
    val models: List<AiModelSpec> = listOf(
        // Provisioned Firebase AI Logic Gemini 3.x / 4 models
        AiModelSpec(
            id = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash",
            provider = ProviderType.GOOGLE_GEMINI,
            contextWindowTokens = 1_048_576,
            supportsTools = true,
            supportsVision = true,
            inputCostPerMillion = 0.10,
            outputCostPerMillion = 0.40,
            description = "Default frontier-speed agent model for coding, planning & tools"
        ),
        AiModelSpec(
            id = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash-Lite",
            provider = ProviderType.GOOGLE_GEMINI,
            contextWindowTokens = 1_048_576,
            supportsTools = true,
            supportsVision = true,
            inputCostPerMillion = 0.04,
            outputCostPerMillion = 0.15,
            description = "Ultra-fast sub-agent model for quick research & context compaction"
        ),
        AiModelSpec(
            id = "gemini-4-argon",
            displayName = "Gemini 4 Argon",
            provider = ProviderType.GOOGLE_GEMINI,
            contextWindowTokens = 2_097_152,
            supportsTools = true,
            supportsVision = true,
            inputCostPerMillion = 1.25,
            outputCostPerMillion = 5.00,
            description = "Deep architectural reasoning, complex refactoring & multi-agent verification"
        ),
        // Anthropic Claude models
        AiModelSpec(
            id = "claude-sonnet-4-20250514",
            displayName = "Claude Sonnet 4",
            provider = ProviderType.ANTHROPIC_CLAUDE,
            contextWindowTokens = 200_000,
            supportsTools = true,
            supportsVision = true,
            inputCostPerMillion = 3.00,
            outputCostPerMillion = 15.00,
            description = "High-precision software engineering & code synthesis"
        ),
        AiModelSpec(
            id = "claude-3-5-haiku-latest",
            displayName = "Claude 3.5 Haiku",
            provider = ProviderType.ANTHROPIC_CLAUDE,
            contextWindowTokens = 200_000,
            supportsTools = true,
            supportsVision = false,
            inputCostPerMillion = 0.80,
            outputCostPerMillion = 4.00,
            description = "Fast coding assistant & review sub-agent"
        ),
        // OpenAI models
        AiModelSpec(
            id = "gpt-4.1",
            displayName = "GPT-4.1",
            provider = ProviderType.OPENAI,
            contextWindowTokens = 128_000,
            supportsTools = true,
            supportsVision = true,
            inputCostPerMillion = 2.00,
            outputCostPerMillion = 8.00,
            description = "Flagship OpenAI coding and tool execution model"
        ),
        AiModelSpec(
            id = "o3-mini",
            displayName = "OpenAI o3-mini",
            provider = ProviderType.OPENAI,
            contextWindowTokens = 128_000,
            supportsTools = true,
            supportsVision = false,
            inputCostPerMillion = 1.10,
            outputCostPerMillion = 4.40,
            description = "Fast STEM and algorithmic reasoning model"
        ),
        // DeepSeek models
        AiModelSpec(
            id = "deepseek-chat",
            displayName = "DeepSeek V3 Chat",
            provider = ProviderType.DEEPSEEK,
            contextWindowTokens = 64_000,
            supportsTools = true,
            supportsVision = false,
            inputCostPerMillion = 0.27,
            outputCostPerMillion = 1.10,
            description = "Efficient code generation & tool calling"
        ),
        AiModelSpec(
            id = "deepseek-reasoner",
            displayName = "DeepSeek R1 Reasoner",
            provider = ProviderType.DEEPSEEK,
            contextWindowTokens = 64_000,
            supportsTools = true,
            supportsVision = false,
            inputCostPerMillion = 0.55,
            outputCostPerMillion = 2.19,
            description = "Chain-of-thought reasoning specialist"
        ),
        // OpenRouter
        AiModelSpec(
            id = "anthropic/claude-3.7-sonnet",
            displayName = "OpenRouter: Claude 3.7 Sonnet",
            provider = ProviderType.OPENROUTER,
            contextWindowTokens = 200_000,
            supportsTools = true,
            supportsVision = true,
            inputCostPerMillion = 3.00,
            outputCostPerMillion = 15.00,
            description = "Unified OpenRouter gateway model"
        ),
        // Ollama
        AiModelSpec(
            id = "qwen2.5-coder:7b",
            displayName = "Ollama: Qwen 2.5 Coder",
            provider = ProviderType.OLLAMA,
            contextWindowTokens = 32_768,
            supportsTools = true,
            supportsVision = false,
            inputCostPerMillion = 0.0,
            outputCostPerMillion = 0.0,
            description = "Self-hosted Ollama LAN model"
        ),
        // Custom OpenAI Compatible
        AiModelSpec(
            id = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
            displayName = "Compatible: Llama 3.3 70B",
            provider = ProviderType.OPENAI_COMPATIBLE,
            contextWindowTokens = 128_000,
            supportsTools = true,
            supportsVision = false,
            inputCostPerMillion = 0.60,
            outputCostPerMillion = 0.60,
            description = "Custom OpenAI-compatible v1/chat/completions endpoint"
        )
    )

    fun findById(id: String): AiModelSpec =
        models.firstOrNull { it.id == id } ?: models.first()
}

enum class AgentRole(
    val title: String,
    val badge: String,
    val description: String,
    val defaultModelId: String
) {
    MAIN_AGENT(
        title = "Main Coordinator Agent",
        badge = "MAIN",
        description = "Orchestrates user requests, synthesizes results, and manages tool permissions.",
        defaultModelId = "gemini-3.8-flash"
    ),
    PLANNER_AGENT(
        title = "Planner Agent",
        badge = "PLAN",
        description = "Decomposes complex goals into structured step-by-step execution plans.",
        defaultModelId = "gemini-3.8-flash"
    ),
    RESEARCH_AGENT(
        title = "Research Agent",
        badge = "RSRCH",
        description = "Searches files, code references, documentation, and web sources.",
        defaultModelId = "gemini-3.5-flash-lite"
    ),
    CODING_AGENT(
        title = "Coding Agent",
        badge = "CODE",
        description = "Authors, refactors, and patches project source files with diff previews.",
        defaultModelId = "gemini-3.8-flash"
    ),
    REVIEW_AGENT(
        title = "Review Agent",
        badge = "REVW",
        description = "Audits code diffs for bugs, security risks, and style compliance.",
        defaultModelId = "gemini-3.8-flash"
    ),
    TESTING_AGENT(
        title = "Testing Agent",
        badge = "TEST",
        description = "Runs syntax diagnostics, static verification, and task checks.",
        defaultModelId = "gemini-3.5-flash-lite"
    )
}

enum class ToolCategory(val label: String) {
    FILE("File System"),
    CODE("Code Intelligence"),
    PROJECT("Project & Build"),
    GIT("Git & VCS"),
    WEB("Web & Docs"),
    ANDROID("Android Native"),
    MCP("MCP Protocol")
}

enum class PermissionPolicy(val label: String) {
    AUTO_ALLOW("Auto-Allow"),
    ASK_APPROVAL("Ask Approval"),
    DENY("Always Deny")
}

data class ToolSchema(
    val name: String,
    val category: ToolCategory,
    val description: String,
    val parametersDescription: String,
    val defaultPolicy: PermissionPolicy,
    val isDestructive: Boolean = false
)

data class ToolExecutionRecord(
    val id: String,
    val toolName: String,
    val category: ToolCategory,
    val agentRole: AgentRole,
    val argumentsJson: String,
    val status: String, // RUNNING, COMPLETED, FAILED, DENIED
    val output: String,
    val durationMs: Long,
    val fileDiff: FileDiffPreview? = null
)

data class FileDiffPreview(
    val relativePath: String,
    val oldContent: String,
    val newContent: String,
    val unifiedDiff: String,
    val additions: Int,
    val deletions: Int,
    val applied: Boolean = true
)

data class PendingPermissionRequest(
    val id: String,
    val toolName: String,
    val category: ToolCategory,
    val agentRole: AgentRole,
    val argumentsJson: String,
    val reason: String,
    val diffPreview: FileDiffPreview? = null
)

data class AgentTaskStatus(
    val role: AgentRole,
    val assignedModelId: String,
    val state: String, // IDLE, RUNNING, COMPLETED, ERROR
    val currentStep: String,
    val lockedFiles: List<String> = emptyList(),
    val lastSummary: String = ""
)
