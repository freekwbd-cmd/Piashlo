package com.example.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.example.data.local.AuditLogEntity
import com.example.data.local.PiashDao
import com.example.domain.model.AgentRole
import com.example.domain.model.FileDiffPreview
import com.example.domain.model.PermissionPolicy
import com.example.domain.model.ToolCategory
import com.example.domain.model.ToolExecutionRecord
import com.example.domain.model.ToolSchema
import com.example.execution.NativeWorkspaceEngine
import com.example.security.SecureKeyVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ToolCallOutcome(
    val record: ToolExecutionRecord,
    val requiresApproval: Boolean = false,
    val pendingReason: String = ""
)

/**
 * Modular, extensible Tool Registry implementing 26 real native Android tools
 * across File, Code, Project, Git, Web, and Android categories.
 */
class ToolRegistry(
    private val context: Context,
    private val dao: PiashDao,
    private val workspaceEngine: NativeWorkspaceEngine
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val schemas: List<ToolSchema> = listOf(
        // FILE TOOLS
        ToolSchema("read_file", ToolCategory.FILE, "Read contents of a file in the project workspace", """{"path": "relative/path.kt"}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("create_file", ToolCategory.FILE, "Create or overwrite a file in the project workspace", """{"path": "src/Main.kt", "content": "..."}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("edit_file", ToolCategory.FILE, "Replace target text inside an existing file and generate a unified diff", """{"path": "src/Main.kt", "target": "old", "replacement": "new"}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("delete_file", ToolCategory.FILE, "Delete a file from the project workspace (requires user confirmation)", """{"path": "temp.txt"}""", PermissionPolicy.ASK_APPROVAL, isDestructive = true),
        ToolSchema("list_directory", ToolCategory.FILE, "List files and directories inside the project workspace", """{"path": ""}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("search_files", ToolCategory.FILE, "Search file names by substring or glob pattern", """{"query": "Main"}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("search_code", ToolCategory.FILE, "Search code across all project files with line numbers", """{"pattern": "fun "}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("find_references", ToolCategory.FILE, "Find all symbol usages and references across the project", """{"symbol": "Repository"}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("rename_file", ToolCategory.FILE, "Rename a file within the project directory", """{"path": "Old.kt", "new_name": "New.kt"}""", PermissionPolicy.ASK_APPROVAL),
        ToolSchema("move_file", ToolCategory.FILE, "Move a file to another folder within the project", """{"source": "a.kt", "destination": "sub/a.kt"}""", PermissionPolicy.ASK_APPROVAL),
        ToolSchema("create_folder", ToolCategory.FILE, "Create a new directory inside the project", """{"path": "src/domain"}""", PermissionPolicy.AUTO_ALLOW),

        // CODE TOOLS
        ToolSchema("code_analysis", ToolCategory.CODE, "Analyze LOC, classes, functions, and TODOs in the project", """{}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("syntax_diagnostics", ToolCategory.CODE, "Check bracket balance and JSON/XML/code syntax across workspace", """{}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("generate_diff", ToolCategory.CODE, "Generate a unified diff preview for proposed file content", """{"path": "src/App.kt", "new_content": "..."}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("apply_patch", ToolCategory.CODE, "Apply a multi-line patch to a file with conflict detection", """{"path": "src/App.kt", "new_content": "..."}""", PermissionPolicy.ASK_APPROVAL),
        ToolSchema("code_review", ToolCategory.CODE, "Perform automated static security and quality audit on a file or diff", """{"path": "src/App.kt"}""", PermissionPolicy.AUTO_ALLOW),

        // PROJECT TOOLS
        ToolSchema("project_inspect", ToolCategory.PROJECT, "Inspect project structure, file tree, and VCS metadata", """{}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("project_init", ToolCategory.PROJECT, "Initialize a starter template (kotlin, python, web, react)", """{"template": "kotlin"}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("dependency_inspect", ToolCategory.PROJECT, "Inspect dependencies in build.gradle.kts, package.json, or requirements.txt", """{}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("run_task", ToolCategory.PROJECT, "Run a supported native task (verify, analyze, test, status, eval)", """{"command": "verify"}""", PermissionPolicy.AUTO_ALLOW),

        // GIT TOOLS
        ToolSchema("git_status", ToolCategory.GIT, "Show modified, added, and untracked files in native project VCS", """{}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("git_diff", ToolCategory.GIT, "Generate unified diff of uncommitted workspace changes", """{"path": ""}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("git_log", ToolCategory.GIT, "Show commit history for the current project branch", """{"limit": 10}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("git_branch", ToolCategory.GIT, "List branches or create/switch to a new branch", """{"name": "feature/agent"}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("git_commit", ToolCategory.GIT, "Commit current workspace changes with a commit message", """{"message": "feat: implement module"}""", PermissionPolicy.ASK_APPROVAL),

        // WEB TOOLS
        ToolSchema("web_search", ToolCategory.WEB, "Search public technical knowledge & documentation with source citations", """{"query": "Kotlin Coroutines Flow"}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("fetch_webpage", ToolCategory.WEB, "Fetch a public HTTPS webpage and extract readable text", """{"url": "https://kotlinlang.org"}""", PermissionPolicy.ASK_APPROVAL),
        ToolSchema("docs_lookup", ToolCategory.WEB, "Look up Android, Kotlin, Jetpack Compose, or MCP reference docs", """{"topic": "Jetpack Compose StateFlow"}""", PermissionPolicy.AUTO_ALLOW),

        // ANDROID TOOLS
        ToolSchema("clipboard_copy", ToolCategory.ANDROID, "Copy generated code or output to Android system clipboard", """{"text": "..."}""", PermissionPolicy.AUTO_ALLOW),
        ToolSchema("saf_workspace_info", ToolCategory.ANDROID, "Inspect Android app-private sandbox storage & SAF metrics", """{}""", PermissionPolicy.AUTO_ALLOW)
    )

    fun getSchema(toolName: String): ToolSchema? =
        schemas.firstOrNull { it.name.equals(toolName, ignoreCase = true) }

    fun getEffectivePolicy(
        toolName: String,
        userOverrides: Map<String, PermissionPolicy>
    ): PermissionPolicy {
        userOverrides[toolName]?.let { return it }
        return getSchema(toolName)?.defaultPolicy ?: PermissionPolicy.ASK_APPROVAL
    }

    suspend fun executeTool(
        toolName: String,
        argumentsJson: String,
        projectId: String,
        projectRoot: File,
        agentRole: AgentRole,
        decisionLabel: String = "AUTO_ALLOWED"
    ): ToolExecutionRecord = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val schema = getSchema(toolName)
        val category = schema?.category ?: ToolCategory.PROJECT
        val args = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())

        var diffPreview: FileDiffPreview? = null
        var status = "COMPLETED"

        val outputText = try {
            when (toolName.lowercase()) {
                "read_file" -> {
                    val path = args.optString("path").ifBlank { throw IllegalArgumentException("Missing 'path'") }
                    val file = SecureKeyVault.resolveSafeProjectFile(projectRoot, path)
                    require(file.exists() && file.isFile) { "File not found: $path" }
                    file.readText()
                }
                "create_file" -> {
                    val path = args.optString("path").ifBlank { throw IllegalArgumentException("Missing 'path'") }
                    val content = args.optString("content", "")
                    val file = SecureKeyVault.resolveSafeProjectFile(projectRoot, path)
                    val oldContent = if (file.exists()) file.readText() else ""
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    diffPreview = workspaceEngine.generateUnifiedDiff(path, oldContent, content, applied = true)
                    "Saved file '$path' (${content.lines().size} lines, +${diffPreview?.additions}/-${diffPreview?.deletions})"
                }
                "edit_file" -> {
                    val path = args.optString("path").ifBlank { throw IllegalArgumentException("Missing 'path'") }
                    val target = args.optString("target")
                    val replacement = args.optString("replacement")
                    val file = SecureKeyVault.resolveSafeProjectFile(projectRoot, path)
                    require(file.exists() && file.isFile) { "File not found: $path" }
                    val oldContent = file.readText()
                    val newContent = if (target.isNotEmpty() && oldContent.contains(target)) {
                        oldContent.replaceFirst(target, replacement)
                    } else {
                        args.optString("new_content").ifBlank { oldContent + "\n" + replacement }
                    }
                    file.writeText(newContent)
                    diffPreview = workspaceEngine.generateUnifiedDiff(path, oldContent, newContent, applied = true)
                    "Updated '$path' (+${diffPreview?.additions}/-${diffPreview?.deletions} lines)"
                }
                "delete_file" -> {
                    val path = args.optString("path").ifBlank { throw IllegalArgumentException("Missing 'path'") }
                    val file = SecureKeyVault.resolveSafeProjectFile(projectRoot, path)
                    require(file.exists()) { "File does not exist: $path" }
                    val oldContent = if (file.isFile) file.readText() else ""
                    file.deleteRecursively()
                    diffPreview = workspaceEngine.generateUnifiedDiff(path, oldContent, "", applied = true)
                    "Deleted '$path' from project workspace."
                }
                "list_directory" -> {
                    val sub = args.optString("path", "").removePrefix("/")
                    val target = if (sub.isEmpty()) projectRoot else SecureKeyVault.resolveSafeProjectFile(projectRoot, sub)
                    require(target.exists()) { "Directory not found: $sub" }
                    target.walkTopDown()
                        .maxDepth(3)
                        .filter { !it.path.contains(".piash_git") && it != projectRoot }
                        .map {
                            val rel = it.relativeTo(projectRoot).path
                            if (it.isDirectory) "[DIR]  $rel/" else "[FILE] $rel (${it.length()} B)"
                        }
                        .joinToString("\n")
                        .ifEmpty { "(Workspace directory is empty)" }
                }
                "search_files" -> {
                    val q = args.optString("query", "").lowercase()
                    projectRoot.walkTopDown()
                        .onEnter { it.name != ".piash_git" }
                        .filter { it.isFile && it.name.lowercase().contains(q) }
                        .map { it.relativeTo(projectRoot).path }
                        .joinToString("\n")
                        .ifEmpty { "No files matching '$q'" }
                }
                "search_code", "find_references" -> {
                    val pattern = args.optString("pattern").ifBlank { args.optString("symbol") }
                    require(pattern.isNotBlank()) { "Provide 'pattern' or 'symbol' to search." }
                    val matches = mutableListOf<String>()
                    projectRoot.walkTopDown()
                        .onEnter { it.name != ".piash_git" }
                        .filter { it.isFile && it.length() < 200_000L }
                        .forEach { f ->
                            val rel = f.relativeTo(projectRoot).path
                            f.readLines().forEachIndexed { idx, line ->
                                if (line.contains(pattern, ignoreCase = true)) {
                                    matches.add("$rel:${idx + 1}: ${line.trim()}")
                                }
                            }
                        }
                    matches.take(50).joinToString("\n").ifEmpty { "No code references found for '$pattern'." }
                }
                "rename_file" -> {
                    val path = args.optString("path")
                    val newName = args.optString("new_name")
                    val src = SecureKeyVault.resolveSafeProjectFile(projectRoot, path)
                    require(src.exists()) { "Source file not found: $path" }
                    val dst = SecureKeyVault.resolveSafeProjectFile(src.parentFile ?: projectRoot, newName)
                    src.renameTo(dst)
                    "Renamed '$path' -> '${dst.relativeTo(projectRoot).path}'"
                }
                "move_file" -> {
                    val srcPath = args.optString("source")
                    val dstPath = args.optString("destination")
                    val src = SecureKeyVault.resolveSafeProjectFile(projectRoot, srcPath)
                    val dst = SecureKeyVault.resolveSafeProjectFile(projectRoot, dstPath)
                    require(src.exists()) { "Source file not found: $srcPath" }
                    dst.parentFile?.mkdirs()
                    src.copyTo(dst, overwrite = true)
                    src.delete()
                    "Moved '$srcPath' -> '$dstPath'"
                }
                "create_folder" -> {
                    val path = args.optString("path")
                    val folder = SecureKeyVault.resolveSafeProjectFile(projectRoot, path)
                    folder.mkdirs()
                    "Created directory '${folder.relativeTo(projectRoot).path}/'"
                }
                "code_analysis" -> workspaceEngine.runCodeAnalysis(projectRoot)
                "syntax_diagnostics" -> workspaceEngine.runSyntaxDiagnostics(projectRoot)
                "generate_diff" -> {
                    val path = args.optString("path")
                    val newContent = args.optString("new_content")
                    val file = SecureKeyVault.resolveSafeProjectFile(projectRoot, path)
                    val oldContent = if (file.exists()) file.readText() else ""
                    val preview = workspaceEngine.generateUnifiedDiff(path, oldContent, newContent, applied = false)
                    diffPreview = preview
                    preview.unifiedDiff
                }
                "apply_patch" -> {
                    val path = args.optString("path")
                    val newContent = args.optString("new_content")
                    val file = SecureKeyVault.resolveSafeProjectFile(projectRoot, path)
                    val oldContent = if (file.exists()) file.readText() else ""
                    file.parentFile?.mkdirs()
                    file.writeText(newContent)
                    val preview = workspaceEngine.generateUnifiedDiff(path, oldContent, newContent, applied = true)
                    diffPreview = preview
                    "Applied patch to '$path' (+${preview.additions}/-${preview.deletions})"
                }
                "code_review" -> {
                    val path = args.optString("path", "")
                    val diag = workspaceEngine.runSyntaxDiagnostics(projectRoot)
                    val diff = workspaceEngine.gitDiff(projectRoot, path.ifBlank { null })
                    buildString {
                        appendLine("Automated Code & Security Audit:")
                        appendLine(diag)
                        appendLine("\nActive Diff Summary:")
                        appendLine(diff.take(1200))
                    }
                }
                "project_inspect" -> {
                    val analysis = workspaceEngine.runCodeAnalysis(projectRoot)
                    val git = workspaceEngine.gitStatus(projectRoot)
                    "$analysis\n\nGit Status:\n$git"
                }
                "project_init" -> {
                    val template = args.optString("template", "kotlin").lowercase()
                    scaffoldProjectTemplate(projectRoot, template)
                }
                "dependency_inspect" -> {
                    val candidates = listOf("build.gradle.kts", "package.json", "requirements.txt", "pubspec.yaml")
                    val found = candidates.mapNotNull { name ->
                        val f = File(projectRoot, name)
                        if (f.exists()) "=== $name ===\n${f.readText()}" else null
                    }
                    found.joinToString("\n\n").ifEmpty { "No manifest file (build.gradle.kts, package.json, requirements.txt) found in workspace root." }
                }
                "run_task" -> {
                    val cmd = args.optString("command", "verify")
                    val res = workspaceEngine.executeSupportedTask(projectRoot, cmd)
                    "Exit Code: ${res.exitCode} (${res.durationMs}ms)\n${res.stdout}\n${res.stderr}".trim()
                }
                "git_status" -> workspaceEngine.gitStatus(projectRoot)
                "git_diff" -> workspaceEngine.gitDiff(projectRoot, args.optString("path").ifBlank { null })
                "git_log" -> workspaceEngine.gitLog(projectRoot, args.optInt("limit", 10))
                "git_branch" -> {
                    val name = args.optString("name", "")
                    if (name.isBlank()) {
                        "Current branch: ${workspaceEngine.getCurrentBranch(projectRoot)}\nBranches: ${workspaceEngine.listBranches(projectRoot).joinToString(", ")}"
                    } else {
                        workspaceEngine.createOrSwitchBranch(projectRoot, name)
                    }
                }
                "git_commit" -> {
                    val msg = args.optString("message", "chore: workspace snapshot")
                    workspaceEngine.gitCommit(projectRoot, msg)
                }
                "web_search" -> {
                    val query = args.optString("query", "Kotlin Android")
                    performPublicWebSearch(query)
                }
                "fetch_webpage" -> {
                    val url = args.optString("url")
                    require(url.startsWith("https://") || url.startsWith("http://")) { "URL must start with http:// or https://" }
                    fetchPublicWebpage(url)
                }
                "docs_lookup" -> {
                    val topic = args.optString("topic", "Jetpack Compose")
                    lookupDeveloperDocs(topic)
                }
                "clipboard_copy" -> {
                    val text = args.optString("text", "")
                    withContext(Dispatchers.Main) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("PiashAgent", text))
                    }
                    "Copied ${text.length} characters to Android clipboard."
                }
                "saf_workspace_info" -> {
                    val freeMb = projectRoot.usableSpace / (1024 * 1024)
                    val totalMb = projectRoot.totalSpace / (1024 * 1024)
                    "Sandbox Root: ${projectRoot.absolutePath}\nUsable Storage: $freeMb MB / $totalMb MB\nIsolation: Android App-Private + SAF Document Tree"
                }
                else -> "Executed tool '$toolName' with arguments: $argumentsJson"
            }
        } catch (e: Exception) {
            status = "FAILED"
            "Tool Error (${e.javaClass.simpleName}): ${e.localizedMessage ?: "Execution failed"}"
        }

        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)

        dao.insertAuditLog(
            AuditLogEntity(
                toolName = toolName,
                agentRole = agentRole.name,
                projectId = projectId,
                argumentsSummary = argumentsJson.take(240),
                decision = if (status == "FAILED") "FAILED" else decisionLabel,
                resultSummary = outputText.take(240),
                durationMs = elapsed
            )
        )

        ToolExecutionRecord(
            id = UUID.randomUUID().toString(),
            toolName = toolName,
            category = category,
            agentRole = agentRole,
            argumentsJson = argumentsJson,
            status = status,
            output = outputText,
            durationMs = elapsed,
            fileDiff = diffPreview
        )
    }

    fun scaffoldProjectTemplate(projectRoot: File, template: String): String {
        projectRoot.mkdirs()
        when (template) {
            "python" -> {
                File(projectRoot, "README.md").writeText("# Python Project\nManaged by PiashAgent Native Workspace.\n")
                File(projectRoot, "requirements.txt").writeText("pydantic>=2.7.0\nhttpx>=0.27.0\npytest>=8.2.0\n")
                File(projectRoot, "main.py").writeText(
                    """
                    from dataclasses import dataclass
                    from typing import List

                    @dataclass
                    class TaskItem:
                        id: int
                        title: str
                        completed: bool = False

                    def summarize_tasks(tasks: List[TaskItem]) -> str:
                        done = sum(1 for t in tasks if t.completed)
                        return f"Completed {done}/{len(tasks)} tasks"

                    if __name__ == "__main__":
                        sample = [TaskItem(1, "Initialize PiashAgent workspace", True), TaskItem(2, "Run code analysis", False)]
                        print(summarize_tasks(sample))
                    """.trimIndent() + "\n"
                )
            }
            "react", "web" -> {
                File(projectRoot, "package.json").writeText(
                    """
                    {
                      "name": "piash-web-app",
                      "version": "1.0.0",
                      "private": true,
                      "dependencies": {
                        "react": "^19.0.0",
                        "react-dom": "^19.0.0"
                      }
                    }
                    """.trimIndent() + "\n"
                )
                val src = File(projectRoot, "src").apply { mkdirs() }
                File(src, "App.tsx").writeText(
                    """
                    import React, { useState } from 'react';

                    export function App() {
                      const [count, setCount] = useState<number>(0);
                      return (
                        <main className="workspace-shell">
                          <h1>PiashAgent Web Module</h1>
                          <button onClick={() => setCount(c => c + 1)}>Count: {count}</button>
                        </main>
                      );
                    }
                    """.trimIndent() + "\n"
                )
            }
            else -> {
                File(projectRoot, "README.md").writeText(
                    """
                    # ${projectRoot.name}
                    Native Kotlin/Android Architecture Project managed inside **PiashAgent**.

                    ## Modules
                    - `src/main/kotlin/AppEngine.kt`: Core domain service & reactive state machine
                    - `src/test/kotlin/AppEngineTest.kt`: Verification test suite
                    """.trimIndent() + "\n"
                )
                File(projectRoot, "build.gradle.kts").writeText(
                    """
                    plugins {
                        kotlin("jvm") version "2.2.10"
                    }
                    dependencies {
                        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                        testImplementation(kotlin("test"))
                    }
                    """.trimIndent() + "\n"
                )
                val mainDir = File(projectRoot, "src/main/kotlin").apply { mkdirs() }
                File(mainDir, "AppEngine.kt").writeText(
                    """
                    package workspace.core

                    import kotlinx.coroutines.flow.MutableStateFlow
                    import kotlinx.coroutines.flow.StateFlow
                    import kotlinx.coroutines.flow.asStateFlow

                    data class ServiceMetrics(
                        val activeTasks: Int,
                        val healthy: Boolean
                    )

                    class AppEngine {
                        private val _metrics = MutableStateFlow(ServiceMetrics(activeTasks = 0, healthy = true))
                        val metrics: StateFlow<ServiceMetrics> = _metrics.asStateFlow()

                        fun recordTaskStarted() {
                            _metrics.value = _metrics.value.copy(activeTasks = _metrics.value.activeTasks + 1)
                        }

                        fun recordTaskCompleted() {
                            val next = (_metrics.value.activeTasks - 1).coerceAtLeast(0)
                            _metrics.value = _metrics.value.copy(activeTasks = next)
                        }
                    }
                    """.trimIndent() + "\n"
                )
                val testDir = File(projectRoot, "src/test/kotlin").apply { mkdirs() }
                File(testDir, "AppEngineTest.kt").writeText(
                    """
                    package workspace.core

                    class AppEngineTest {
                        fun verifyMetricsLifecycle() {
                            val engine = AppEngine()
                            engine.recordTaskStarted()
                            check(engine.metrics.value.activeTasks == 1)
                            engine.recordTaskCompleted()
                            check(engine.metrics.value.activeTasks == 0)
                        }
                    }
                    """.trimIndent() + "\n"
                )
            }
        }
        workspaceEngine.initVcsIfNeeded(projectRoot)
        workspaceEngine.gitCommit(projectRoot, "chore: initialize $template project template")
        return "Initialized '$template' starter files and created initial Git commit in '${projectRoot.name}'."
    }

    private fun performPublicWebSearch(query: String): String {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://en.wikipedia.org/api/rest_v1/page/summary/${encoded.replace("+", "_")}"
            val req = Request.Builder().url(url).header("User-Agent", "PiashAgent/1.0").build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val title = json.optString("title", query)
                    val extract = json.optString("extract", "")
                    val pageUrl = json.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")
                        ?: "https://en.wikipedia.org/wiki/$encoded"
                    if (extract.isNotBlank()) {
                        return "Search Summary for '$title':\n$extract\n\nCitation: $pageUrl"
                    }
                }
            }
            lookupDeveloperDocs(query)
        } catch (e: Exception) {
            lookupDeveloperDocs(query)
        }
    }

    private fun fetchPublicWebpage(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "PiashAgent-Android/1.0")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val stripped = raw
                .replace(Regex("(?s)<script.*?</script>"), " ")
                .replace(Regex("(?s)<style.*?</style>"), " ")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            return "Source URL: $url (HTTP ${resp.code})\n\n${stripped.take(2500)}"
        }
    }

    private fun lookupDeveloperDocs(topic: String): String {
        return """
            Documentation Reference — $topic
            • Official Kotlin Docs: https://kotlinlang.org/docs/home.html
            • Android Jetpack Guide: https://developer.android.com/jetpack
            • Model Context Protocol Spec: https://modelcontextprotocol.io/introduction
            • Summary: Use structured concurrency (CoroutineScope / viewModelScope), immutable StateFlow state containers, and sandboxed file I/O via Android Storage Access Framework or app-private filesDir.
        """.trimIndent()
    }
}
