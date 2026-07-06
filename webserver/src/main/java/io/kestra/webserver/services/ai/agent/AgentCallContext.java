package io.kestra.webserver.services.ai.agent;

public final class AgentCallContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private AgentCallContext() {
    }

    public static void set(final String tenant) {
        CURRENT_TENANT.set(tenant);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    public static String requireTenant() {
        String tenant = CURRENT_TENANT.get();
        if (tenant == null) {
            throw new IllegalStateException("No agent call context bound to this thread");
        }
        return tenant;
    }
}
