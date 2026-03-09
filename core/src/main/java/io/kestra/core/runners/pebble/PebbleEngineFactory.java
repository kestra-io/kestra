package io.kestra.core.runners.pebble;

import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.runners.pebble.functions.RenderingFunctionInterface;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Nullable;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.extension.Extension;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.lexer.Syntax;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class PebbleEngineFactory {

    private final ApplicationContext applicationContext;
    private final VariableRenderer.VariableConfiguration variableConfiguration;
    private final MeterRegistry meterRegistry;

    @Inject
    public PebbleEngineFactory(ApplicationContext applicationContext, @Nullable VariableRenderer.VariableConfiguration variableConfiguration, MeterRegistry meterRegistry) {
        this.applicationContext = applicationContext;
        this.variableConfiguration = variableConfiguration;
        this.meterRegistry = meterRegistry;
    }

    public PebbleEngine create() {
        PebbleEngine.Builder builder = newPebbleEngineBuilder();
        this.applicationContext.getBeansOfType(Extension.class).forEach(builder::extension);
        return builder.build();
    }

    public PebbleEngine createWithCustomSyntax(Syntax syntax, Class<? extends Extension> extension) {
        PebbleEngine.Builder builder = newPebbleEngineBuilder()
            .syntax(syntax);
        this.applicationContext.getBeansOfType(extension).forEach(builder::extension);
        return builder.build();
    }

    public PebbleEngine createWithMaskedFunctions(VariableRenderer renderer, final List<String> functionsToMask) {

        PebbleEngine.Builder builder = newPebbleEngineBuilder();

        this.applicationContext.getBeansOfType(Extension.class).stream()
            .map(e -> functionsToMask.stream().anyMatch(fun -> e.getFunctions().containsKey(fun))
                ? extensionWithMaskedFunctions(renderer, e, functionsToMask)
                : e)
            .forEach(builder::extension);

        return builder.build();
    }

    private PebbleEngine.Builder newPebbleEngineBuilder() {
        PebbleEngine.Builder builder = new PebbleEngine.Builder()
            .registerExtensionCustomizer(ExtensionCustomizer::new)
            .strictVariables(true)
            .cacheActive(this.variableConfiguration.getCacheEnabled())
            .newLineTrimming(false)
            .autoEscaping(false);

        if (this.variableConfiguration.getCacheEnabled()) {
            PebbleLruCache cache = new PebbleLruCache(this.variableConfiguration.getCacheSize());
            cache.register(meterRegistry);
            builder = builder.templateCache(cache);
        }
        return builder;
    }

    private Extension extensionWithMaskedFunctions(VariableRenderer renderer, Extension initialExtension, List<String> maskedFunctions) {
        return (Extension) Proxy.newProxyInstance(
            initialExtension.getClass().getClassLoader(),
            new Class[]{Extension.class},
            (proxy, method, methodArgs) -> {
                if (method.getName().equals("getFunctions")) {
                    return initialExtension.getFunctions().entrySet().stream()
                        .map(entry -> {
                            if (maskedFunctions.contains(entry.getKey())) {
                                return Map.entry(entry.getKey(), this.maskedFunctionProxy(entry.getValue()));
                            } else if (RenderingFunctionInterface.class.isAssignableFrom(entry.getValue().getClass())) {
                                return Map.entry(entry.getKey(), this.variableRendererProxy(renderer, entry.getValue()));
                            }

                            return entry;
                        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                }

                return method.invoke(initialExtension, methodArgs);
            }
        );
    }

    private Function variableRendererProxy(VariableRenderer renderer, Function initialFunction) {
        return (Function) Proxy.newProxyInstance(
            initialFunction.getClass().getClassLoader(),
            new Class[]{Function.class, RenderingFunctionInterface.class},
            (functionProxy, functionMethod, functionArgs) -> {
                if (functionMethod.getName().equals("variableRenderer")) {
                    return renderer;
                }
                return functionMethod.invoke(initialFunction, functionArgs);
            }
        );
    }

    private Function maskedFunctionProxy(Function initialFunction) {
        return (Function) Proxy.newProxyInstance(
            initialFunction.getClass().getClassLoader(),
            new Class[]{Function.class},
            (functionProxy, functionMethod, functionArgs) -> {
                Object result;
                try {
                    result = functionMethod.invoke(initialFunction, functionArgs);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
                if (functionMethod.getName().equals("execute")) {
                    return "******";
                }
                return result;
            }
        );
    }
}
