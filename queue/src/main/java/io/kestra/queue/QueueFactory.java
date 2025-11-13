package io.kestra.queue;

import io.kestra.core.utils.ExecutorsUtils;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Factory
public class QueueFactory {
    public final static String QUEUE_EXECUTOR = "queueExecutor";


    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T extends GenericEvent> List<Class<T>> listAllEvent(ClassLoader classLoader, Class<T> eventClass) {
        final SoftServiceLoader<BeanIntrospectionReference> definitions = SoftServiceLoader.load(
            BeanIntrospectionReference.class,
            classLoader
        );

        List<Class<T>> list = new ArrayList<>();

        for (ServiceDefinition<BeanIntrospectionReference> definition : definitions) {
            if (definition.isPresent()) {
                final BeanIntrospectionReference ref = definition.load();
                Class beanType = ref.getBeanType();

                if (Modifier.isAbstract(beanType.getModifiers())) {
                    continue;
                }

                if (eventClass.isAssignableFrom(beanType)) {
                    list.add((Class<T>) beanType);
                }

            }
        }

        return list;
    }

    @Named(QUEUE_EXECUTOR)
    @Singleton
    @Inject
    public ExecutorService executorsUtils(ExecutorsUtils executorsUtils, QueueConfiguration queueConfiguration) {
        return executorsUtils.cachedThreadPool("queue-" + queueConfiguration.type());
    }
}
