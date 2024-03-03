package io.kestra.core.plugins;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.annotations.ClassLoaderIsolated;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.util.functional.ThrowingSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advice for enforcing Java ClassLoader isolation for pluggable classes/interfaces.
 */
@Factory
@InterceptorBean(ClassLoaderIsolated.class)
public class ClassLoaderIsolatedInterceptor implements MethodInterceptor<Object, Object> {

    private static final Logger LOG = LoggerFactory.getLogger(ClassLoaderIsolatedInterceptor.class);

    // By default, only intercept PluginClassLoader
    private boolean interceptAnyClassLoader = false;

    /**
     * {@inheritDoc}
     **/
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        final Class<Object> type = context.getDeclaringType();
        final ClassLoader classLoader = type.getClassLoader();
        if (interceptAnyClassLoader || classLoader instanceof PluginClassLoader) {
            LOG.trace("Method '{}' intercepted for class '{}', applying ClassLoader isolation for: {}",
                context.getMethodName(),
                type.getName(),
                classLoader
            );
            return classLoaderIsolation(classLoader, context::proceed);
        }
        return context.proceed();
    }

    @InterceptorBean(ClassLoaderIsolated.class)
    public ConstructorInterceptor<Object> aroundConstruct() {
        return context -> {
            final Class<Object> type = context.getDeclaringType();
            final ClassLoader classLoader = type.getClassLoader();
            if (interceptAnyClassLoader || classLoader instanceof PluginClassLoader) {
                if (LOG.isTraceEnabled()) {
                    InterceptorKind kind = context.getKind();
                    String annotationName = kind.getAnnotationType().getSimpleName(); // @PostConstruct, @PreDestroy
                    LOG.trace("Bean constructor intercepted for class '{}' on @{}, applying ClassLoader isolation for: {}",
                        type.getName(),
                        annotationName,
                        classLoader
                    );
                }
                return classLoaderIsolation(classLoader, context::proceed);
            }
            return context.proceed();
        };
    }

    @VisibleForTesting
    void setInterceptAnyClassLoader(final boolean interceptAnyClassLoader) {
        this.interceptAnyClassLoader = interceptAnyClassLoader;
    }

    private static <T, E extends Exception> T classLoaderIsolation(final ClassLoader cl,
                                                                   final ThrowingSupplier<T, E> supplier) throws E {
        final ClassLoader saved = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(cl);
            return supplier.get();
        } finally {
            Thread.currentThread().setContextClassLoader(saved);
        }
    }
}
