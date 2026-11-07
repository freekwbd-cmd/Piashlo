package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.FileDiffPreview
import com.example.domain.model.PendingPermissionRequest
import com.example.domain.model.ToolExecutionRecord
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElevatedSurfaceDark
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RoseAccent
import com.example.ui.theme.VioletAccent

/**
 * Syntax highlighter for Kotlin, Java, Python, TypeScript/JS, JSON, and Shell snippets.
 */
fun highlightCodeSyntax(code: String): AnnotatedString {
    val keywords = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var", "return",
        "if", "else", "when", "for", "while", "try", "catch", "finally", "throw", "suspend",
        "private", "public", "internal", "protected", "override", "data", "sealed", "enum",
        "def", "from", "async", "await", "function", "const", "let", "export", "default",
        "true", "false", "null", "None", "True", "False"
    )
    val tokenRegex = Regex("""("//.*|#.*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|@[A-Za-z0-9_]+|\b[A-Za-z_][A-Za-z0-9_]*\b|\b\d+(?:\.\d+)?\b)""")

    return buildAnnotatedString {
        var lastIdx = 0
        for (match in tokenRegex.findAll(code)) {
            if (match.range.first > lastIdx) {
                append(code.substring(lastIdx, match.range.first))
            }
            val token = match.value
            val style = when {
                token.startsWith("//") || token.startsWith("#") || token.startsWith("/*") ->
                    SpanStyle(color = Color(0xFF6B7280))
                token.startsWith("\"") ->
                    SpanStyle(color = EmeraldAccent)
                token.startsWith("@") ->
                    SpanStyle(color = AmberAccent, fontWeight = FontWeight.Medium)
                token in keywords ->
                    SpanStyle(color = CyanAccent, fontWeight = FontWeight.SemiBold)
                token.first().isDigit() ->
                    SpanStyle(color = VioletAccent)
                token.first().isUpperCase() ->
                    SpanStyle(color = Color(0xFF93C5FD))
                else -> null
            }
            if (style != null) {
                withStyle(style) { append(token) }
            } else {
                append(token)
            }
            lastIdx = match.range.last + 1
        }
        if (lastIdx < code.length) {
            append(code.substring(lastIdx))
        }
    }
}

/**
 * Renders Claude Desktop-style Markdown including fenced code blocks with copy & apply actions,
 * headers, bullet lists, and inline code spans.
 */
