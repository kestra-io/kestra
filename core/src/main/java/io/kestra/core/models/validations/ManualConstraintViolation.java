package io.kestra.core.models.validations;

import lombok.Getter;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.metadata.ConstraintDescriptor;

@Getter
public class ManualConstraintViolation<T> implements ConstraintViolation<T> {
    private String message;
    private T rootBean;
    private Class<T> rootBeanClass;
    private Object leafBean;
    private Path propertyPath;
    private Object invalidValue;

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
