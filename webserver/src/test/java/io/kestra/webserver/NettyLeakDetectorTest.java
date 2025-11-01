package io.kestra.webserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;


class NettyLeakDetectorTest {

    
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


    @Test
    @Disabled("this test demonstrates leak detection - enable temporarily to verify leak detection works")
    void shouldDetectLeakWhenBufferNotReleased() {
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
        buffer.writeBytes("test data".getBytes());
        
        assertNotNull(buffer);
    }
}
