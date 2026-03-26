package io.kestra.core.runners.pebble.functions;

import io.kestra.core.runners.VariableRenderer;

import io.micronaut.context.ApplicationContext;

public interface RenderingFunctionInterface {
    String functionName();

    default VariableRenderer variableRenderer(ApplicationContext applicationContext) {
        return applicationContext.getBean(VariableRenderer.class);
    }
}
