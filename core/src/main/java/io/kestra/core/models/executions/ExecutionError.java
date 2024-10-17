package io.kestra.core.models.executions;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Builder
@Setter
@Getter
public class ExecutionError {
    private static final int MAX_NB_FRAMES = 10;

    private String message;
    private String stacktrace;

    public static ExecutionError from(Throwable throwable) {
        if (throwable == null) {
            return ExecutionError.builder().message("Unknown error").build();
        }

        String firstLine = throwable.getClass().getName() + ": " + throwable.getMessage() + "\n";
        StackTraceElement[] stackTraces = throwable.getStackTrace();
        String stackTraceStr;
        if (stackTraces.length > 10) {
            // keep only the top 10 frames
            stackTraceStr = stackTraceToString(firstLine, Arrays.copyOf(stackTraces, 10)) + "\n\t[...]";
        } else {
            stackTraceStr = stackTraceToString(firstLine, stackTraces);
        }
        return ExecutionError.builder()
            .message(throwable.getMessage())
            .stacktrace(stackTraceStr)
            .build();
    }

    private static String stackTraceToString(String firstLine, StackTraceElement[] stackTraces) {
        return Stream.of(stackTraces)
            .map(stackTraceElement -> stackTraceElement.toString())
            .collect(Collectors.joining("\n\t", firstLine, ""));
    }
}
