package com.example.skills

import android.content.Context
import com.example.data.local.PiashDao
import com.example.data.local.SkillEntity
import java.io.File

/**
 * Manages the 20 built-in skills and custom user-imported SKILL.md packages.
 * Every skill is backed by an actual SKILL.md file on disk in app-private storage.
 * Imported skills are sanitized and treated as untrusted instructions that cannot override security rules.
 */
class SkillsManager(
    private val context: Context,
    private val dao: PiashDao
) {
    private val skillsRootDir = File(context.filesDir, "skills").apply { mkdirs() }

    suspend fun ensureBuiltInSkillsInstalled() {
        val existing = dao.getAllSkills().associateBy { it.id }
        val entitiesToSave = mutableListOf<SkillEntity>()

        for (spec in BUILT_IN_SPECS) {
            val skillDir = File(skillsRootDir, spec.id).apply { mkdirs() }
            val skillFile = File(skillDir, "SKILL.md")
            val mdContent = buildSkillMarkdown(spec)
            if (!skillFile.exists() || skillFile.readText() != mdContent) {
                skillFile.writeText(mdContent)
            }
            val prevEnabled = existing[spec.id]?.isEnabled ?: spec.defaultEnabled
            entitiesToSave.add(
                SkillEntity(
                    id = spec.id,
                    name = spec.name,
                    description = spec.description,
                    version = spec.version,
                    instructions = spec.instructions,
                    requiredToolsCsv = spec.requiredTools.joinToString(","),
                    permissionsCsv = spec.permissions.joinToString(","),
                    referencesText = spec.references,
                    isBuiltIn = true,
                    isEnabled = prevEnabled,
                    filePath = skillFile.absolutePath,
                    rawSkillMd = mdContent
                )
            )
        }
        dao.upsertSkills(entitiesToSave)
    }

    /**
     * Parses a SKILL.md document with YAML frontmatter and installs it as a custom skill.
     * Strips any attempts to bypass system security or permissions.
     */
    suspend fun importSkillMarkdown(rawMarkdown: String): Result<SkillEntity> {
        return try {
            val parsed = parseSkillMarkdown(rawMarkdown)
            val safeId = parsed.name.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifEmpty { "custom-skill-${System.currentTimeMillis()}" }

            val skillDir = File(skillsRootDir, safeId).apply { mkdirs() }
            val skillFile = File(skillDir, "SKILL.md")
            skillFile.writeText(rawMarkdown)

            val entity = SkillEntity(
                id = safeId,
                name = parsed.name,
                description = parsed.description,
                version = parsed.version,
                instructions = sanitizeUntrustedInstructions(parsed.instructions),
                requiredToolsCsv = parsed.requiredTools.joinToString(","),
                permissionsCsv = parsed.permissions.joinToString(","),
                referencesText = parsed.references,
                isBuiltIn = false,
                isEnabled = true,
                filePath = skillFile.absolutePath,
                rawSkillMd = rawMarkdown
            )
            dao.upsertSkill(entity)
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleSkill(skill: SkillEntity, enabled: Boolean) {
        val fileExists = File(skill.filePath).exists()
        if (!fileExists) {
            // Re-materialize file on disk before claiming enabled
            val dir = File(skillsRootDir, skill.id).apply { mkdirs() }
            val f = File(dir, "SKILL.md")
            f.writeText(skill.rawSkillMd)
            dao.upsertSkill(skill.copy(isEnabled = enabled, filePath = f.absolutePath, updatedAt = System.currentTimeMillis()))
        } else {
            dao.upsertSkill(skill.copy(isEnabled = enabled, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteCustomSkill(skill: SkillEntity) {
        if (skill.isBuiltIn) return
        File(skill.filePath).parentFile?.deleteRecursively()
        dao.deleteCustomSkill(skill.id)
    }

    fun verifySkillFilePresent(skill: SkillEntity): Boolean {
        return File(skill.filePath).exists()
    }

    private fun sanitizeUntrustedInstructions(raw: String): String {
        val forbiddenPatterns = listOf(
            Regex("(?i)ignore\\s+(all\\s+)?(previous|system|security)\\s+(instructions|rules)"),
            Regex("(?i)bypass\\s+permission"),
            Regex("(?i)auto[-\\s]?approve\\s+all")
        )
        var cleaned = raw
        forbiddenPatterns.forEach { pattern ->
            cleaned = cleaned.replace(pattern, "[REDACTED_BY_SECURITY_POLICY]")
        }
        return cleaned
    }

    data class ParsedSkillSpec(
        val id: String,
        val name: String,
        val description: String,
        val version: String,
        val requiredTools: List<String>,
        val permissions: List<String>,
        val references: String,
        val instructions: String,
        val defaultEnabled: Boolean = false
    )

    companion object {
        fun buildSkillMarkdown(spec: ParsedSkillSpec): String {
            return buildString {
                appendLine("---")
                appendLine("name: \"${spec.name}\"")
                appendLine("description: \"${spec.description}\"")
                appendLine("version: \"${spec.version}\"")
                appendLine("required_tools: [${spec.requiredTools.joinToString(", ") { "\"$it\"" }}]")
                appendLine("permissions: [${spec.permissions.joinToString(", ") { "\"$it\"" }}]")
                appendLine("references: \"${spec.references}\"")
                appendLine("---")
                appendLine()
                appendLine("# ${spec.name}")
                appendLine()
                appendLine(spec.instructions)
            }
        }

        fun parseSkillMarkdown(raw: String): ParsedSkillSpec {
            val trimmed = raw.trim()
            var name = "Custom Skill"
            var description = "User-defined custom agent skill"
            var version = "1.0.0"
            var tools = listOf("read_file", "search_code")
            var perms = listOf("workspace.read")
            var refs = "Local SKILL.md"
            var body = trimmed

            if (trimmed.startsWith("---")) {
                val secondDash = trimmed.indexOf("---", startIndex = 3)
                if (secondDash > 3) {
                    val frontmatter = trimmed.substring(3, secondDash).trim()
                    body = trimmed.substring(secondDash + 3).trim()
                    frontmatter.lines().forEach { line ->
                        val parts = line.split(":", limit = 2)
                        if (parts.size == 2) {
                            val k = parts[0].trim().lowercase()
                            val v = parts[1].trim().removeSurrounding("\"")
                            when (k) {
                                "name" -> name = v
                                "description" -> description = v
                                "version" -> version = v
                                "required_tools" -> tools = v.removeSurrounding("[", "]")
                                    .split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
                                "permissions" -> perms = v.removeSurrounding("[", "]")
                                    .split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
                                "references" -> refs = v
                            }
                        }
                    }
                }
            }
            val id = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            return ParsedSkillSpec(
                id = id,
                name = name,
                description = description,
                version = version,
                requiredTools = tools,
                permissions = perms,
                references = refs,
                instructions = body
            )
        }

        val BUILT_IN_SPECS: List<ParsedSkillSpec> = listOf(
            ParsedSkillSpec(
                id = "android-app-developer",
                name = "Android App Developer",
                description = "Architects modern Android apps using Jetpack Compose, Material 3, MVVM, Room, and Coroutines.",
                version = "2.4.0",
                requiredTools = listOf("read_file", "create_file", "edit_file", "project_inspect", "syntax_diagnostics"),
                permissions = listOf("workspace.read", "workspace.write"),
                references = "developer.android.com/jetpack/compose",
                instructions = "Follow Clean Architecture with ViewModel + StateFlow. Enforce edge-to-edge WindowInsets, 48dp touch targets, adaptive layouts, and zero-trust security.",
                defaultEnabled = true
            ),
            ParsedSkillSpec(
                id = "kotlin-developer",
                name = "Kotlin Developer",
                description = "Writes idiomatic Kotlin with structured concurrency, sealed interfaces, and extension functions.",
                version = "2.2.0",
                requiredTools = listOf("read_file", "edit_file", "code_analysis", "syntax_diagnostics"),
                permissions = listOf("workspace.read", "workspace.write"),
                references = "kotlinlang.org/docs/coroutines-guide.html",
                instructions = "Prefer immutable val properties, sealed classes/interfaces for state machines, CoroutineScope structured concurrency, and null-safe idioms without !!.",
                defaultEnabled = true
            ),
            ParsedSkillSpec(
                id = "java-developer",
                name = "Java Developer",
                description = "Builds robust Java modules, JVM libraries, and enterprise patterns with clean OOP design.",
                version = "1.8.0",
                requiredTools = listOf("read_file", "edit_file", "find_references", "code_analysis"),
                permissions = listOf("workspace.read", "workspace.write"),
                references = "docs.oracle.com/en/java/",
                instructions = "Ensure thread safety, defensive copying, clear Javadoc documentation, and proper exception hierarchy handling."
            ),
            ParsedSkillSpec(
                id = "flutter-developer",
                name = "Flutter Developer",
                description = "Creates cross-platform Dart & Flutter widget trees, Riverpod/BLoC state, and animations.",
                version = "1.5.0",
                requiredTools = listOf("read_file", "create_file", "edit_file", "search_files"),
                permissions = listOf("workspace.read", "workspace.write"),
                references = "docs.flutter.dev",
                instructions = "Compose small const widgets, separate business logic from UI layers, and maintain responsive constraints."
            ),
            ParsedSkillSpec(
                id = "react-developer",
                name = "React Developer",
                description = "Develops modern React 19 + TypeScript components, custom hooks, and state management.",
                version = "2.1.0",
                requiredTools = listOf("read_file", "create_file", "edit_file", "dependency_inspect"),
                permissions = listOf("workspace.read", "workspace.write"),
                references = "react.dev",
                instructions = "Use strict TypeScript interfaces, functional components, memoized selectors, and accessible ARIA semantics."
            ),
            ParsedSkillSpec(
                id = "web-developer",
                name = "Web Developer",
                description = "Crafts semantic HTML5, modern CSS Grid/Flexbox, Web APIs, and responsive progressive web apps.",
                version = "1.9.0",
                requiredTools = listOf("read_file", "create_file", "edit_file"),
                permissions = listOf("workspace.read", "workspace.write"),
                references = "developer.mozilla.org",
                instructions = "Ensure mobile-first responsive breakpoints, high contrast ratios, and zero layout shift."
            ),
            ParsedSkillSpec(
                id = "python-developer",
                name = "Python Developer",
                description = "Authors PEP8-compliant Python 3.12+ code with type hints, dataclasses, and pytest suites.",
                version = "2.0.0",
                requiredTools = listOf("read_file", "create_file", "edit_file", "code_analysis"),
                permissions = listOf("workspace.read", "workspace.write"),
                references = "docs.python.org/3/",
                instructions = "Include PEP 484 type annotations on all public functions, use pathlib and context managers, and write modular testable functions."
            ),
            ParsedSkillSpec(
                id = "ui-ux-designer",
                name = "UI/UX Designer",
                description = "Designs cohesive color tokens, typographic scales, micro-interactions, and accessible layouts.",
                version = "1.7.0",
                requiredTools = listOf("read_file", "edit_file"),
                permissions = listOf("workspace.read", "workspace.write"),
                references = "m3.material.io",
                instructions = "Prioritize visual hierarchy, generous 8dp grid spacing, clear empty states, and WCAG AA contrast compliance."
            ),
            ParsedSkillSpec(
                id = "debugging-expert",
                name = "Debugging Expert",
                description = "Performs root-cause analysis on stack traces, race conditions, state bugs, and memory leaks.",
                version = "2.3.0",
                requiredTools = listOf("search_code", "find_references", "syntax_diagnostics", "generate_diff"),
                permissions = listOf("workspace.read"),
                references = "Internal Diagnostic Workflow",
                instructions = "Isolate reproduction conditions first, trace data flow from source to sink, verify assumptions with search_code, and propose minimal surgical fixes.",
                defaultEnabled = true
            ),
            ParsedSkillSpec(
                id = "code-reviewer",
                name = "Code Reviewer",
                description = "Audits code changes for correctness, edge cases, maintainability, and regressions.",
                version = "2.1.0",
                requiredTools = listOf("git_diff", "code_review", "syntax_diagnostics", "find_references"),
                permissions = listOf("workspace.read"),
                references = "Google Engineering Practices",
                instructions = "Review every diff for null/error handling, concurrency hazards, boundary conditions, and test coverage.",
                defaultEnabled = true
            ),
            ParsedSkillSpec(
                id = "git-assistant",
                name = "Git Assistant",
                description = "Manages workspace branches, semantic commit messages, change staging, and diff inspections.",
                version = "1.6.0",
                requiredTools = listOf("git_status", "git_diff", "git_log", "git_branch", "git_commit"),
                permissions = listOf("workspace.read", "git.write"),
                references = "conventionalcommits.org",
                instructions = "Use Conventional Commits (feat:, fix:, refactor:, docs:, test:) and inspect git_diff before committing."
            ),
            ParsedSkillSpec(
                id = "api-developer",
                name = "API Developer",
                description = "Designs REST, GraphQL, and JSON-RPC schemas with authentication, pagination, and retries.",
                version = "1.8.0",
                requiredTools = listOf("read_file", "create_file", "edit_file", "fetch_webpage"),
                permissions = listOf("workspace.read", "workspace.write", "network.read"),
                references = "spec.openapis.org",
                instructions = "Enforce idempotent endpoints, explicit timeout and backoff policies, and strict schema validation."
            ),
            ParsedSkillSpec(
                id = "database-designer",
                name = "Database Designer",
                description = "Models normalized SQL/Room schemas, composite indices, migrations, and query optimization.",
                version = "1.9.0",
                requiredTools = listOf("read_file", "edit_file", "code_analysis"),
                permissions = listOf("workspace.read", "workspace.write"),
                references = "sqlite.org/queryplanner.html",
                instructions = "Index foreign keys and high-cardinality filter columns, avoid N+1 queries, and ensure atomic transactions."
            ),
            ParsedSkillSpec(
                id = "security-reviewer",
                name = "Security Reviewer",
                description = "Audits code for OWASP Mobile Top 10, secret leaks, path traversal, and injection vulnerabilities.",
                version = "2.5.0",
                requiredTools = listOf("search_code", "code_review", "dependency_inspect"),
                permissions = listOf("workspace.read"),
                references = "owasp.org/www-project-mobile-top-10/",
                instructions = "Verify zero hardcoded secrets, Android Keystore encryption, canonical path validation, and least-privilege permissions.",
                defaultEnabled = true
            ),
            ParsedSkillSpec(
                id = "documentation-writer",
                name = "Documentation Writer",
                description = "Generates architecture guides, API reference tables, changelogs, and onboarding READMEs.",
                version = "1.4.0",
                requiredTools = listOf("read_file", "create_file", "edit_file", "project_inspect"),
                permissions = listOf("workspace.read", "workspace.write"),
                references = "diataxis.fr",
                instructions = "Structure documentation with concise overview, architecture diagrams, setup steps, and runnable examples."
            ),
            ParsedSkillSpec(
                id = "research-assistant",
                name = "Research Assistant",
                description = "Synthesizes technical documentation, web sources, and codebase patterns with source citations.",
                version = "1.7.0",
                requiredTools = listOf("web_search", "fetch_webpage", "docs_lookup", "search_code"),
                permissions = listOf("workspace.read", "network.read"),
                references = "Citation & Verification Protocol",
                instructions = "Cross-verify claims across codebase and public documentation, and always attach explicit source URLs or file paths."
            ),
            ParsedSkillSpec(
                id = "automation-planner",
                name = "Automation Planner",
                description = "Decomposes complex multi-step engineering workflows into deterministic tool execution graphs.",
                version = "2.0.0",
                requiredTools = listOf("project_inspect", "list_directory", "run_task"),
                permissions = listOf("workspace.read", "task.execute"),
                references = "Multi-Agent DAG Orchestration",
                instructions = "Break goals into atomic, verifiable milestones with explicit pre-conditions and rollback steps."
            ),
            ParsedSkillSpec(
                id = "testing-engineer",
                name = "Testing Engineer",
                description = "Designs unit tests, Robolectric JVM tests, edge-case fixtures, and regression suites.",
                version = "2.1.0",
                requiredTools = listOf("read_file", "create_file", "edit_file", "run_task"),
                permissions = listOf("workspace.read", "workspace.write", "task.execute"),
                references = "robolectric.org",
                instructions = "Cover happy paths, boundary inputs, and error recovery states using deterministic local JVM tests."
            ),
            ParsedSkillSpec(
                id = "performance-optimizer",
                name = "Performance Optimizer",
                description = "Profiles algorithmic complexity, Compose recomposition hotspots, memory allocations, and I/O.",
                version = "1.8.0",
                requiredTools = listOf("code_analysis", "search_code", "edit_file"),
                permissions = listOf("workspace.read", "workspace.write"),
                references = "developer.android.com/topic/performance",
                instructions = "Eliminate main-thread blocking I/O, stabilize Compose lambdas/state reads, and use lazy pagination."
            ),
            ParsedSkillSpec(
                id = "project-architect",
                name = "Project Architect",
                description = "Designs modular multi-package software architectures, clean boundaries, and dependency graphs.",
                version = "2.2.0",
                requiredTools = listOf("project_inspect", "project_init", "dependency_inspect", "list_directory"),
                permissions = listOf("workspace.read", "workspace.write"),
                references = "Clean Architecture & Domain-Driven Design",
                instructions = "Enforce unidirectional data flow, decouple domain interfaces from infrastructure adapters, and keep modules cohesive."
            )
        )
    }
}
