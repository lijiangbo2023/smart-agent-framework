package com.smart.agent.agent.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InMemorySessionLockService.
 */
class InMemorySessionLockServiceTest {

    private InMemorySessionLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new InMemorySessionLockService();
    }

    @Test
    void tryLock_success_returnsNonNullLockValue() {
        String lockValue = lockService.tryLock("user1", "session1");
        assertNotNull(lockValue);
    }

    @Test
    void tryLock_sameKeySecondCall_returnsNull() {
        String first = lockService.tryLock("user1", "session1");
        assertNotNull(first);

        String second = lockService.tryLock("user1", "session1");
        assertNull(second, "Second lock on same key should fail");
    }

    @Test
    void tryLock_differentKeys_bothSucceed() {
        String first = lockService.tryLock("user1", "session1");
        String second = lockService.tryLock("user1", "session2");
        String third = lockService.tryLock("user2", "session1");

        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);
    }

    @Test
    void unlock_withCorrectValue_releasesLock() {
        String lockValue = lockService.tryLock("user1", "session1");
        assertNotNull(lockValue);

        lockService.unlock("user1", "session1", lockValue);

        // Should be able to lock again
        String newLockValue = lockService.tryLock("user1", "session1");
        assertNotNull(newLockValue, "Lock should be released after unlock with correct value");
    }

    @Test
    void unlock_withWrongValue_doesNotRelease() {
        String lockValue = lockService.tryLock("user1", "session1");
        assertNotNull(lockValue);

        lockService.unlock("user1", "session1", "wrong-value");

        // Should NOT be able to lock again
        String newLockValue = lockService.tryLock("user1", "session1");
        assertNull(newLockValue, "Lock should NOT be released with wrong value");
    }

    @Test
    void unlock_withNull_isNoOp() {
        String lockValue = lockService.tryLock("user1", "session1");
        assertNotNull(lockValue);

        // Should not throw
        assertDoesNotThrow(() -> lockService.unlock("user1", "session1", null));

        // Lock should still be held
        assertNull(lockService.tryLock("user1", "session1"));
    }

    @Test
    void unlock_nonExistentKey_isNoOp() {
        assertDoesNotThrow(() -> lockService.unlock("nobody", "nothing", "some-value"));
    }

    @Test
    void concurrentAccess_onlyOneWins() throws Exception {
        int threads = 10;
        int[] winners = {0};
        Thread[] workers = new Thread[threads];

        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                String val = lockService.tryLock("user1", "session1");
                if (val != null) {
                    synchronized (winners) {
                        winners[0]++;
                    }
                }
            });
        }

        for (Thread t : workers) t.start();
        for (Thread t : workers) t.join(5000);

        assertEquals(1, winners[0], "Exactly one thread should acquire the lock");
    }
}
