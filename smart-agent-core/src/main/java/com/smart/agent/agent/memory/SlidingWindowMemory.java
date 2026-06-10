package com.smart.agent.agent.memory;

import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.session.Session;
import io.agentscope.core.state.SessionKey;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 滑动窗口记忆
 *
 * @description 基于消息数量和时间窗口的记忆管理，自动淘汰过期和超量消息，支持持久化到 Session
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
public class SlidingWindowMemory implements Memory {

    private static final String SESSION_KEY = "memory_messages";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final List<Msg> messages = new CopyOnWriteArrayList<>();
    private final int maxMessages;
    private final Duration timeWindow;

    /**
     * 构造滑动窗口记忆
     *
     * @description 初始化最大消息数和时间窗口
     * @param maxMessages 最大保留消息数
     * @param timeWindow 时间窗口，超出窗口的消息将被淘汰
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public SlidingWindowMemory(int maxMessages, Duration timeWindow) {
        this.maxMessages = maxMessages;
        this.timeWindow = timeWindow;
    }

    /**
     * 添加消息
     *
     * @description 添加一条消息到记忆，并执行淘汰策略
     * @param message 消息对象
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public void addMessage(Msg message) {
        messages.add(message);
        evict();
    }

    /**
     * 获取所有有效消息
     *
     * @description 淘汰过期消息后返回剩余消息的副本
     * @return 消息列表
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public List<Msg> getMessages() {
        evict();
        return new ArrayList<>(messages);
    }

    /**
     * 删除指定位置的消息
     *
     * @description 根据索引删除消息
     * @param index 消息索引
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public void deleteMessage(int index) {
        if (index >= 0 && index < messages.size()) {
            messages.remove(index);
        }
    }

    /**
     * 清空所有消息
     *
     * @description 清除记忆中的全部消息
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public void clear() {
        messages.clear();
    }

    /**
     * 保存记忆到 Session
     *
     * @description 将当前有效消息持久化到 Session 存储
     * @param session 会话存储
     * @param sessionKey 会话标识
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public void saveTo(Session session, SessionKey sessionKey) {
        evict();
        session.save(sessionKey, SESSION_KEY, new ArrayList<>(messages));
    }

    /**
     * 从 Session 加载记忆
     *
     * @description 从 Session 存储中恢复消息，并执行淘汰策略
     * @param session 会话存储
     * @param sessionKey 会话标识
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public void loadFrom(Session session, SessionKey sessionKey) {
        List<Msg> loaded = session.getList(sessionKey, SESSION_KEY, Msg.class);
        messages.clear();
        messages.addAll(loaded);
        int beforeSize = messages.size();
        evict();
        int evicted = beforeSize - messages.size();
        if (evicted > 0) {
            log.info("Loaded {} messages, evicted {} expired/overflow messages", beforeSize, evicted);
        }
    }

    private void evict() {
        LocalDateTime cutoff = LocalDateTime.now().minus(timeWindow);
        messages.removeIf(msg -> isExpired(msg, cutoff));
        while (messages.size() > maxMessages) {
            messages.remove(0);
        }
    }

    private boolean isExpired(Msg msg, LocalDateTime cutoff) {
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
}
