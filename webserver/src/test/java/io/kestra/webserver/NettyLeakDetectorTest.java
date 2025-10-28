package io.kestra.webserver;

import io.kestra.core.junit.annotations.KestraTest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@KestraTest
class NettyLeakDetectorTest {

    @Test
    @Disabled("This test verifies leak detection works enable to test leak detection")
    void shouldDetectLeakWhenBufferNotReleased() {
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        buffer.writeBytes("test data".getBytes());
        assertNotNull(buffer);
    }

    @Test
    void shouldNotDetectLeakWhenBufferReleased() {
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        try {
            buffer.writeBytes("test data".getBytes());
            assertNotNull(buffer);
        } finally {
            buffer.release();
        }
    }
}
