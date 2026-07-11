package com.smart.agent.agent.session;

import com.smart.agent.constant.AgentConstants;
import com.smart.agent.persistence.entity.AgentSessionEntity;
import com.smart.agent.persistence.mapper.AgentSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.agentscope.core.session.Session;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.SimpleSessionKey;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Database-backed session implementation
 *
 * @description MySQL-based Session implementation providing persistent storage for Agent session state. Supports message count limits and time window filtering to prevent unbounded context growth. Data is isolated by composite key (userId + sessionId) across different users and sessions.
 *              When Redis is configured, Read-Through caching is automatically enabled: read operations query Redis cache first, falling back to MySQL on cache miss and writing back to cache; write and delete operations automatically invalidate Redis cache.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
public class DatabaseSession implements Session {

    private static final String DEFAULT_SESSION_ID = "default";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String REDIS_KEY_PREFIX = "session:";

    private final AgentSessionMapper mapper;
    private final String agentName;
    private final int maxMessages;
    private final Duration timeWindow;
    private final SessionIdentity fixedIdentity;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Constructs a database session instance
     *
     * @description Creates a database session instance without a fixed composite key; session identity is dynamically resolved from the SessionKey on each operation
     * @param mapper session data access mapper
     * @param agentName agent name
     * @param maxMessages maximum number of messages
     * @param timeWindow message validity time window
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public DatabaseSession(AgentSessionMapper mapper, String agentName, int maxMessages, Duration timeWindow) {
        this(mapper, agentName, maxMessages, timeWindow, null, null);
    }

