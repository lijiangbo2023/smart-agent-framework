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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据库会话实现
 *
 * @description 基于MySQL数据库的Session实现，提供Agent会话状态的持久化存储。支持消息条数限制和时间窗口过滤，确保上下文不会无限增长。通过组合键（userId + sessionId）隔离不同用户和会话的数据
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
public class DatabaseSession implements Session {

    private static final String DEFAULT_SESSION_ID = "default";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final AgentSessionMapper mapper;
    private final String agentName;
    private final int maxMessages;
    private final Duration timeWindow;
    private final SessionIdentity fixedIdentity;

    /**
     * 构造数据库会话实例
     *
     * @description 创建不绑定固定组合键的数据库会话实例，会话标识从每次操作的SessionKey中动态解析
     * @param mapper 会话数据访问Mapper
     * @param agentName Agent名称
     * @param maxMessages 最大消息条数
     * @param timeWindow 消息有效时间窗口
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public DatabaseSession(AgentSessionMapper mapper, String agentName, int maxMessages, Duration timeWindow) {
        this(mapper, agentName, maxMessages, timeWindow, null);
    }

    /**
     * 构造数据库会话实例（带固定组合键）
     *
     * @description 创建绑定固定组合键的数据库会话实例，所有操作使用预设的compositeKey而非从SessionKey解析
     * @param mapper 会话数据访问Mapper
     * @param agentName Agent名称
     * @param maxMessages 最大消息条数
     * @param timeWindow 消息有效时间窗口
     * @param compositeKey 固定组合键（userId + separator + sessionId），为null时退化为动态解析模式
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public DatabaseSession(AgentSessionMapper mapper, String agentName, int maxMessages, Duration timeWindow, String compositeKey) {
        this.mapper = mapper;
        this.agentName = agentName;
        this.maxMessages = maxMessages;
        this.timeWindow = timeWindow;
        this.fixedIdentity = compositeKey != null ? parseCompositeKey(compositeKey) : null;
    }

    /**
     * 保存单个状态对象
     *
     * @description 将单个State对象序列化为JSON并持久化到数据库，若已存在则更新
     * @param sessionKey 会话键
     * @param key 数据键
     * @param value 状态对象
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
     * 保存状态对象列表
     *
     * @description 将State对象列表序列化为JSON并持久化到数据库，若已存在则更新
     * @param sessionKey 会话键
     * @param key 数据键
     * @param values 状态对象列表
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
     * 获取单个状态对象
     *
     * @description 从数据库查询指定键的状态数据，反序列化为目标类型。超出时间窗口的数据将返回空
     * @param sessionKey 会话键
     * @param key 数据键
     * @param type 目标类型
     * @return 状态对象的Optional包装，不存在或已过期时返回空
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public <T extends State> Optional<T> get(SessionKey sessionKey, String key, Class<T> type) {
        SessionIdentity identity = resolveSessionIdentity(sessionKey);
        AgentSessionEntity entity = selectOne(identity, key);
        if (entity == null || entity.getDataValue() == null) {
            return Optional.empty();
        }
        if (!isWithinTimeWindow(entity)) {
            return Optional.empty();
        }
        T result = JsonUtils.getJsonCodec().fromJson(entity.getDataValue(), type);
        return Optional.ofNullable(result);
    }

    /**
     * 获取状态对象列表
     *
     * @description 从数据库查询指定键的状态列表数据，按时间窗口和最大消息数进行过滤和截断，从最新消息向前取最多maxMessages条未过期消息
     * @param sessionKey 会话键
     * @param key 数据键
     * @param itemType 列表元素类型
     * @return 过滤后的状态对象列表，不存在时返回空列表
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public <T extends State> List<T> getList(SessionKey sessionKey, String key, Class<T> itemType) {
        SessionIdentity identity = resolveSessionIdentity(sessionKey);
        AgentSessionEntity entity = selectOne(identity, key);
        if (entity == null || entity.getDataValue() == null) {
            return List.of();
        }

        List<T> allItems = JsonUtils.getJsonCodec().fromJson(
                entity.getDataValue(),
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
     * 判断会话是否存在
     *
     * @description 检查数据库中是否存在指定会话键对应的会话数据
     * @param sessionKey 会话键
     * @return true表示会话存在，false表示不存在
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
     * 删除整个会话
     *
     * @description 删除数据库中指定会话键对应的所有会话数据
     * @param sessionKey 会话键
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
    }

    /**
     * 删除会话中指定键的数据
     *
     * @description 删除数据库中指定会话键和数据键对应的单条会话数据
     * @param sessionKey 会话键
     * @param key 数据键
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
    }

    /**
     * 列出所有会话键
     *
     * @description 查询当前Agent下所有已存储的会话键，按userId和sessionId分组去重
     * @return 会话键集合
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
        AgentSessionEntity existing = selectOne(identity, dataKey);
        Date now = new Date();
        if (existing != null) {
            existing.setDataValue(dataValue);
            existing.setGmtModified(now);
            mapper.update(existing,
                    new LambdaQueryWrapper<AgentSessionEntity>()
                            .eq(AgentSessionEntity::getUserId, identity.userId)
                            .eq(AgentSessionEntity::getAgentName, agentName)
                            .eq(AgentSessionEntity::getSessionId, identity.sessionId)
                            .eq(AgentSessionEntity::getDataKey, dataKey)
            );
        } else {
            AgentSessionEntity entity = AgentSessionEntity.builder()
                    .userId(identity.userId)
                    .agentName(agentName)
                    .sessionId(identity.sessionId)
                    .dataKey(dataKey)
                    .dataValue(dataValue)
                    .gmtCreate(now)
                    .gmtModified(now)
                    .build();
            mapper.insert(entity);
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

    private record SessionIdentity(String userId, String sessionId) {
    }

    /**
     * 构建会话组合键
     *
     * @description 将userId和sessionId拼接为组合键，sessionId为空时使用默认值
     * @param userId 用户ID
     * @param sessionId 会话ID，为null或空时使用默认值"default"
     * @return 组合键字符串
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
