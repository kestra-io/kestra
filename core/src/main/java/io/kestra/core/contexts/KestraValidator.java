package io.kestra.core.contexts;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.validation.validator.DefaultValidator;
import io.micronaut.validation.validator.ValidatorConfiguration;
import lombok.extern.slf4j.Slf4j;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Replaces(DefaultValidator.class)
@Slf4j

@SuppressWarnings({"unchecked", "rawtypes"})
public class KestraValidator extends DefaultValidator {
    private Map<String, BeanIntrospectionReference<Object>> introspectionMap;

    protected KestraValidator(@NonNull ValidatorConfiguration configuration) {
        super(configuration);
    }

    protected @Nullable
    BeanIntrospection<Object> getBeanIntrospection(@NonNull Object object) {
        //noinspection ConstantConditions
        if (object == null) {
            return null;
        }

        BeanIntrospection<Object> beanIntrospection = super.getBeanIntrospection(object);
        if (beanIntrospection != null) {
            return beanIntrospection;
        }

        // plugins introspection
        if (object instanceof Class) {
            return this.findIntrospection((Class<Object>) object).orElse(null);
        }

        return this.findIntrospection((Class<Object>) object.getClass()).orElse(null);
    }

    private <T> Optional<BeanIntrospection<T>> findIntrospection(@NonNull Class<T> beanType) {
        ArgumentUtils.requireNonNull("beanType", beanType);
        BeanIntrospectionReference reference = this.getIntrospections().get(beanType.getName());

        try {
            if (reference != null) {
                return Optional
                    .of(reference)
                    .map((ref) -> {
                        if (log.isDebugEnabled()) {
                            log.debug("Found BeanIntrospection for type: " + ref.getBeanType());
                        }

                        return ref.load();
                    });
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("No BeanIntrospection found for bean type: " + beanType);
                }

                return Optional.empty();
            }
        } catch (Throwable e) {
            throw new IntrospectionException("Error loading BeanIntrospection for type [" + beanType + "]: " + e.getMessage(), e);
        }
    }

    private Map<String, BeanIntrospectionReference<Object>> getIntrospections() {
        Map<String, BeanIntrospectionReference<Object>> introspectionMap = this.introspectionMap;
        if (introspectionMap == null) {
            synchronized(this) {
                introspectionMap = this.introspectionMap;
                if (introspectionMap == null) {
                    introspectionMap = new HashMap<>(30);

                    PluginRegistry pluginRegistry;

                    // class loader may be not ready, mostly for unit test
                    try {
                        pluginRegistry = KestraClassLoader.instance().getPluginRegistry();
                    } catch (IllegalStateException e) {
                        if (log.isDebugEnabled()) {
                            log.debug(e.getMessage());
                        }

                        return introspectionMap;
                    }

                    if (pluginRegistry != null) {
                        for (RegisteredPlugin registeredPlugin : pluginRegistry.getPlugins()) {

                            SoftServiceLoader<BeanIntrospectionReference> services = SoftServiceLoader.load(BeanIntrospectionReference.class, registeredPlugin.getClassLoader());

                            for (ServiceDefinition<BeanIntrospectionReference> service : services) {
                                if (service.isPresent()) {
                                    BeanIntrospectionReference ref = service.load();
                                    ((Map) introspectionMap).put(ref.getName(), ref);
                                } else if (log.isDebugEnabled()) {
                                    log.debug(
                                        "BeanIntrospection {} not loaded since associated bean is not present on the classpath",
                                        service.getName()
                                    );
                                }
                            }
                        }
                    }

                    this.introspectionMap = introspectionMap;
                }
            }
        }

        return introspectionMap;
    }
}
