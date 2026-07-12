package com.smart.agent.demo;

import com.smart.agent.rag.RagService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Code tools for CodeAgent — code analysis, formatting, and knowledge lookup.
 *
 * @description Toolset registered with the CodeAgent's Toolkit, providing code_review,
 *              format_code, explain_code, generate_unit_test, and knowledge_search tools.
 * @author Jiangbo Li
 * @date 2025-07-12
 * @version 1.0
 */
public class CodeTools {

    private final RagService ragService;

    public CodeTools(RagService ragService) {
        this.ragService = ragService;
    }

    @Tool(name = "knowledge_search", description = "Search the knowledge base for technical documentation and best practices")
    public String knowledgeSearch(
            @ToolParam(name = "query", description = "Search query") String query) {
        try {
            String result = ragService.retrieve(query, 5);
            if (result == null || result.isEmpty()) {
                return "No knowledge base results found for: " + query;
            }
            return "Relevant documentation:\n" + result;
        } catch (Exception e) {
            return "Knowledge search error: " + e.getMessage();
        }
    }

    @Tool(name = "code_review", description = "Review code for potential issues including bugs, performance, and style")
    public String codeReview(
            @ToolParam(name = "code", description = "Code to review") String code,
            @ToolParam(name = "language", description = "Programming language, e.g. 'java', 'python', 'javascript'") String language) {
        StringBuilder report = new StringBuilder();
        report.append("## Code Review Report\n");
        report.append("**Language:** ").append(language).append("\n");
        report.append("**Lines:** ").append(code.split("\n").length).append("\n\n");

        // Basic static analysis
        if (code.contains("TODO") || code.contains("FIXME")) {
            report.append("- ⚠️ Found TODO/FIXME markers\n");
        }
        if (code.contains("System.out.println") || code.contains("console.log") || code.contains("print(")) {
            report.append("- 💡 Consider using a proper logging framework instead of print statements\n");
        }
        if (code.contains("catch (Exception") || code.contains("catch Exception")) {
            report.append("- ⚠️ Catching generic Exception — consider catching specific exceptions\n");
        }
        if (code.contains("Thread.sleep(")) {
            report.append("- ⚠️ Thread.sleep detected — consider using proper async patterns\n");
        }
        if (code.contains("new Thread(")) {
            report.append("- 💡 Manual thread creation — consider using thread pools\n");
        }
        if (language.equalsIgnoreCase("java") && !code.contains("@Override") && code.contains("public ")) {
            report.append("- 💡 Consider adding @Override annotations to overridden methods\n");
        }
        if (code.split("\n").length > 200) {
            report.append("- 💡 Code is quite long (200+ lines) — consider splitting into smaller functions\n");
        }

        report.append("\nPlease provide specific improvement suggestions based on the code content.");
        return report.toString();
    }

    @Tool(name = "format_code", description = "Format and beautify code with proper indentation hints")
    public String formatCode(
            @ToolParam(name = "code", description = "Code to format") String code,
            @ToolParam(name = "language", description = "Programming language") String language) {
        // Basic formatting: trim trailing spaces, normalize indentation
        String[] lines = code.split("\n");
        StringBuilder formatted = new StringBuilder();
        formatted.append("```").append(language).append("\n");
        for (String line : lines) {
            formatted.append(line.stripTrailing()).append("\n");
        }
        formatted.append("```\n");
        return "Formatted code:\n" + formatted;
    }

    @Tool(name = "explain_code", description = "Analyze code structure and provide an explanation")
    public String explainCode(
            @ToolParam(name = "code", description = "Code to explain") String code,
            @ToolParam(name = "language", description = "Programming language") String language) {
        int functions = countMatches(code, "def ", "function ", "func ", "public .*\\(", "private .*\\(", "const .*=");
        int classes = countMatches(code, "class ", "interface ", "struct ", "enum ");
        int imports = countMatches(code, "import ", "from .* import", "require\\(", "using ");
        int comments = countMatches(code, "//", "#", "/\\*", "\\* ");

        return String.format("""
                ## Code Structure Analysis
                - **Language:** %s
                - **Lines:** %d
                - **Functions/Methods:** ~%d
                - **Classes/Structs:** ~%d
                - **Imports:** ~%d
                - **Comments:** ~%d

                Please review the actual code and provide a detailed explanation.
                """, language, code.split("\n").length, functions, classes, imports, comments);
    }

    @Tool(name = "generate_unit_test", description = "Generate a unit test template for the given code")
    public String generateTest(
            @ToolParam(name = "code", description = "Source code to test") String code,
            @ToolParam(name = "language", description = "Programming language") String language) {
        String framework = switch (language.toLowerCase()) {
            case "java" -> "JUnit 5 + Mockito";
            case "python" -> "pytest";
            case "javascript", "typescript" -> "Jest";
            case "go" -> "testing";
            default -> "standard testing library";
        };
        return String.format("""
                ## Unit Test Template
                Generate unit tests using **%s** for the following code:

                ```%s
                %s
                ```

                Please create comprehensive tests covering:
                1. Normal/expected inputs
                2. Edge cases and boundary conditions
                3. Error/exception paths
                """, framework, language, code.substring(0, Math.min(2000, code.length())));
    }

    private int countMatches(String code, String... patterns) {
        int count = 0;
        for (String pattern : patterns) {
            count += (code.length() - code.replaceAll(pattern, "").length()) / Math.max(1, pattern.replaceAll("\\\\", "").length());
        }
        return Math.min(count, 50);
    }
}
