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
import java.util.stream.Stream;

/**
 * Git skill loader
 *
 * @description Loads skill definitions from a Git repository, supports automatic cloning, incremental sync
 *              and local caching, builds AgentSkill objects by parsing SKILL.md files
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Component
public class GitSkillLoader {

    private static final String SKILLS_SUB_DIR = "skills";
    private static final String SKILL_MD = "SKILL.md";

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
     * Initialize the Git skill loader
     *
     * @description Executed automatically after Bean initialization, checks Git repository configuration
     *              and clones the remote repository to the local cache directory
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
     * Load a single skill by name
     *
     * @description Searches the Git repository for a skill directory with the given name,
     *              prioritizes cache lookup; on cache miss, parses SKILL.md and builds an AgentSkill object
     * @param skillName skill name
     * @return AgentSkill object on success, or null if the skill does not exist or loading fails
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public AgentSkill loadSkill(String skillName) {
        if (repoRoot == null || skillName == null || skillName.isBlank()) {
            return null;
        }
        if (!isSafeName(skillName)) {
            log.warn("Rejected unsafe skill name: {}", skillName);
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
            if (skillDir == null || !isWithinRepoRoot(skillDir) || !Files.isDirectory(skillDir)) {
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
     * Load a skill box
     *
     * @description Loads all skills associated with the given agent name and registers them into a SkillBox
     * @param agentName agent name
     * @param toolkit toolkit
     * @return SkillBox containing all skills for the agent
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public SkillBox loadSkillBox(String agentName, Toolkit toolkit) {
        SkillBox skillBox = new SkillBox(toolkit);
        loadSkillsByAgent(agentName).forEach(skillBox::registerSkill);
        return skillBox;
    }

    /**
     * Load skills by agent name
     *
     * @description Prioritizes loading skills from the agent-specific directory; falls back to the shared
     *              skills directory if empty. Results are cached
     * @param agentName agent name
     * @return list of skills, or empty list if loading fails or no skills are found
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    /**
     * Validate that a name parameter does not contain path traversal sequences.
     *
     * @param name the name to validate (skill name or agent name)
     * @return true if the name is safe to use in path operations
     */
    private boolean isSafeName(String name) {
        if (name == null || name.isBlank()) return false;
        if (name.contains("..") || name.contains("/") || name.contains("\\")) return false;
        if (name.contains("\0")) return false;
        return name.matches("[a-zA-Z0-9_\\-.]+");
    }

    /**
     * Validate that a resolved path is within the repository root.
     *
     * @param resolved the path to validate
     * @return true if the path is safely contained within repoRoot
     */
    private boolean isWithinRepoRoot(Path resolved) {
        try {
            return resolved.normalize().startsWith(repoRoot.normalize());
        } catch (Exception e) {
            return false;
        }
    }

    public List<AgentSkill> loadSkillsByAgent(String agentName) {
        if (agentName == null || agentName.isBlank()) {
            return List.of();
        }
        if (!isSafeName(agentName)) {
            log.warn("Rejected unsafe agent name: {}", agentName);
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
            Path agentDir = repoRoot.resolve(agentName).normalize();
            if (isWithinRepoRoot(agentDir) && Files.isDirectory(agentDir)) {
                result = scanSkillsInDir(agentDir);
            }

            if (result.isEmpty()) {
                Path fallbackDir = repoRoot.resolve(SKILLS_SUB_DIR).normalize();
                if (isWithinRepoRoot(fallbackDir) && Files.isDirectory(fallbackDir)) {
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
     * Load all skills
     *
     * @description Scans the skills directory (or repository root) in the Git repository,
     *              loading all skill directories that contain a SKILL.md file
     * @return list of all skills, or empty list if loading fails
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
            Map<String, String> frontmatter = SkillFrontmatterParser.parse(skillMdContent);
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
                    .map(p -> p.resolve(skillName).normalize())
                    .filter(this::isWithinRepoRoot)
                    .filter(Files::isDirectory)
                    .findFirst()
                    .orElse(null);
            if (found != null) return found;
        } catch (IOException e) {
            log.warn("Failed to scan repo root for skill '{}': {}", skillName, e.getMessage());
        }
        Path inSkillsSubDir = repoRoot.resolve(SKILLS_SUB_DIR).resolve(skillName).normalize();
        if (isWithinRepoRoot(inSkillsSubDir) && Files.isDirectory(inSkillsSubDir)) return inSkillsSubDir;
        Path inRoot = repoRoot.resolve(skillName).normalize();
        if (isWithinRepoRoot(inRoot) && Files.isDirectory(inRoot)) return inRoot;
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
}
