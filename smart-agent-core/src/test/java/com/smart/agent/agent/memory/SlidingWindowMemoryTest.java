package com.smart.agent.agent.memory;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowMemoryTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private SlidingWindowMemory memory;

    @BeforeEach
    void setUp() {
        memory = new SlidingWindowMemory(5, Duration.ofMinutes(30));
    }

    // ========== basic operations ==========

    @Test
    void addAndGet_singleMessage() {
        Msg msg = buildMsg("hello", LocalDateTime.now());
        memory.addMessage(msg);

        List<Msg> result = memory.getMessages();
        assertEquals(1, result.size());
        assertEquals("hello", result.get(0).getTextContent());
    }

    @Test
    void getMessages_returnsDefensiveCopy() {
        memory.addMessage(buildMsg("msg1", LocalDateTime.now()));
        List<Msg> list1 = memory.getMessages();
        List<Msg> list2 = memory.getMessages();
        assertNotSame(list1, list2);
    }

    @Test
    void clear_removesAll() {
        memory.addMessage(buildMsg("a", LocalDateTime.now()));
        memory.addMessage(buildMsg("b", LocalDateTime.now()));
        memory.clear();
        assertTrue(memory.getMessages().isEmpty());
    }

    @Test
    void deleteMessage_validIndex() {
        memory.addMessage(buildMsg("a", LocalDateTime.now()));
        memory.addMessage(buildMsg("b", LocalDateTime.now()));
        memory.addMessage(buildMsg("c", LocalDateTime.now()));
        memory.deleteMessage(1);

        List<Msg> result = memory.getMessages();
        assertEquals(2, result.size());
        assertEquals("a", result.get(0).getTextContent());
        assertEquals("c", result.get(1).getTextContent());
    }

    @Test
    void deleteMessage_negativeIndex_noOp() {
        memory.addMessage(buildMsg("a", LocalDateTime.now()));
        memory.deleteMessage(-1);
        assertEquals(1, memory.getMessages().size());
    }

    @Test
    void deleteMessage_outOfBounds_noOp() {
        memory.addMessage(buildMsg("a", LocalDateTime.now()));
        memory.deleteMessage(5);
        assertEquals(1, memory.getMessages().size());
    }

    // ========== max messages eviction ==========

    @Test
    void evict_exceedsMax_removesOldest() {
        for (int i = 0; i < 7; i++) {
            memory.addMessage(buildMsg("msg" + i, LocalDateTime.now()));
        }
        List<Msg> result = memory.getMessages();
        assertEquals(5, result.size());
        assertEquals("msg2", result.get(0).getTextContent());
        assertEquals("msg6", result.get(4).getTextContent());
    }

    // ========== time window eviction ==========

    @Test
    void evict_expiredMessages_removed() {
        LocalDateTime now = LocalDateTime.now();
        memory.addMessage(buildMsg("old", now.minusMinutes(60)));
        memory.addMessage(buildMsg("recent", now));

        List<Msg> result = memory.getMessages();
        assertEquals(1, result.size());
        assertEquals("recent", result.get(0).getTextContent());
    }

    @Test
    void evict_allExpired_empty() {
        memory.addMessage(buildMsg("old1", LocalDateTime.now().minusHours(2)));
        memory.addMessage(buildMsg("old2", LocalDateTime.now().minusHours(1)));

        assertTrue(memory.getMessages().isEmpty());
    }

    @Test
    void evict_nullTimestamp_notExpired() {
        Msg msg = Msg.builder().role(MsgRole.USER).textContent("no-timestamp").build();
        memory.addMessage(msg);
        assertEquals(1, memory.getMessages().size());
    }

    @Test
    void evict_invalidTimestamp_notExpired() {
        Msg msg = Msg.builder().role(MsgRole.USER).textContent("bad-ts")
                .timestamp("not-a-date").build();
        memory.addMessage(msg);
        assertEquals(1, memory.getMessages().size());
    }

    // ========== combined eviction ==========

    @Test
    void evict_timeAndMax_combined() {
        SlidingWindowMemory small = new SlidingWindowMemory(3, Duration.ofMinutes(10));
        LocalDateTime now = LocalDateTime.now();

        small.addMessage(buildMsg("expired", now.minusMinutes(20)));
        small.addMessage(buildMsg("a", now.minusMinutes(5)));
        small.addMessage(buildMsg("b", now.minusMinutes(3)));
        small.addMessage(buildMsg("c", now.minusMinutes(1)));
        small.addMessage(buildMsg("d", now));

        List<Msg> result = small.getMessages();
        assertEquals(3, result.size());
        assertEquals("b", result.get(0).getTextContent());
    }

    // ========== thread safety ==========

    @Test
    void concurrentAccess_noException() throws InterruptedException {
        int threads = 10;
        int opsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        memory.addMessage(buildMsg("msg" + i, LocalDateTime.now()));
                        memory.getMessages();
                        if (i % 3 == 0) memory.deleteMessage(0);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> memory.getMessages());
        executor.shutdown();
    }

    // ========== helpers ==========

    private Msg buildMsg(String text, LocalDateTime time) {
        return Msg.builder()
                .role(MsgRole.USER)
                .textContent(text)
                .timestamp(time.format(FMT))
                .build();
    }
}
