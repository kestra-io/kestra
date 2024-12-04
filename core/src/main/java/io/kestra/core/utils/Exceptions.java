package io.kestra.core.utils;


/**
 * Utility method for manipulating Exception.
 */
public interface Exceptions {

    static String getStacktraceAsString(final Throwable throwable) {
        return getStacktraceAsString(throwable, Integer.MAX_VALUE);
    }

    static String getStacktraceAsString(final Throwable throwable, int maxLines) {
        StringBuilder limitedStackTrace = new StringBuilder();

        limitedStackTrace.append(throwable).append(System.lineSeparator());

        StackTraceElement[] stackTraceElements = throwable.getStackTrace();
        for (int i = 0; i < Math.min(maxLines, stackTraceElements.length); i++) {
            limitedStackTrace.append("\tat ").append(stackTraceElements[i]).append(System.lineSeparator());
        }

        return limitedStackTrace.toString();
    }
}
