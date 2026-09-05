package com.sfm_optimizer.transfer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransferMemoryStoreTest {
    @Test void sleepThenWake() {
        TransferMemoryStore<String> s = new TransferMemoryStore<>();
        assertFalse(s.isAsleep("a", 0L));
        s.sleep("a", 0L, 10L);
        assertTrue(s.isAsleep("a", 5L));
        assertFalse(s.isAsleep("a", 11L));
        s.wake("a");
        assertFalse(s.isAsleep("a", 5L));
    }
    @Test void slotMemoryDefaultsToMinusOne() {
        TransferMemoryStore<String> s = new TransferMemoryStore<>();
        assertEquals(-1, s.lastSlot("a"));
        s.rememberSlot("a", 7);
        assertEquals(7, s.lastSlot("a"));
    }
    @Test void clearResetsEverything() {
        TransferMemoryStore<String> s = new TransferMemoryStore<>();
        s.sleep("a", 0L, 100L);
        s.rememberSlot("a", 3);
        s.clear();
        assertFalse(s.isAsleep("a", 0L));
        assertEquals(-1, s.lastSlot("a"));
    }
}
