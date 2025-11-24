package io.kestra.queue;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

@Factory
public class QueueFactory {
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T extends Event> List<Class<T>> listAllEvent(ClassLoader classLoader, Class<T> eventClass) {
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
}
