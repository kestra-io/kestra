package io.kestra.core.models.validations;

import io.kestra.core.models.flows.input.FloatInput;
import jakarta.validation.ConstraintViolationException;
import lombok.Getter;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.metadata.ConstraintDescriptor;

import java.util.Set;

@Getter
public class ManualConstraintViolation<T> implements ConstraintViolation<T> {
    private final String message;
    private final T rootBean;
    private final Class<T> rootBeanClass;
    private final Object leafBean;
    private final Path propertyPath;
    private final Object invalidValue;

    private ManualConstraintViolation(
        String message,
        T rootBean,
        Class<T> rootBeanClass,
        Object leafBean,
        Path propertyPath,
        Object invalidValue
    ) {
        this.message = message;
        this.rootBean = rootBean;
        this.rootBeanClass = rootBeanClass;
        this.leafBean = leafBean;
        this.propertyPath = propertyPath;
        this.invalidValue = invalidValue;
    }

    public static <T> ManualConstraintViolation<T> of(
        String message,
        T object,
        Class<T> cls,
        String propertyPath,
        Object invalidValue
    ) {
        return new ManualConstraintViolation<T>(
            message,
            object,
            cls,
            object,
            new ManualPath(new ManualPropertyNode(propertyPath)),
            invalidValue
        );
    }

    public static <T> ConstraintViolationException toConstraintViolationException(
        String message,
        T object,
        Class<T> cls,
        String propertyPath,
        Object invalidValue
    ) {
        return new ConstraintViolationException(Set.of(of(
            message,
            object,
            cls,
            propertyPath,
            invalidValue
        )));
    }

    public String getMessageTemplate() {
        return "{messageTemplate}";
    }

    public Object[] getExecutableParameters() {
        return new Object[0];
    }

    public Object getExecutableReturnValue() {
        return null;
    }

    @Override
    public ConstraintDescriptor<?> getConstraintDescriptor() {
        return null;
    }

    @Override
    public <C> C unwrap(Class<C> type) {
        throw new IllegalArgumentException("Type " + type.getName() + " not supported for unwrapping.");
    }
}
