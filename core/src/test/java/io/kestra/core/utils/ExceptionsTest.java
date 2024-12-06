package io.kestra.core.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionsTest {

    @Test
    void shouldGetStacktraceGivenNoMaxLines() {
        String stacktrace = Exceptions.getStacktraceAsString(new RuntimeException("boom!"));
        Assertions.assertNotNull(stacktrace);
        Assertions.assertTrue(stacktrace.startsWith("java.lang.RuntimeException: boom!\n"));
    }

    @Test
    void shouldGetStacktraceGivenMaxLines() {
        String stacktrace = Exceptions.getStacktraceAsString(new RuntimeException("boom!"), 3);
        Assertions.assertTrue(stacktrace.startsWith("java.lang.RuntimeException: boom!\n"));
        Assertions.assertEquals(4, stacktrace.lines().count());
    }
}