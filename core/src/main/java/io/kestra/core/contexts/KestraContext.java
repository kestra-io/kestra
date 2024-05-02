package io.kestra.core.contexts;

import io.kestra.core.models.ServerType;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.utils.VersionProvider;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class for retrieving common information about a Kestra Server at runtime.
 */
public abstract class KestraContext {

    private static final Logger log = LoggerFactory.getLogger(KestraContext.class);

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
     * Returns the Kestra Plugin Registry.
     *
     * @return the {@link PluginRegistry}.
     */
    public abstract PluginRegistry getPluginRegistry();
    
    /**
     * Shutdowns the Kestra application.
     */
    public void shutdown() {
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

        private final AtomicBoolean isShutdown = new AtomicBoolean(false);

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
        public void shutdown() {
            if (isShutdown.compareAndSet(false, true)) {
                log.info("Kestra server - Shutdown initiated");
                applicationContext.close();
                log.info("Kestra server - Shutdown completed");
            }
        }

        /** {@inheritDoc} **/
        @Override
        public String getVersion() {
            return version;
        }
        
        /** {@inheritDoc} **/
        @Override
        public PluginRegistry getPluginRegistry() {
            // Lazy init of the PluginRegistry.
            return this.applicationContext.getBean(PluginRegistry.class);
        }
    }
}
