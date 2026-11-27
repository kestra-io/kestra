package io.kestra.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

/** Captures the standard streams around a single action only, so the logs emitted outside it stay visible. */
public final class ConsoleCapture {

    public record Result<T>(T value, String out, String err) {
    }

    private ConsoleCapture() {
    }

    public static <T> Result<T> run(Callable<T> action) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));

        try {
            T value = action.call();
            return new Result<>(value, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Replayed so that the logs of a failing action are not lost with the buffers.
            originalOut.print(out.toString(StandardCharsets.UTF_8));
            originalErr.print(err.toString(StandardCharsets.UTF_8));
            throw e instanceof RuntimeException runtimeException ? runtimeException : new IllegalStateException(e);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