@Composable
fun RichMarkdownContent(
    markdown: String,
    onCopyCode: (String) -> Unit,
    onSaveSnippetToFile: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val segments = remember(markdown) { parseMarkdownSegments(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEach { seg ->
            when (seg) {
                is MarkdownSegment.CodeBlock -> {
                    Surface(
                        color = Color(0xFF09090D),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ElevatedSurfaceDark)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = seg.language.ifBlank { "code" }.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (onSaveSnippetToFile != null) {
                                        Text(
                                            text = "Open in Editor",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable {
                                                    val ext = seg.language.lowercase().ifBlank { "kt" }
                                                    onSaveSnippetToFile("src/Snippet.$ext", seg.code)
                                                }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { onCopyCode(seg.code) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy code",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Copy",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = remember(seg.code) { highlightCodeSyntax(seg.code) },
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                is MarkdownSegment.TextBlock -> {
                    seg.text.lines().forEach { line ->
                        val trimmed = line.trim()
                        when {
                            trimmed.startsWith("### ") -> Text(
                                text = trimmed.removePrefix("### "),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            trimmed.startsWith("## ") -> Text(
                                text = trimmed.removePrefix("## "),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            trimmed.startsWith("# ") -> Text(
                                text = trimmed.removePrefix("# "),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            trimmed.startsWith("- ") || trimmed.startsWith("• ") -> Row(
                                modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "• ",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = renderInlineFormatting(trimmed.drop(2)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            trimmed.isNotEmpty() -> Text(
                                text = renderInlineFormatting(line),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun renderInlineFormatting(line: String): AnnotatedString {
    val inlineRegex = Regex("""(\*\*.*?\*\*|`[^`]+`)""")
    return buildAnnotatedString {
        var last = 0
        for (m in inlineRegex.findAll(line)) {
            if (m.range.first > last) {
                append(line.substring(last, m.range.first))
            }
            val token = m.value
            when {
                token.startsWith("**") && token.endsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                        append(token.removeSurrounding("**"))
                    }
                }
                token.startsWith("`") && token.endsWith("`") -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            color = CyanAccent,
                            background = Color(0xFF18181B)
                        )
                    ) {
                        append(" ${token.removeSurrounding("`")} ")
                    }
                }
                else -> append(token)
            }
            last = m.range.last + 1
        }
        if (last < line.length) {
            append(line.substring(last))
        }
    }
}

sealed interface MarkdownSegment {
    data class TextBlock(val text: String) : MarkdownSegment
    data class CodeBlock(val language: String, val code: String) : MarkdownSegment
}

private fun parseMarkdownSegments(raw: String): List<MarkdownSegment> {
    val result = mutableListOf<MarkdownSegment>()
    val fenceRegex = Regex("(?s)```([a-zA-Z0-9_-]*)\\n?(.*?)```")
    var lastIdx = 0
    for (match in fenceRegex.findAll(raw)) {
        if (match.range.first > lastIdx) {
            val textPart = raw.substring(lastIdx, match.range.first).trim()
            if (textPart.isNotEmpty()) result.add(MarkdownSegment.TextBlock(textPart))
        }
        val lang = match.groupValues[1].trim()
        val code = match.groupValues[2].trimEnd()
        result.add(MarkdownSegment.CodeBlock(lang, code))
        lastIdx = match.range.last + 1
    }
    if (lastIdx < raw.length) {
        val tail = raw.substring(lastIdx).trim()
        if (tail.isNotEmpty()) result.add(MarkdownSegment.TextBlock(tail))
    }
    return result
}

/**
 * Expandable Tool Execution Card displaying tool name, agent role badge, duration, and output.
 */
@Composable
fun ToolExecutionCard(
    record: ToolExecutionRecord,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = when (record.status) {
        "COMPLETED" -> EmeraldAccent
        "FAILED", "DENIED" -> ErrorRed
        else -> AmberAccent
    }

    Surface(
        color = Color(0xFF0D0D12),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (record.status == "COMPLETED") Icons.Default.CheckCircle else Icons.Default.Build,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = record.toolName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = VioletAccent.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = record.agentRole.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = VioletAccent,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${record.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle tool details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Args: ${record.argumentsJson}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = record.output.take(1500),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFFD4D4D8)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Unified Diff Viewer Card with color-coded additions (+ green) and deletions (- red).
 */
@Composable
fun UnifiedDiffCard(
    diff: FileDiffPreview,
    onOpenInEditor: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    Surface(
        color = Color(0xFF0A0E14),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = diff.relativePath,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "+${diff.additions}",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldAccent
                    )
                    Text(
                        text = "-${diff.deletions}",
                        style = MaterialTheme.typography.labelMedium,
                        color = RoseAccent
                    )
                    if (onOpenInEditor != null) {
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onOpenInEditor(diff.relativePath) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black)
                        .padding(8.dp)
                ) {
                    diff.unifiedDiff.lines().take(60).forEach { line ->
                        val bg = when {
                            line.startsWith("+") && !line.startsWith("+++") -> EmeraldAccent.copy(alpha = 0.14f)
                            line.startsWith("-") && !line.startsWith("---") -> ErrorRed.copy(alpha = 0.14f)
                            line.startsWith("@@") -> CyanAccent.copy(alpha = 0.12f)
                            else -> Color.Transparent
                        }
                        val fg = when {
                            line.startsWith("+") && !line.startsWith("+++") -> EmeraldAccent
                            line.startsWith("-") && !line.startsWith("---") -> RoseAccent
                            line.startsWith("@@") -> CyanAccent
                            else -> Color(0xFFA1A1AA)
                        }
                        Text(
                            text = line,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = fg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bg)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Security Permission Approval Modal before destructive or gated tool execution.
 */
@Composable
fun PermissionApprovalDialog(
    request: PendingPermissionRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        containerColor = Color(0xFF111116),
        titleContentColor = Color.White,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Security Permission Required",
                tint = AmberAccent,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Approve Tool Execution?",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${request.agentRole.title} requests permission to execute:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Tool: ${request.toolName} (${request.category.label})",
                            style = MaterialTheme.typography.titleSmall,
                            color = AmberAccent
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = request.argumentsJson,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
                request.diffPreview?.let { diff ->
                    UnifiedDiffCard(diff = diff)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent, contentColor = Color.Black),
                modifier = Modifier.testTag("approve_permission_button")
            ) {
                Text("Approve & Execute", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDeny,
                border = BorderStroke(1.dp, ErrorRed),
                modifier = Modifier.testTag("deny_permission_button")
            ) {
                Text("Deny", color = ErrorRed)
            }
        }
    )
}
