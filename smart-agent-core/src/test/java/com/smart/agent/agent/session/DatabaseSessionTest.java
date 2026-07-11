package com.smart.agent.agent.session;

import com.smart.agent.constant.AgentConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseSessionTest {

    // ========== buildSessionKey ==========

    @Test
    void buildSessionKey_normal() {
        String key = DatabaseSession.buildSessionKey("user001", "session001");
        assertEquals("user001" + AgentConstants.SESSION_COMPOSITE_KEY_SEPARATOR + "session001", key);
    }

    @Test
    void buildSessionKey_nullSessionId_usesDefault() {
        String key = DatabaseSession.buildSessionKey("user001", null);
        assertEquals("user001" + AgentConstants.SESSION_COMPOSITE_KEY_SEPARATOR + "default", key);
    }

    @Test
    void buildSessionKey_emptySessionId_usesDefault() {
        String key = DatabaseSession.buildSessionKey("user001", "");
        assertEquals("user001" + AgentConstants.SESSION_COMPOSITE_KEY_SEPARATOR + "default", key);
    }

    @Test
    void buildSessionKey_containsSeparator_inUserId() {
        String key = DatabaseSession.buildSessionKey("user" + AgentConstants.SESSION_COMPOSITE_KEY_SEPARATOR + "extra", "session001");
        assertNotNull(key);
        assertTrue(key.contains("session001"));
    }

    @Test
    void buildSessionKey_specialChars() {
        String key = DatabaseSession.buildSessionKey("user@domain.com", "sess-123-abc");
        assertTrue(key.startsWith("user@domain.com"));
        assertTrue(key.endsWith("sess-123-abc"));
    }

    @Test
    void buildSessionKey_longIds() {
        String longUserId = "u".repeat(200);
        String longSessionId = "s".repeat(200);
        String key = DatabaseSession.buildSessionKey(longUserId, longSessionId);
        assertTrue(key.contains(longUserId));
        assertTrue(key.contains(longSessionId));
    }

    // ========== composite key round-trip ==========

    @Test
    void buildSessionKey_roundTrip_containsBothIds() {
        String key = DatabaseSession.buildSessionKey("testUser", "testSession");
        assertTrue(key.contains("testUser"));
        assertTrue(key.contains("testSession"));
        assertTrue(key.contains(AgentConstants.SESSION_COMPOSITE_KEY_SEPARATOR));
    }
}
