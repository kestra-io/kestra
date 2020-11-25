package org.kestra.core.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class DurationOrSizeTriggerTest {
    private final Duration batchDuration = Duration.ofMillis(500);

    private final DurationOrSizeTrigger<String> trigger = new DurationOrSizeTrigger<>(
        batchDuration, 100
    );

    @Test
    public void testBySize() {
        HashMap<String, String> map = generateHashMap(99);

        assertFalse(trigger.test(map.values()));
        map.put("test", "b");
        assertTrue(trigger.test(map.values()));
    }

    @Test
    public void testByDuration() throws InterruptedException {
        HashMap<String, String> map = generateHashMap(10);

        assertFalse(trigger.test(map.values()));
        Thread.sleep(100L);
        assertFalse(trigger.test(map.values()));
        Thread.sleep(500L);
        assertTrue(trigger.test(map.values()));
    }

    @Test
    public void testByDurationAndSize() throws InterruptedException {
        Instant next = trigger.getNext();
        assertTrue(trigger.test(generateHashMap(100).values()));
        Thread.sleep(500L);
        assertTrue(trigger.test(generateHashMap(1).values()));
        assertEquals(next.plus(batchDuration), trigger.getNext());
    }

    private HashMap<String, String> generateHashMap(int count) {
        HashMap<String, String> map = new HashMap<>();
        for (int i = 0; i < count; i++) {
            map.put(i + "", "b");
        }
        return map;
    }
}