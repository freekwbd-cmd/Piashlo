package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PiashDao {

    // Projects
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    suspend fun getAllProjects(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String)

    // Conversations
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun observeProjectConversations(projectId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    suspend fun searchConversations(query: String): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    // Messages
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversationMessages(conversationId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt >= :fromTimestamp")
    suspend fun deleteMessagesFromTimestamp(conversationId: String, fromTimestamp: Long)

    // Memory System (3 levels: CONVERSATION, PROJECT, USER_PREFERENCE)
    @Query("SELECT * FROM memory_entries ORDER BY updatedAt DESC")
    fun observeAllMemories(): Flow<List<MemoryEntryEntity>>

    @Query("SELECT * FROM memory_entries WHERE level = :level AND scopeId = :scopeId ORDER BY updatedAt DESC")
    suspend fun getMemoriesForScope(level: String, scopeId: String): List<MemoryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(entry: MemoryEntryEntity)

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("DELETE FROM memory_entries WHERE level = :level")
    suspend fun clearMemoriesByLevel(level: String)

    @Query("DELETE FROM memory_entries")
    suspend fun clearAllMemories()

    // Skills
    @Query("SELECT * FROM skills ORDER BY isBuiltIn DESC, name ASC")
    fun observeSkills(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skills ORDER BY isBuiltIn DESC, name ASC")
    suspend fun getAllSkills(): List<SkillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkill(skill: SkillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkills(skills: List<SkillEntity>)

    @Query("DELETE FROM skills WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomSkill(id: String)

    // MCP Servers
    @Query("SELECT * FROM mcp_servers ORDER BY name ASC")
    fun observeMcpServers(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers ORDER BY name ASC")
    suspend fun getAllMcpServers(): List<McpServerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMcpServer(server: McpServerEntity)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun deleteMcpServer(id: String)

    // Security Audit Logs
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAuditLogs()
}
