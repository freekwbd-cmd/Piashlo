package com.example

import com.example.agent.AgentOrchestrator
import com.example.agent.SlashCommandAction
import com.example.agent.SlashCommandProcessor
import com.example.domain.model.AgentRole
import com.example.domain.model.ModelCatalog
import com.example.execution.NativeWorkspaceEngine
import com.example.security.SecureKeyVault
import com.example.skills.SkillsManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    @Test
    fun `verify 20 built-in skills and SKILL markdown parsing`() {
        assertEquals(20, SkillsManager.BUILT_IN_SPECS.size)
        val first = SkillsManager.BUILT_IN_SPECS.first()
        val md = SkillsManager.buildSkillMarkdown(first)
        assertTrue(md.contains("name: \"Android App Developer\""))
        val reparsed = SkillsManager.parseSkillMarkdown(md)
        assertEquals("Android App Developer", reparsed.name)
        assertTrue(reparsed.requiredTools.contains("read_file"))
    }

    @Test
    fun `verify path traversal protection blocks escaping project root`() {
        val tempRoot = Files.createTempDirectory("piash_sandbox_test").toFile()
        try {
            val safeFile = SecureKeyVault.resolveSafeProjectFile(tempRoot, "src/Main.kt")
            assertTrue(safeFile.canonicalPath.startsWith(tempRoot.canonicalPath))

            var blocked = false
            try {
                SecureKeyVault.resolveSafeProjectFile(tempRoot, "../../etc/passwd")
            } catch (_: IllegalArgumentException) {
                blocked = true
            }
            assertTrue("Path traversal must be blocked", blocked)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `verify native git vcs engine status diff and commit`() = runBlocking {
        val tempProject = Files.createTempDirectory("piash_git_test").toFile()
        try {
            val engine = NativeWorkspaceEngine()
            engine.initVcsIfNeeded(tempProject)
            assertEquals("main", engine.getCurrentBranch(tempProject))

            File(tempProject, "App.kt").writeText("fun main() {\n    println(\"v1\")\n}\n")
            val status1 = engine.gitStatus(tempProject)
            assertTrue(status1.contains("App.kt"))

            val commitMsg = engine.gitCommit(tempProject, "feat: initial commit")
            assertTrue(commitMsg.contains("feat: initial commit"))

            File(tempProject, "App.kt").writeText("fun main() {\n    println(\"v2\")\n}\n")
            val diff = engine.gitDiff(tempProject)
            assertTrue(diff.contains("-    println(\"v1\")"))
            assertTrue(diff.contains("+    println(\"v2\")"))

            val taskRes = engine.executeSupportedTask(tempProject, "verify")
            assertEquals(0, taskRes.exitCode)
            assertTrue(taskRes.stdout.contains("0 syntax issues"))

            val termuxBlocked = engine.executeSupportedTask(tempProject, "proot -0")
            assertEquals(126, termuxBlocked.exitCode)
        } finally {
            tempProject.deleteRecursively()
        }
    }

    @Test
    fun `verify slash command processor and tool call parser`() {
        val helpAction = SlashCommandProcessor.process(
            rawInput = "/help",
            currentProject = null,
            currentConversation = null,
            skills = emptyList(),
            mcpServers = emptyList(),
            memories = emptyList(),
            roleModels = emptyMap()
        )
        assertTrue(helpAction is SlashCommandAction.ImmediateReply)

        val switchAction = SlashCommandProcessor.process(
            rawInput = "/model gemini-4-argon",
            currentProject = null,
            currentConversation = null,
            skills = emptyList(),
            mcpServers = emptyList(),
            memories = emptyList(),
            roleModels = emptyMap()
        )
        assertTrue(switchAction is SlashCommandAction.SwitchModel)

        val sampleOutput = """
            I will inspect the file first.
            <tool_call>{"tool": "read_file", "args": {"path": "src/AppEngine.kt"}}</tool_call>
        """.trimIndent()
        val extracted = AgentOrchestrator.extractToolCalls(sampleOutput)
        assertEquals(1, extracted.size)
        assertEquals("read_file", extracted.first().first)
    }

    @Test
    fun `verify model catalog and multi-agent roles`() {
        val flash = ModelCatalog.findById("gemini-3.8-flash")
        assertNotNull(flash)
        assertEquals(6, AgentRole.entries.size)
        assertFalse(ModelCatalog.models.isEmpty())
    }
}
