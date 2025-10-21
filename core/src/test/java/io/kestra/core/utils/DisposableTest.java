package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisposableTest {
    
    @Test
    void shouldOnlyRunOnce() {
        // Given
        AtomicInteger count = new AtomicInteger(0);
        Disposable disposable = Disposable.of(count::incrementAndGet);
        
        // When
        disposable.dispose();
        disposable.dispose();
        
        // Then
        assertEquals(1, count.get());
    }
    
}