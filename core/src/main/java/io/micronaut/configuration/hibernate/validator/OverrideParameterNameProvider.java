package io.micronaut.configuration.hibernate.validator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;
import jakarta.validation.ParameterNameProvider;

@Singleton
@Replaces(DefaultParameterNameProvider.class)
public class OverrideParameterNameProvider implements ParameterNameProvider {

    private final DefaultParameterNameProvider delegate;

    public OverrideParameterNameProvider(DefaultParameterNameProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<String> getParameterNames(Constructor<?> constructor) {
        return normalize(constructor, delegate.getParameterNames(constructor));
    }

    @Override
    public List<String> getParameterNames(Method method) {
        return normalize(method, delegate.getParameterNames(method));
    }

    private List<String> normalize(Executable exec, List<String> names) {
        int paramCount = exec.getParameterCount();

        if (names.size() == paramCount) {
            return names;
        }

        // Trim extra names (Micronaut internal params leak)
        if (names.size() > paramCount) {
            return new ArrayList<>(names.subList(0, paramCount));
        }

        // Pad missing names to avoid Hibernate Validator crash
        List<String> normalized = new ArrayList<>(paramCount);
        normalized.addAll(names);

        for (int i = names.size(); i < paramCount; i++) {
            normalized.add("arg" + i);
        }

        return normalized;
    }
}
