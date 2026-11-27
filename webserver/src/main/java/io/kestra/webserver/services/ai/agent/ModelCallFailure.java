package io.kestra.webserver.services.ai.agent;

import dev.langchain4j.exception.HttpException;

final class ModelCallFailure {
    private static final String JDK_HTTP_CLIENT = "dev.langchain4j.http.client.jdk.JdkHttpClient";

    private ModelCallFailure() {
    }

    /**
     * @return the reason a model call failed, carrying the HTTP status when the provider, or a proxy on the way, answered with one.
     */
    static String reason(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpException http) {
                return "HTTP %d: %s".formatted(http.statusCode(), http.getMessage());
            }
            if (isBodilessErrorResponse(cause)) {
                return "The HTTP response had no body, which is what a proxy refusing the connection returns (for example 407 Proxy Authentication Required). "
                    + "Check the JVM proxy credentials (http(s).proxyUser and http(s).proxyPassword) and that the JVM is started with -Djdk.http.auth.tunneling.disabledSchemes= .";
            }
        }
        return failure.getMessage();
    }

    // langchain4j reads the body of an error response without a null check, and the JDK gives none to a refused CONNECT: the status is lost in a NullPointerException.
    private static boolean isBodilessErrorResponse(final Throwable cause) {
        StackTraceElement[] stackTrace = cause.getStackTrace();
        return cause instanceof NullPointerException && stackTrace.length > 0 && JDK_HTTP_CLIENT.equals(stackTrace[0].getClassName());
    }
}
