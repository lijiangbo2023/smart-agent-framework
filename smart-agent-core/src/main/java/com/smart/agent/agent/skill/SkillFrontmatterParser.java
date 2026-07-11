package com.smart.agent.agent.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill YAML Frontmatter parser
 *
 * @description Parses YAML frontmatter metadata from Markdown files,
 *              extracting fields such as name and description. Shared by CommonSkillLoader and GitSkillLoader.
 * @author Jiangbo Li
 * @date 2026-06-16
 * @version 1.0
 */
public final class SkillFrontmatterParser {

    private static final Pattern FRONTMATTER_PATTERN =
            Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);

    private static final Pattern YAML_FIELD_PATTERN =
            Pattern.compile("^(\\w[\\w-]*):\\s*(.+)", Pattern.MULTILINE);

    private SkillFrontmatterParser() {
    }

    /**
     * Parse YAML frontmatter from Markdown content
     *
     * @description Extracts the YAML frontmatter block delimited by --- from Markdown text,
     *              and parses it into a key-value Map. Supports quoted values and YAML multiline
     *              folding syntax (&gt; / &gt;-).
     * @param content full content of the Markdown file
     * @return key-value Map of frontmatter fields, or empty Map if no frontmatter is found
     * @author Jiangbo Li
     * @date 2026-06-16
     */
    public static Map<String, String> parse(String content) {
        Matcher frontmatterMatcher = FRONTMATTER_PATTERN.matcher(content);
        if (!frontmatterMatcher.find()) {
            return new HashMap<>();
        }

        String yamlBlock = frontmatterMatcher.group(1);
        Map<String, String> fields = new HashMap<>();
        Matcher fieldMatcher = YAML_FIELD_PATTERN.matcher(yamlBlock);
        while (fieldMatcher.find()) {
            String key = fieldMatcher.group(1).trim();
            String value = fieldMatcher.group(2).trim();
            if (value.startsWith(">-") || value.startsWith(">")) {
                int nextLineStart = fieldMatcher.end();
                value = extractMultilineYamlValue(yamlBlock, nextLineStart);
            } else if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            fields.put(key, value);
        }
        return fields;
    }

    private static String extractMultilineYamlValue(String yamlBlock, int startIndex) {
        StringBuilder result = new StringBuilder();
        String[] lines = yamlBlock.substring(startIndex).split("\\n");
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("  ") || line.startsWith("\t")) {
                if (!result.isEmpty()) {
                    result.append(" ");
                }
                result.append(line.trim());
            } else {
                break;
            }
        }
        return result.toString();
    }
}
