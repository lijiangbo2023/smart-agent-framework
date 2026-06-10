package com.smart.agent.agent.skill;

import io.agentscope.core.skill.AgentSkill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用技能加载器
 *
 * @description 从classpath中加载技能定义文件(SKILL.md)，解析frontmatter元数据并构建AgentSkill对象
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
public class CommonSkillLoader {

    private static final String SKILL_DIR = "skills";

    private static final Pattern FRONTMATTER_PATTERN =
            Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);

    private static final Pattern YAML_FIELD_PATTERN =
            Pattern.compile("^(\\w[\\w-]*):\\s*(.+)", Pattern.MULTILINE);

    private CommonSkillLoader() {
    }

    /**
     * 根据技能名称加载技能
     *
     * @description 从classpath中查找指定名称的技能目录，读取SKILL.md文件并解析为AgentSkill对象，同时收集技能目录下的所有资源文件
     * @param skillName 技能名称，对应classpath下skills目录中的子目录名
     * @return 加载成功返回AgentSkill对象，技能不存在或加载失败返回null
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static AgentSkill loadSkill(String skillName) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String skillPath = SKILL_DIR + "/" + skillName + "/SKILL.md";
            Resource[] skillMdResources = resolver.getResources("classpath*:" + skillPath);

            if (skillMdResources.length == 0) {
                log.warn("Skill not found: {}", skillPath);
                return null;
            }

            return loadSingleSkill(skillMdResources[0], resolver);
        } catch (Exception e) {
            log.error("Failed to load skill '{}': {}", skillName, e.getMessage(), e);
            return null;
        }
    }

    private static AgentSkill loadSingleSkill(Resource skillMdResource, PathMatchingResourcePatternResolver resolver)
            throws IOException {
        String skillMdContent = readResource(skillMdResource);
        Map<String, String> frontmatter = parseFrontmatter(skillMdContent);

        String name = frontmatter.get("name");
        String description = frontmatter.get("description");
        if (name == null || name.isEmpty() || description == null || description.isEmpty()) {
            log.warn("Skill SKILL.md missing required 'name' or 'description' in frontmatter: {}",
                    skillMdResource.getURL());
            return null;
        }

        String skillDirPath = SKILL_DIR + "/" + name + "/";
        Map<String, String> resources = new HashMap<>();
        resources.put("SKILL.md", skillMdContent);

        try {
            Resource[] allResources = resolver.getResources("classpath*:" + skillDirPath + "**/*");
            for (Resource resource : allResources) {
                if (!resource.isReadable()) {
                    continue;
                }
                String resourceUrl = resource.getURL().toString();
                int skillDirIndex = resourceUrl.indexOf(skillDirPath);
                if (skillDirIndex >= 0) {
                    String relativePath = resourceUrl.substring(skillDirIndex + skillDirPath.length());
                    if (!relativePath.isEmpty() && !"SKILL.md".equals(relativePath)) {
                        resources.put(relativePath, readResource(resource));
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to load additional resources for skill '{}': {}", name, e.getMessage());
        }

        return AgentSkill.builder()
                .name(name)
                .description(description)
                .skillContent(skillMdContent)
                .resources(resources)
                .source("classpath")
                .build();
    }

    private static Map<String, String> parseFrontmatter(String content) {
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

    private static String readResource(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
