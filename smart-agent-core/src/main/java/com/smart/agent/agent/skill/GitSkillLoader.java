package com.smart.agent.agent.skill;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Git技能加载器
 *
 * @description 从Git仓库中加载技能定义，支持自动克隆、增量同步和本地缓存，通过解析SKILL.md文件构建AgentSkill对象
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Component
public class GitSkillLoader {

    private static final String SKILLS_SUB_DIR = "skills";
    private static final String SKILL_MD = "SKILL.md";

    private static final Pattern FRONTMATTER_PATTERN =
            Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);
    private static final Pattern YAML_FIELD_PATTERN =
            Pattern.compile("^(\\w[\\w-]*):\\s*(.+)", Pattern.MULTILINE);

    @Value("${skill.git.repo-url:}")
    private String repoUrl;

    @Value("${skill.git.token:}")
    private String token;

    @Value("${skill.git.username:oauth2}")
    private String username;

    @Value("${skill.git.branch:master}")
    private String branch;

    @Value("${skill.git.local-cache-dir:/tmp/smart-agent-skills}")
    private String localCacheDir;

    private Path repoRoot;

    private static final long FETCH_DEDUP_WINDOW_MS = 3000L;
    private volatile long lastFetchTimeMs;

    private final Map<String, AgentSkill> skillCache = new ConcurrentHashMap<>();
    private final Map<String, List<AgentSkill>> agentSkillCache = new ConcurrentHashMap<>();

    /**
     * 初始化Git技能加载器
     *
     * @description 在Bean初始化后自动执行，检查Git仓库配置并克隆远程仓库到本地缓存目录
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @PostConstruct
    public void init() {
        if (repoUrl == null || repoUrl.isBlank()) {
            log.info("GitSkillLoader disabled: skill.git.repo-url not configured");
            return;
        }
        this.repoRoot = Paths.get(localCacheDir);
        try {
            ensureCloned();
            log.info("GitSkillLoader initialized, repo={}, branch={}, local={}", repoUrl, branch, repoRoot);
        } catch (Exception e) {
            log.error("GitSkillLoader init failed, repo={}", repoUrl, e);
        }
    }

    /**
     * 根据技能名称加载单个技能
     *
     * @description 从Git仓库中查找指定名称的技能目录，优先从缓存获取，未命中则解析SKILL.md并构建AgentSkill对象
     * @param skillName 技能名称
     * @return 加载成功返回AgentSkill对象，技能不存在或加载失败返回null
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public AgentSkill loadSkill(String skillName) {
        if (repoRoot == null || skillName == null || skillName.isBlank()) {
            return null;
        }
        try {
            ensureCloned();
            syncIfRemoteChanged();

            AgentSkill cached = skillCache.get(skillName);
            if (cached != null) {
                return cached;
            }

            Path skillDir = resolveSkillDir(skillName);
            if (skillDir == null || !Files.isDirectory(skillDir)) {
                return null;
            }
            AgentSkill skill = buildSkillFromDir(skillDir);
            if (skill != null) {
                skillCache.put(skillName, skill);
            }
            return skill;
        } catch (Exception e) {
            log.error("Failed to load skill '{}' from git repo {}", skillName, repoUrl, e);
            return null;
        }
    }

    /**
     * 加载技能箱
     *
     * @description 根据Agent名称加载其关联的所有技能，并注册到SkillBox中
     * @param agentName Agent名称
     * @param toolkit 工具集
     * @return 包含该Agent所有技能的SkillBox对象
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public SkillBox loadSkillBox(String agentName, Toolkit toolkit) {
        SkillBox skillBox = new SkillBox(toolkit);
        loadSkillsByAgent(agentName).forEach(skillBox::registerSkill);
        return skillBox;
    }

    /**
     * 根据Agent名称加载技能列表
     *
     * @description 优先从Agent专属目录加载技能，若为空则回退到公共skills目录加载，结果会被缓存
     * @param agentName Agent名称
     * @return 技能列表，加载失败或无技能时返回空列表
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public List<AgentSkill> loadSkillsByAgent(String agentName) {
        if (agentName == null || agentName.isBlank()) {
            return List.of();
        }
        try {
            ensureCloned();
            syncIfRemoteChanged();

            List<AgentSkill> cached = agentSkillCache.get(agentName);
            if (cached != null) {
                return cached;
            }

            List<AgentSkill> result = new ArrayList<>();
            Path agentDir = repoRoot.resolve(agentName);
            if (Files.isDirectory(agentDir)) {
                result = scanSkillsInDir(agentDir);
            }

            if (result.isEmpty()) {
                Path fallbackDir = repoRoot.resolve(SKILLS_SUB_DIR);
                if (Files.isDirectory(fallbackDir)) {
                    result = scanSkillsInDir(fallbackDir);
                }
            }
            agentSkillCache.put(agentName, List.copyOf(result));
            return result;
        } catch (Exception e) {
            log.error("Failed to load skills for agent '{}' from git repo {}", agentName, repoUrl, e);
            return List.of();
        }
    }

    /**
     * 加载所有技能
     *
     * @description 扫描Git仓库中的skills目录（或仓库根目录），加载所有包含SKILL.md的技能目录
     * @return 所有技能的列表，加载失败时返回空列表
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public List<AgentSkill> loadAllSkills() {
        List<AgentSkill> result = new ArrayList<>();
        try {
            ensureCloned();
            syncIfRemoteChanged();

            Path scanRoot = repoRoot.resolve(SKILLS_SUB_DIR);
            if (!Files.isDirectory(scanRoot)) {
                scanRoot = repoRoot;
            }

            try (Stream<Path> children = Files.list(scanRoot)) {
                children.filter(Files::isDirectory)
                        .filter(p -> !p.getFileName().toString().startsWith("."))
                        .filter(p -> Files.isRegularFile(p.resolve(SKILL_MD)))
                        .sorted()
                        .forEach(skillDir -> {
                            AgentSkill skill = buildSkillFromDir(skillDir);
                            if (skill != null) {
                                result.add(skill);
                            }
                        });
            }
        } catch (Exception e) {
            log.error("Failed to load all skills from git repo {}", repoUrl, e);
        }
        return result;
    }

    private List<AgentSkill> scanSkillsInDir(Path dir) {
        List<AgentSkill> result = new ArrayList<>();
        try (Stream<Path> children = Files.list(dir)) {
            children.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(p -> Files.isRegularFile(p.resolve(SKILL_MD)))
                    .sorted()
                    .forEach(skillDir -> {
                        AgentSkill skill = buildSkillFromDir(skillDir);
                        if (skill != null) {
                            result.add(skill);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to scan directory {}: {}", dir, e.getMessage());
        }
        return result;
    }

    private AgentSkill buildSkillFromDir(Path skillDir) {
        try {
            Path skillMdPath = skillDir.resolve(SKILL_MD);
            if (!Files.isRegularFile(skillMdPath)) {
                return null;
            }

            String skillMdContent = Files.readString(skillMdPath, StandardCharsets.UTF_8);
            Map<String, String> frontmatter = parseFrontmatter(skillMdContent);
            String name = frontmatter.get("name");
            String description = frontmatter.get("description");
            if (name == null || name.isEmpty() || description == null || description.isEmpty()) {
                return null;
            }

            Map<String, String> resources = new HashMap<>();
            resources.put(SKILL_MD, skillMdContent);
            collectResources(skillDir, resources);

            return AgentSkill.builder()
                    .name(name)
                    .description(description)
                    .skillContent(skillMdContent)
                    .resources(resources)
                    .source("git")
                    .build();
        } catch (Exception e) {
            log.error("Failed to build skill from dir: {}", skillDir, e);
            return null;
        }
    }

    private synchronized void ensureCloned() throws Exception {
        if (repoRoot == null) return;
        if (Files.isDirectory(repoRoot.resolve(".git"))) {
            return;
        }
        if (Files.exists(repoRoot)) {
            deleteRecursively(repoRoot);
        }
        Files.createDirectories(repoRoot.getParent());
        try (Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(repoRoot.toFile())
                .setBranch(branch)
                .setCredentialsProvider(buildCredentials())
                .call()) {
            log.info("Skill repo cloned, head={}", git.getRepository().resolve("HEAD"));
        }
    }

    private synchronized void syncIfRemoteChanged() {
        long now = System.currentTimeMillis();
        if (now - lastFetchTimeMs < FETCH_DEDUP_WINDOW_MS) {
            return;
        }
        try (Git git = Git.open(repoRoot.toFile())) {
            Repository repository = git.getRepository();
            ObjectId localHead = repository.resolve("HEAD");

            FetchResult fetchResult = git.fetch()
                    .setCredentialsProvider(buildCredentials())
                    .call();
            lastFetchTimeMs = System.currentTimeMillis();
            Ref remoteRef = fetchResult.getAdvertisedRef("refs/heads/" + branch);
            ObjectId remoteHead = remoteRef == null ? null : remoteRef.getObjectId();

            if (remoteHead == null || remoteHead.equals(localHead)) {
                return;
            }
            git.pull()
                    .setRemoteBranchName(branch)
                    .setCredentialsProvider(buildCredentials())
                    .call();
            skillCache.clear();
            agentSkillCache.clear();
            log.info("Skill repo pulled to {}, cache invalidated", remoteHead);
        } catch (Exception e) {
            log.warn("Sync skill repo failed, using local cache: {}", e.getMessage());
        }
    }

    private Path resolveSkillDir(String skillName) {
        try (Stream<Path> topDirs = Files.list(repoRoot)) {
            Path found = topDirs
                    .filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(p -> !SKILLS_SUB_DIR.equals(p.getFileName().toString()))
                    .map(p -> p.resolve(skillName))
                    .filter(Files::isDirectory)
                    .findFirst()
                    .orElse(null);
            if (found != null) return found;
        } catch (IOException e) {
            log.warn("Failed to scan repo root for skill '{}': {}", skillName, e.getMessage());
        }
        Path inSkillsSubDir = repoRoot.resolve(SKILLS_SUB_DIR).resolve(skillName);
        if (Files.isDirectory(inSkillsSubDir)) return inSkillsSubDir;
        Path inRoot = repoRoot.resolve(skillName);
        if (Files.isDirectory(inRoot)) return inRoot;
        return null;
    }

    private void collectResources(Path skillDir, Map<String, String> resources) throws IOException {
        try (Stream<Path> stream = Files.walk(skillDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(p -> {
                        String relative = skillDir.relativize(p).toString().replace('\\', '/');
                        if (SKILL_MD.equals(relative)) return;
                        try {
                            resources.put(relative, Files.readString(p, StandardCharsets.UTF_8));
                        } catch (IOException ex) {
                            log.warn("Read resource failed: {}", p, ex);
                        }
                    });
        }
    }

    private UsernamePasswordCredentialsProvider buildCredentials() {
        if (token == null || token.isEmpty()) return null;
        String effectiveUsername = (username == null || username.isEmpty()) ? "oauth2" : username;
        return new UsernamePasswordCredentialsProvider(effectiveUsername, token);
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ex) { log.warn("Delete failed: {}", p); }
            });
        }
    }

    private static Map<String, String> parseFrontmatter(String content) {
        Matcher frontmatterMatcher = FRONTMATTER_PATTERN.matcher(content);
        if (!frontmatterMatcher.find()) return new HashMap<>();
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
            if (line.isEmpty()) continue;
            if (line.startsWith("  ") || line.startsWith("\t")) {
                if (!result.isEmpty()) result.append(" ");
                result.append(line.trim());
            } else {
                break;
            }
        }
        return result.toString();
    }
}
