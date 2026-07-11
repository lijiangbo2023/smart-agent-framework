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

/**
 * Common skill loader
 *
 * @description Loads skill definition files (SKILL.md) from classpath, parses frontmatter metadata
 *              and builds AgentSkill objects
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
public class CommonSkillLoader {

    private static final String SKILL_DIR = "skills";

    private CommonSkillLoader() {
    }

    /**
     * Load a skill by name
     *
     * @description Searches classpath for a skill directory with the given name, reads the SKILL.md file
     *              and parses it into an AgentSkill object, while collecting all resource files under the skill directory
     * @param skillName skill name, corresponding to a subdirectory name under the skills directory on classpath
     * @return AgentSkill object on success, or null if the skill does not exist or loading fails
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
        Map<String, String> frontmatter = SkillFrontmatterParser.parse(skillMdContent);

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

    private static String readResource(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
