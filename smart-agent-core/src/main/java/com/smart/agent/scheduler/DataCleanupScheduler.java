package com.smart.agent.scheduler;

import com.smart.agent.persistence.mapper.AgentSessionMapper;
import com.smart.agent.service.AgentChatMessageService;
import com.smart.agent.util.RedisLockUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled task for cleaning up expired data.
 *
 * @description Cleans up expired sessions and message records daily at 3:00 AM.
 *              In multi-instance deployments, a Redis distributed lock (TTL 1 hour) ensures
 *              only one instance performs cleanup, avoiding concurrent DELETE contention on
 *              MySQL row locks. When Redis is not configured, each instance executes independently.
 * @author Jiangbo Li
 * @date 2026-06-18
 * @version 1.0
 */
@Slf4j
@Component
public class DataCleanupScheduler {

    private static final String CLEANUP_LOCK_KEY = "cleanup:lock";
    private static final int BATCH_SIZE = 2000;

    @Value("${cleanup.session.retention-days:7}")
    private int sessionRetentionDays;

    @Value("${cleanup.message.retention-days:90}")
    private int messageRetentionDays;

    private final AgentSessionMapper agentSessionMapper;
    private final AgentChatMessageService agentChatMessageService;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    public DataCleanupScheduler(AgentSessionMapper agentSessionMapper,
                                 AgentChatMessageService agentChatMessageService) {
        this.agentSessionMapper = agentSessionMapper;
        this.agentChatMessageService = agentChatMessageService;
    }

    /**
     * Scheduled cleanup of expired data.
     *
     * @description Runs daily at 3:00 AM. Uses Redis distributed lock in multi-instance deployments.
     * @author Jiangbo Li
     * @date 2026-06-16
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredData() {
        String lockValue = UUID.randomUUID().toString();
        boolean locked = false;
        if (redisTemplate != null) {
            try {
                Boolean result = redisTemplate.opsForValue().setIfAbsent(
                        CLEANUP_LOCK_KEY, lockValue, 1, TimeUnit.HOURS);
                if (!Boolean.TRUE.equals(result)) {
                    log.info("Cleanup skipped: another instance is running cleanup");
                    return;
                }
                locked = true;
            } catch (Exception e) {
                log.warn("Redis cleanup lock failed, proceeding without lock: {}", e.getMessage());
            }
        }

        try {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -sessionRetentionDays);
            Date sessionCutoff = cal.getTime();
            int sessionsDeleted = agentSessionMapper.deleteExpiredRecords(sessionCutoff);

            cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -messageRetentionDays);
            Date messageCutoff = cal.getTime();
            int messagesDeleted = 0;
            int deleted;
            do {
                deleted = agentChatMessageService.cleanupExpiredMessages(messageCutoff, BATCH_SIZE);
                messagesDeleted += deleted;
            } while (deleted >= BATCH_SIZE);

            log.info("Daily cleanup completed: sessions deleted={}, messages deleted={}",
                    sessionsDeleted, messagesDeleted);
        } catch (Exception e) {
            log.error("Scheduled cleanup failed", e);
        } finally {
            if (redisTemplate != null && locked) {
                try {
                    RedisLockUtils.unlock(redisTemplate, CLEANUP_LOCK_KEY, lockValue);
                } catch (Exception e) {
                    log.warn("Redis cleanup unlock failed: {}", e.getMessage());
                }
            }
        }
    }
}