    /**
     * Constructs a database session instance with a fixed composite key
     *
     * @description Creates a database session instance bound to a fixed composite key; all operations use the preset compositeKey rather than resolving from SessionKey
     * @param mapper session data access mapper
     * @param agentName agent name
     * @param maxMessages maximum number of messages
     * @param timeWindow message validity time window
     * @param compositeKey fixed composite key (userId + separator + sessionId); when null, falls back to dynamic resolution mode
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public DatabaseSession(AgentSessionMapper mapper, String agentName, int maxMessages, Duration timeWindow, String compositeKey) {
        this(mapper, agentName, maxMessages, timeWindow, compositeKey, null);
    }

    /**
     * Constructs a database session instance with Redis caching
     *
     * @description Creates a database session instance with Redis caching support; reads query Redis first, writes automatically invalidate cache
     * @param mapper session data access mapper
     * @param agentName agent name
     * @param maxMessages maximum number of messages
     * @param timeWindow message validity time window
     * @param compositeKey fixed composite key (userId + separator + sessionId); when null, falls back to dynamic resolution mode
     * @param redisTemplate Redis template; when null, falls back to pure MySQL mode
     * @author Jiangbo Li
     * @date 2026-06-16
     */
    public DatabaseSession(AgentSessionMapper mapper, String agentName, int maxMessages, Duration timeWindow,
                           String compositeKey, RedisTemplate<String, String> redisTemplate) {
        this.mapper = mapper;
        this.agentName = agentName;
        this.maxMessages = maxMessages;
        this.timeWindow = timeWindow;
        this.fixedIdentity = compositeKey != null ? parseCompositeKey(compositeKey) : null;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Saves a single state object
     *
     * @description Serializes a single State object to JSON and persists it to the database, updating if it already exists.
     *              Automatically invalidates the corresponding Redis cache after write to ensure the next read retrieves the latest data.
     * @param sessionKey session key
     * @param key data key
     * @param value state object
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public void save(SessionKey sessionKey, String key, State value) {
        SessionIdentity identity = resolveSessionIdentity(sessionKey);
        String json = JsonUtils.getJsonCodec().toJson(value);
        upsert(identity, key, json);
    }

    /**
     * Saves a list of state objects
     *
     * @description Serializes a list of State objects to JSON and persists it to the database, updating if it already exists.
     *              Automatically invalidates the corresponding Redis cache after write to ensure the next read retrieves the latest data.
     * @param sessionKey session key
     * @param key data key
     * @param values list of state objects
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public void save(SessionKey sessionKey, String key, List<? extends State> values) {
        SessionIdentity identity = resolveSessionIdentity(sessionKey);
        String json = JsonUtils.getJsonCodec().toJson(values);
        upsert(identity, key, json);
    }

    /**
     * Retrieves a single state object
     *
     * @description Queries Redis cache first for the state data of the specified key; on cache miss, queries the database and writes back to Redis cache (Read-Through strategy).
     *              Deserializes to the target type; data outside the time window returns empty. Automatically degrades to direct database query when Redis is unavailable.
     * @param sessionKey session key
     * @param key data key
     * @param type target type
     * @return Optional wrapper of the state object; empty if not found or expired
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public <T extends State> Optional<T> get(SessionKey sessionKey, String key, Class<T> type) {
        SessionIdentity identity = resolveSessionIdentity(sessionKey);

        // Try Redis cache first
        if (redisTemplate != null) {
            try {
                String cacheKey = buildRedisKey(identity, key);
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null && !cached.isEmpty()) {
                    T result = JsonUtils.getJsonCodec().fromJson(cached, type);
                    log.debug("Session cache hit: {}", cacheKey);
                    return Optional.ofNullable(result);
                }
            } catch (Exception e) {
                log.warn("Session cache read failed: {}", e.getMessage());
            }
        }

        AgentSessionEntity entity = selectOne(identity, key);
        if (entity == null || entity.getDataValue() == null) {
            return Optional.empty();
        }
        if (!isWithinTimeWindow(entity)) {
            return Optional.empty();
        }

        // Write back to Redis cache
        if (redisTemplate != null) {
            try {
                String cacheKey = buildRedisKey(identity, key);
                long ttlMinutes = timeWindow.toMinutes() > 0 ? timeWindow.toMinutes() : 30;
                redisTemplate.opsForValue().set(cacheKey, entity.getDataValue(), ttlMinutes, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("Session cache write failed: {}", e.getMessage());
            }
        }

        T result = JsonUtils.getJsonCodec().fromJson(entity.getDataValue(), type);
        return Optional.ofNullable(result);
    }

    /**
     * Retrieves a list of state objects
     *
     * @description Queries Redis cache first for the state list data of the specified key; on cache miss, queries the database and writes back to Redis cache (Read-Through strategy).
     *              Filters and truncates by time window and maximum message count, taking up to maxMessages unexpired messages from newest to oldest.
     *              Automatically degrades to direct database query when Redis is unavailable.
     * @param sessionKey session key
     * @param key data key
     * @param itemType list element type
     * @return filtered list of state objects; empty list if not found
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public <T extends State> List<T> getList(SessionKey sessionKey, String key, Class<T> itemType) {
        SessionIdentity identity = resolveSessionIdentity(sessionKey);
        String dataValue = null;

        // Try Redis cache first
        if (redisTemplate != null) {
            try {
                String cacheKey = buildRedisKey(identity, key);
                dataValue = redisTemplate.opsForValue().get(cacheKey);
                if (dataValue != null && !dataValue.isEmpty()) {
                    log.debug("Session cache hit: {}", cacheKey);
                }
            } catch (Exception e) {
                log.warn("Session cache read failed: {}", e.getMessage());
                dataValue = null;
            }
        }

        // Fallback to MySQL
        if (dataValue == null || dataValue.isEmpty()) {
            AgentSessionEntity entity = selectOne(identity, key);
            if (entity == null || entity.getDataValue() == null) {
                return List.of();
            }
            dataValue = entity.getDataValue();

            // Write back to Redis cache
            if (redisTemplate != null) {
                try {
                    String cacheKey = buildRedisKey(identity, key);
                    long ttlMinutes = timeWindow.toMinutes() > 0 ? timeWindow.toMinutes() : 30;
                    redisTemplate.opsForValue().set(cacheKey, dataValue, ttlMinutes, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.warn("Session cache write failed: {}", e.getMessage());
                }
            }
        }

        List<T> allItems = JsonUtils.getJsonCodec().fromJson(
                dataValue,
                new TypeReference<>() {
                    @Override
                    public java.lang.reflect.Type getType() {
                        return com.fasterxml.jackson.databind.type.TypeFactory.defaultInstance()
                                .constructCollectionType(List.class, itemType);
                    }
                }
        );

        if (allItems == null || allItems.isEmpty()) {
            return List.of();
        }

        LocalDateTime cutoff = LocalDateTime.now().minus(timeWindow);
        List<T> collected = new ArrayList<>(maxMessages);
        for (int i = allItems.size() - 1; i >= 0 && collected.size() < maxMessages; i--) {
            T item = allItems.get(i);
            if (item instanceof io.agentscope.core.message.Msg msg) {
                if (isMessageExpired(msg, cutoff)) {
                    break;
                }
                collected.add(item);
            } else {
                collected.add(item);
            }
        }

        Collections.reverse(collected);
        return collected;
    }

    private boolean isMessageExpired(io.agentscope.core.message.Msg msg, LocalDateTime cutoff) {
        String timestamp = msg.getTimestamp();
        if (timestamp == null || timestamp.isEmpty()) {
            return false;
        }
        try {
            LocalDateTime msgTime = LocalDateTime.parse(timestamp, TIMESTAMP_FORMAT);
            return msgTime.isBefore(cutoff);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks whether the session exists
     *
     * @description Checks whether session data for the given session key exists in the database
     * @param sessionKey session key
     * @return true if the session exists, false otherwise
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public boolean exists(SessionKey sessionKey) {
        SessionIdentity identity = resolveSessionIdentity(sessionKey);
        Long count = mapper.selectCount(
                new LambdaQueryWrapper<AgentSessionEntity>()
                        .eq(AgentSessionEntity::getUserId, identity.userId)
                        .eq(AgentSessionEntity::getAgentName, agentName)
                        .eq(AgentSessionEntity::getSessionId, identity.sessionId)
        );
        return count != null && count > 0;
    }

    /**
     * Deletes the entire session
     *
     * @description Deletes all session data for the given session key from the database and batch-invalidates all related Redis cache entries for this session.
     *              Uses pattern matching to find and delete all associated cache keys.
     * @param sessionKey session key
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public void delete(SessionKey sessionKey) {
        SessionIdentity identity = resolveSessionIdentity(sessionKey);
        mapper.delete(
                new LambdaQueryWrapper<AgentSessionEntity>()
                        .eq(AgentSessionEntity::getUserId, identity.userId)
                        .eq(AgentSessionEntity::getAgentName, agentName)
                        .eq(AgentSessionEntity::getSessionId, identity.sessionId)
        );

        // Invalidate all Redis cache for this session using SCAN instead of KEYS
        if (redisTemplate != null) {
            try {
                String pattern = REDIS_KEY_PREFIX + identity.userId + ":" + agentName + ":" + identity.sessionId + ":*";
                List<String> keysToDelete = new ArrayList<>();
                try (org.springframework.data.redis.core.Cursor<String> cursor = redisTemplate.scan(
                        org.springframework.data.redis.core.ScanOptions.scanOptions().match(pattern).count(100).build())) {
                    while (cursor.hasNext()) {
                        keysToDelete.add(cursor.next());
                    }
                }
                if (!keysToDelete.isEmpty()) {
                    redisTemplate.delete(keysToDelete);
                }
            } catch (Exception e) {
                log.warn("Session cache invalidate failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Deletes data for a specific key in the session
     *
     * @description Deletes the single session record matching the given session key and data key from the database, and invalidates the corresponding Redis cache.
     * @param sessionKey session key
     * @param key data key
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public void delete(SessionKey sessionKey, String key) {
        SessionIdentity identity = resolveSessionIdentity(sessionKey);
        mapper.delete(
                new LambdaQueryWrapper<AgentSessionEntity>()
                        .eq(AgentSessionEntity::getUserId, identity.userId)
                        .eq(AgentSessionEntity::getAgentName, agentName)
                        .eq(AgentSessionEntity::getSessionId, identity.sessionId)
                        .eq(AgentSessionEntity::getDataKey, key)
        );

        // Invalidate Redis cache
        if (redisTemplate != null) {
            try {
                String cacheKey = buildRedisKey(identity, key);
                redisTemplate.delete(cacheKey);
            } catch (Exception e) {
                log.warn("Session cache invalidate failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Lists all session keys
     *
     * @description Queries all stored session keys under the current agent, deduplicated by userId and sessionId grouping
     * @return set of session keys
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public Set<SessionKey> listSessionKeys() {
        List<AgentSessionEntity> entities = mapper.selectList(
                new LambdaQueryWrapper<AgentSessionEntity>()
                        .select(AgentSessionEntity::getUserId, AgentSessionEntity::getSessionId)
                        .eq(AgentSessionEntity::getAgentName, agentName)
                        .groupBy(AgentSessionEntity::getUserId, AgentSessionEntity::getSessionId)
        );
        return entities.stream()
                .map(entity -> (SessionKey) SimpleSessionKey.of(
                        entity.getUserId() + AgentConstants.SESSION_COMPOSITE_KEY_SEPARATOR + entity.getSessionId()))
                .collect(Collectors.toSet());
    }

    private void upsert(SessionIdentity identity, String dataKey, String dataValue) {
        mapper.upsertSession(identity.userId, agentName, identity.sessionId, dataKey, dataValue);

        // Invalidate Redis cache
        if (redisTemplate != null) {
            try {
                String cacheKey = buildRedisKey(identity, dataKey);
                redisTemplate.delete(cacheKey);
            } catch (Exception e) {
                log.warn("Session cache invalidate failed: {}", e.getMessage());
            }
        }
    }

    private AgentSessionEntity selectOne(SessionIdentity identity, String dataKey) {
        return mapper.selectOne(
                new LambdaQueryWrapper<AgentSessionEntity>()
                        .eq(AgentSessionEntity::getUserId, identity.userId)
                        .eq(AgentSessionEntity::getAgentName, agentName)
                        .eq(AgentSessionEntity::getSessionId, identity.sessionId)
                        .eq(AgentSessionEntity::getDataKey, dataKey)
        );
    }

    private boolean isWithinTimeWindow(AgentSessionEntity entity) {
        if (entity.getGmtModified() == null) {
            return true;
        }
        Date cutoff = new Date(System.currentTimeMillis() - timeWindow.toMillis());
        return !entity.getGmtModified().before(cutoff);
    }

    private SessionIdentity resolveSessionIdentity(SessionKey sessionKey) {
        if (fixedIdentity != null) {
            return fixedIdentity;
        }
        String raw;
        if (sessionKey instanceof SimpleSessionKey simpleKey) {
            raw = simpleKey.sessionId();
        } else {
            raw = sessionKey.toString();
        }
        return parseCompositeKey(raw);
    }

    private static SessionIdentity parseCompositeKey(String compositeKey) {
        int separatorIndex = compositeKey.indexOf(AgentConstants.SESSION_COMPOSITE_KEY_SEPARATOR);
        if (separatorIndex > 0) {
            String userId = compositeKey.substring(0, separatorIndex);
            String sessionId = compositeKey.substring(separatorIndex + AgentConstants.SESSION_COMPOSITE_KEY_SEPARATOR.length());
            return new SessionIdentity(userId, sessionId.isEmpty() ? DEFAULT_SESSION_ID : sessionId);
        }
        return new SessionIdentity(compositeKey, DEFAULT_SESSION_ID);
    }

    private String buildRedisKey(SessionIdentity identity, String dataKey) {
        return REDIS_KEY_PREFIX + identity.userId + ":" + agentName + ":" + identity.sessionId + ":" + dataKey;
    }

    private record SessionIdentity(String userId, String sessionId) {
    }

    /**
     * Builds a session composite key
     *
     * @description Concatenates userId and sessionId into a composite key; uses default value when sessionId is empty
     * @param userId user ID
     * @param sessionId session ID; uses default value "default" when null or empty
     * @return composite key string
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static String buildSessionKey(String userId, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = DEFAULT_SESSION_ID;
        }
        return userId + AgentConstants.SESSION_COMPOSITE_KEY_SEPARATOR + sessionId;
    }
}
