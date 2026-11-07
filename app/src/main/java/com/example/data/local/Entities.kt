package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "projects",
    indices = [Index(value = ["updatedAt"])]
)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val rootPath: String,
    val instructions: String,
    val gitBranch: String = "main",
    val activeSkillsCsv: String = "android-developer,kotlin-developer,code-reviewer",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["projectId"]), Index(value = ["updatedAt"])]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val parentConversationId: String? = null,
    val modelId: String = "gemini-3.8-flash",
    val providerName: String = "GOOGLE_GEMINI",
    val totalTokens: Int = 0,
    val estimatedCostUsd: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId", "createdAt"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // USER, ASSISTANT, SYSTEM, TOOL
    val content: String,
    val thinkingSummary: String = "",
    val toolCallsJson: String = "[]",
    val fileDiffsJson: String = "[]",
    val attachmentsCsv: String = "",
    val modelUsed: String = "gemini-3.8-flash",
    val tokensEstimated: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "memory_entries",
    indices = [Index(value = ["level", "scopeId"])]
)
data class MemoryEntryEntity(
    @PrimaryKey val id: String,
    val level: String, // CONVERSATION, PROJECT, USER_PREFERENCE
    val scopeId: String, // conversationId, projectId, or "GLOBAL"
    val memoryKey: String,
    val memoryValue: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "skills",
    indices = [Index(value = ["isEnabled"])]
)
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val version: String,
    val instructions: String,
    val requiredToolsCsv: String,
    val permissionsCsv: String,
    val referencesText: String,
    val isBuiltIn: Boolean,
    val isEnabled: Boolean,
    val filePath: String,
    val rawSkillMd: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val endpointUrl: String,
    val transportType: String, // HTTP_JSON_RPC, LOOPBACK_ANDROID
    val isEnabled: Boolean = true,
    val isTrusted: Boolean = false,
    val discoveredToolsJson: String = "[]",
    val discoveredResourcesJson: String = "[]",
    val discoveredPromptsJson: String = "[]",
    val statusMessage: String = "Disconnected",
    val lastCheckedAt: Long = 0L
)

@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["timestamp"])]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String,
    val agentRole: String,
    val projectId: String,
    val argumentsSummary: String,
    val decision: String, // AUTO_ALLOWED, USER_APPROVED, USER_DENIED, FAILED
    val resultSummary: String,
    val durationMs: Long
)
