# PiashAgent — Your Personal AI Development Agent

**PiashAgent** is a native Android AI engineering workspace inspired by Claude Desktop, Claude Code, and modern multi-agent IDE tools. It operates 100% natively within the Android application sandbox — **zero Termux, zero Proot, zero Chroot, and zero Root dependencies**.

---

## Core Architecture & Capabilities

1. **Pure AMOLED Black Workspace (`#000000`)**:
   - Claude Desktop-inspired interface built with Jetpack Compose and Material 3.
   - Bundled local Google Fonts (`Space Grotesk`, `Inter`, `JetBrains Mono`).
   - Optional Light Mode and 5 customizable accent themes (`Cyber Cyan`, `Neural Violet`, `Matrix Emerald`, `Claude Amber`, `Crimson Pulse`).
   - Adaptive responsive layout (`NavigationBar` on handhelds, `NavigationRail` on tablets/foldables).

2. **Modular AI Provider Gateway (`providers/AiProviderGateway.kt`)**:
   - **Google Gemini (`gemini-3.8-flash`, `gemini-3.5-flash-lite`, `gemini-4-argon`)**: Provisioned natively via **Firebase AI Logic** (`Firebase.ai`) and secured with **Firebase App Check**.
   - **Anthropic Claude**, **OpenAI**, **DeepSeek**, **OpenRouter**, **Ollama (LAN)**, and **OpenAI-Compatible Endpoints**: User-configurable via AES-256-GCM **Android Keystore** credential encryption (`SecureKeyVault.kt`).
   - Per-agent role model selection, live connection testing, token estimation, and USD session cost tracking.

3. **Genuine Iterative Agent Engine & Multi-Agent Orchestrator (`agent/AgentOrchestrator.kt`)**:
   - Full Agent Loop: `User Request → Context Collection → Planning → Tool Selection → Permission Check → Tool Execution → Result Observation → Next Action → Verification → Final Streaming Response`.
   - **Multi-Agent System**: Coordinates **Main**, **Planner**, **Research**, **Coding**, **Review**, and **Testing** agents with a concurrent file-lock conflict manager (`ConcurrentHashMap<String, AgentRole>`).

4. **26 Native Android Workspace Tools (`tools/ToolRegistry.kt`)**:
   - **File Tools**: `read_file`, `create_file`, `edit_file`, `delete_file`, `list_directory`, `search_files`, `search_code`, `find_references`, `rename_file`, `move_file`, `create_folder`.
   - **Code Tools**: `code_analysis`, `syntax_diagnostics`, `generate_diff`, `apply_patch`, `code_review`.
   - **Project Tools**: `project_inspect`, `project_init`, `dependency_inspect`, `run_task`.
   - **Git VCS Tools**: `git_status`, `git_diff`, `git_log`, `git_branch`, `git_commit` backed by `NativeWorkspaceEngine`.
   - **Web & Docs Tools**: `web_search`, `fetch_webpage`, `docs_lookup`.
   - **Android Tools**: `clipboard_copy`, `saf_workspace_info`, plus Storage Access Framework (`OpenDocument`) file import and native Speech-to-Text voice input.

5. **Skills System (`skills/SkillsManager.kt`)**:
   - 20 Built-In Skills materialized and verified as real `SKILL.md` files on disk (`filesDir/skills/<id>/SKILL.md`), plus custom `SKILL.md` import, sanitization, export, and toggle controls.

6. **Model Context Protocol (MCP) Client (`mcp/McpClientManager.kt`)**:
   - JSON-RPC 2.0 MCP client supporting `initialize`, `tools/list`, `resources/list`, `prompts/list`, and `tools/call`, plus a built-in Android Workspace Loopback MCP Server (`mcp://android-local/workspace`) and explicit trust/permission gating.

7. **Claude Code-Style Slash Commands (`agent/SlashCommandProcessor.kt`)**:
   - `/help`, `/model`, `/provider`, `/skills`, `/agents`, `/plan`, `/compact`, `/cost`, `/session`, `/project`, `/permissions`, `/mcp`, `/memory`, `/review`, `/test`, `/export`, `/clear`.

---

## Build & Verification Instructions

- **Compile & Assemble Debug APK**:
  ```bash
  gradle assembleDebug
  ```
- **Run Unit & Robolectric Tests**:
  ```bash
  gradle :app:testDebugUnitTest
  ```
