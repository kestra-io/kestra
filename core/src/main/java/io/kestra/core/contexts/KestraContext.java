package io.kestra.core.contexts;

import com.google.common.base.Suppliers;
import io.kestra.core.models.ServerType;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.VersionProvider;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Utility class for retrieving common information about a Kestra Server at runtime.
 */
@Slf4j
@SuppressWarnings("this-escape")
public abstract class KestraContext {

    private static final AtomicReference<KestraContext> INSTANCE = new AtomicReference<>();

    // Properties
    public static final String KESTRA_SERVER_TYPE = "kestra.server-type";

    // Those properties are injected bases on the CLI args.
    private static final String KESTRA_WORKER_MAX_NUM_THREADS = "kestra.worker.max-num-threads";
    private static final String KESTRA_WORKER_GROUP_KEY = "kestra.worker.group-key";

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

    public abstract Optional<Integer> getWorkerMaxNumThreads();

    public abstract Optional<String> getWorkerGroupKey();

    public abstract void injectWorkerConfigs(Integer maxNumThreads, String workerGroupKey);

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

    public abstract StorageInterface getStorageInterface();

    /**
     * Returns the Micronaut active environments.
     */
    public abstract Set<String> getEnvironments();

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
        private final Supplier<String> version;

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
            // Lazy init of the version
            this.version = Suppliers.memoize(() ->
                // VersionProvider is not always available, for example in unit tests, so we use Optional to avoid issues in those cases.
                Optional.ofNullable(applicationContext.getBean(VersionProvider.class)).map(VersionProvider::getVersion).orElse(null)
            );
            this.environment = environment;
            KestraContext.setContext(this);
        }

        /** {@inheritDoc} **/
        @Override
        public ServerType getServerType() {
            return Optional.ofNullable(environment)
                .flatMap(env -> env.getProperty(KESTRA_SERVER_TYPE, ServerType.class))
                .orElse(ServerType.STANDALONE);
        }

        /** {@inheritDoc} **/
        @Override
        public Optional<Integer> getWorkerMaxNumThreads() {
            return Optional.ofNullable(environment)
                .flatMap(env -> env.getProperty(KESTRA_WORKER_MAX_NUM_THREADS, Integer.class));
        }

        /** {@inheritDoc} **/
        @Override
        public Optional<String> getWorkerGroupKey() {
            return Optional.ofNullable(environment)
                .flatMap(env -> env.getProperty(KESTRA_WORKER_GROUP_KEY, String.class));
        }
        /** {@inheritDoc} **/
        @Override
        public void injectWorkerConfigs(Integer maxNumThreads, String workerGroupKey) {
            final Map<String, Object> configs = new HashMap<>();
            Optional.ofNullable(maxNumThreads)
                .ifPresent(val -> configs.put(KESTRA_WORKER_MAX_NUM_THREADS, val));

            Optional.ofNullable(workerGroupKey)
                .ifPresent(val -> configs.put(KESTRA_WORKER_GROUP_KEY, val));

            if (!configs.isEmpty()) {
                environment.addPropertySource(PropertySource.of("kestra-runtime", configs));
            }
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
            return version.get();
        }

        /** {@inheritDoc} **/
        @Override
        public PluginRegistry getPluginRegistry() {
            // Lazy init of the PluginRegistry.
            return this.applicationContext.getBean(PluginRegistry.class);
        }

        @Override
        public StorageInterface getStorageInterface() {
            // Lazy init of the PluginRegistry.
            return this.applicationContext.getBean(StorageInterface.class);
        }

        @Override
        public Set<String> getEnvironments() {
            return this.applicationContext.getEnvironment().getActiveNames();
        }
    }
}
