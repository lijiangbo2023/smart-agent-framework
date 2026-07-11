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

/**
 * Sliding window memory
 *
 * @description Memory management based on message count and time window. Automatically evicts
 *              expired and excess messages, and supports persistence to Session.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
public class SlidingWindowMemory implements Memory {

    private static final String SESSION_KEY = "memory_messages";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final List<Msg> messages = new ArrayList<>();
    private final int maxMessages;
    private final Duration timeWindow;

    /**
     * Construct a sliding window memory
     *
     * @description Initializes the maximum message count and time window
     * @param maxMessages maximum number of messages to retain
     * @param timeWindow time window; messages beyond the window will be evicted
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public SlidingWindowMemory(int maxMessages, Duration timeWindow) {
        this.maxMessages = maxMessages;
        this.timeWindow = timeWindow;
    }

    /**
     * Add a message
     *
     * @description Adds a message to memory and applies the eviction policy
     * @param message the message to add
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public synchronized void addMessage(Msg message) {
        messages.add(message);
        evict();
    }

    /**
     * Get all valid messages
     *
     * @description Evicts expired messages and returns a copy of the remaining messages
     * @return list of messages
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public synchronized List<Msg> getMessages() {
        evict();
        return new ArrayList<>(messages);
    }

    /**
     * Delete a message at the specified position
     *
     * @description Deletes a message by index
     * @param index message index
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public synchronized void deleteMessage(int index) {
        if (index >= 0 && index < messages.size()) {
            messages.remove(index);
        }
    }

    /**
     * Clear all messages
     *
     * @description Removes all messages from memory
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public synchronized void clear() {
        messages.clear();
    }

    /**
     * Save memory to Session
     *
     * @description Persists the current valid messages to Session storage
     * @param session session storage
     * @param sessionKey session identifier
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public synchronized void saveTo(Session session, SessionKey sessionKey) {
        evict();
        session.save(sessionKey, SESSION_KEY, new ArrayList<>(messages));
    }

    /**
     * Load memory from Session
     *
     * @description Restores messages from Session storage and applies the eviction policy
     * @param session session storage
     * @param sessionKey session identifier
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public synchronized void loadFrom(Session session, SessionKey sessionKey) {
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
