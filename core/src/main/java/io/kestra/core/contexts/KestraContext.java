package io.kestra.core.contexts;

import io.kestra.core.models.ServerType;
import io.kestra.core.utils.VersionProvider;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import jakarta.annotation.PreDestroy;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class for retrieving common information about a Kestra Server at runtime.
 */
public abstract class KestraContext {

    private static final AtomicReference<KestraContext> INSTANCE = new AtomicReference<>();

    // Properties
    private static final String KESTRA_SERVER_TYPE = "kestra.server-type";

    /**
     * Gets the current {@link KestraContext}.
     *
     * @return The context.
     * @throws IllegalStateException if not context is initialized.
     */
    public static KestraContext getContext() {
        return Optional.ofNullable(INSTANCE.get())
            .orElseThrow(() -> new IllegalStateException("Kestra context not initialized"));
    }

    /**
     * Sets the current {@link KestraContext}.
     *
     * @param context The context.
     */
    public static void setContext(final KestraContext context) {
        KestraContext.INSTANCE.set(context);
    }

    /**
     * Returns the current {@link ServerType}.
     *
     * @return The {@link ServerType}.
     */
    public abstract ServerType getServerType();

    /**
     * Returns the Kestra Version.
     *
     * @return the string version.
     */
    public abstract String getVersion();

    /**
     * Stops Kestra.
     */
    public void exit(int status) {
        // noop
    }

    /**
     * Kestra context initializer
     */
    @Context
    @Requires(missingBeans = KestraContext.class)
    public static class Initializer extends KestraContext {

        private final ApplicationContext applicationContext;
        private final Environment environment;
        private final String version;

        /**
         * Creates a new {@link KestraContext} instance.
         *
         * @param applicationContext     The {@link ApplicationContext}.
         * @param environment The {@link Environment}.
         */
        public Initializer(ApplicationContext applicationContext,
                           Environment environment) {
            this.applicationContext = applicationContext;
            this.version = Optional.ofNullable(applicationContext.getBean(VersionProvider.class)).map(VersionProvider::getVersion).orElse(null);
            this.environment = environment;
            KestraContext.setContext(this);
        }

        /** {@inheritDoc} **/
        @Override
        public ServerType getServerType() {
            return Optional.ofNullable(environment)
                .flatMap(env -> env.getProperty(KESTRA_SERVER_TYPE, ServerType.class))
                .orElseThrow(() -> new IllegalStateException("Cannot found required environment property '" + KESTRA_SERVER_TYPE + "'."));
        }

        /** {@inheritDoc} **/
        @Override
        public void exit(int status) {
            applicationContext.close();
            Runtime.getRuntime().exit(status);
        }

        /** {@inheritDoc} **/
        @Override
        public String getVersion() {
            return version;
        }

        @PreDestroy
        public void dispose() {
            setContext(null);
        }
    }
}
