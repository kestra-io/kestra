package io.kestra.core.runners.pebble;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.runners.configuration.VariableConfiguration;
import io.kestra.core.runners.pebble.functions.DayOfMonthFunction;
import io.kestra.core.runners.pebble.functions.DayOfWeekFunction;
import io.kestra.core.runners.pebble.functions.EnvFunction;
import io.kestra.core.runners.pebble.functions.FromIonFunction;
import io.kestra.core.runners.pebble.functions.FromJsonFunction;
import io.kestra.core.runners.pebble.functions.HourOfDayFunction;
import io.kestra.core.runners.pebble.functions.IsDayWeekInMonthFunction;
import io.kestra.core.runners.pebble.functions.IsLastWorkingDayFunction;
import io.kestra.core.runners.pebble.functions.IsPublicHolidayFunction;
import io.kestra.core.runners.pebble.functions.IsWeekendFunction;
import io.kestra.core.runners.pebble.functions.MonthOfYearFunction;
import io.kestra.core.runners.pebble.functions.RenderingFunctionInterface;
import io.kestra.core.runners.pebble.functions.SecretFunction;
import io.kestra.core.runners.pebble.functions.YamlFunction;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Nullable;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.extension.Extension;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.lexer.Syntax;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class PebbleEngineFactory {

    private final ApplicationContext applicationContext;
    private final VariableConfiguration variableConfiguration;
    private final MeterRegistry meterRegistry;

    @Inject
    public PebbleEngineFactory(ApplicationContext applicationContext, @Nullable VariableConfiguration variableConfiguration, MeterRegistry meterRegistry) {
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

    /**
     * Allowlist of functions that are safe to invoke when resolving expressions for display:
     * pure, deterministic, side-effect-free transformations (parsing and calendar helpers).
     *
     * <p>This is intentionally an <em>allowlist</em>, not a denylist. Any function not listed here
     * — including {@code kv}, {@code render}, {@code decrypt}, {@code fetchContext}, file IO,
     * non-deterministic generators, and any function added in the future or contributed by a plugin —
     * is kept raw by default. Display resolution is security-sensitive, so the safe default is
     * "do not invoke".
     */
    private static final Set<String> SAFE_DISPLAY_FUNCTIONS = Set.of(
        FromJsonFunction.NAME,
        FromIonFunction.NAME,
        YamlFunction.NAME,
        DayOfMonthFunction.NAME,
        DayOfWeekFunction.NAME,
        HourOfDayFunction.NAME,
        MonthOfYearFunction.NAME,
        IsWeekendFunction.NAME,
        IsDayWeekInMonthFunction.NAME,
        IsLastWorkingDayFunction.NAME,
        IsPublicHolidayFunction.NAME
    );

    /**
     * Returns a PebbleEngine suitable for safe display-time expression resolution.
     *
     * <ul>
     *   <li>{@code secret(...)} → {@code [secret: KEY]} without invoking the real service.</li>
     *   <li>{@code env(...)} → {@code [env: NAME]} without reading env variables.</li>
     *   <li>Functions in {@link #SAFE_DISPLAY_FUNCTIONS} (pure parsing / calendar helpers) work normally.</li>
     *   <li>Every other function throws {@link DisplayUnrenderableException} before invocation,
     *       so the caller keeps the raw segment.</li>
     * </ul>
     */
    public PebbleEngine createForDisplay() {
        var builder = newPebbleEngineBuilder();

        this.applicationContext.getBeansOfType(Extension.class).stream()
            .map(this::extensionForDisplay)
            .forEach(builder::extension);

        return builder.build();
    }

    private Extension extensionForDisplay(Extension initialExtension) {
        // Any function that is not in the safe allowlist must be wrapped (masked or raw-signalled).
        var needsProxy = initialExtension.getFunctions().keySet().stream()
            .anyMatch(name -> !SAFE_DISPLAY_FUNCTIONS.contains(name));

        if (!needsProxy) {
            return initialExtension;
        }

        return (Extension) Proxy.newProxyInstance(
            initialExtension.getClass().getClassLoader(),
            new Class<?>[] {Extension.class},
            (proxy, method, methodArgs) ->
            {
                if (method.getName().equals("getFunctions")) {
                    return initialExtension.getFunctions().entrySet().stream()
                        .map(entry ->
                        {
                            var name = entry.getKey();
                            if (SAFE_DISPLAY_FUNCTIONS.contains(name)) {
                                return entry;
                            } else if (name.equals(SecretFunction.NAME)) {
                                return Map.entry(name, secretMaskProxy(entry.getValue()));
                            } else if (name.equals(EnvFunction.NAME)) {
                                return Map.entry(name, envMaskProxy(entry.getValue()));
                            }
                            // Everything else is kept raw — never invoked.
                            return Map.entry(name, rawSignalProxy(entry.getValue()));
                        })
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                }
                return method.invoke(initialExtension, methodArgs);
            }
        );
    }

    /** Returns {@code [secret: KEY]} without touching the real secret service. */
    private Function secretMaskProxy(Function initial) {
        return (Function) Proxy.newProxyInstance(
            initial.getClass().getClassLoader(),
            new Class<?>[] {Function.class},
            (proxy, method, methodArgs) ->
            {
                if (method.getName().equals("execute")) {
                    @SuppressWarnings("unchecked")
                    var args = (Map<String, Object>) methodArgs[0];
                    var key = args.getOrDefault("key", "?");
                    return "[secret: " + key + "]";
                }
                try {
                    return method.invoke(initial, methodArgs);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        );
    }

    /** Returns {@code [env: NAME]} without reading environment variables. */
    private Function envMaskProxy(Function initial) {
        return (Function) Proxy.newProxyInstance(
            initial.getClass().getClassLoader(),
            new Class<?>[] {Function.class},
            (proxy, method, methodArgs) ->
            {
                if (method.getName().equals("execute")) {
                    @SuppressWarnings("unchecked")
                    var args = (Map<String, Object>) methodArgs[0];
                    var name = args.getOrDefault("name", "?");
                    return "[env: " + name + "]";
                }
                try {
                    return method.invoke(initial, methodArgs);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        );
    }

    /**
     * Signals that this segment should remain raw by throwing {@link DisplayUnrenderableException}
     * before the underlying function is ever invoked.
     */
    private Function rawSignalProxy(Function initial) {
        return (Function) Proxy.newProxyInstance(
            initial.getClass().getClassLoader(),
            new Class<?>[] {Function.class},
            (proxy, method, methodArgs) ->
            {
                if (method.getName().equals("execute")) {
                    throw new DisplayUnrenderableException();
                }
                try {
                    return method.invoke(initial, methodArgs);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        );
    }

    public PebbleEngine createWithMaskedFunctions(VariableRenderer renderer, final List<String> functionsToMask) {

        PebbleEngine.Builder builder = newPebbleEngineBuilder();

        this.applicationContext.getBeansOfType(Extension.class).stream()
            .map(
                e -> functionsToMask.stream().anyMatch(fun -> e.getFunctions().containsKey(fun))
                    ? extensionWithMaskedFunctions(renderer, e, functionsToMask)
                    : e
            )
            .forEach(builder::extension);

        return builder.build();
    }

    private PebbleEngine.Builder newPebbleEngineBuilder() {
        PebbleEngine.Builder builder = new PebbleEngine.Builder()
            .registerExtensionCustomizer(ExtensionCustomizer::new)
            .strictVariables(true)
            .cacheActive(this.variableConfiguration.getCacheEnabled())
            .newLineTrimming(false)
            .autoEscaping(false)
            .allowOverrideCoreOperators(true);

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
            new Class<?>[] { Extension.class },
            (proxy, method, methodArgs) ->
            {
                if (method.getName().equals("getFunctions")) {
                    return initialExtension.getFunctions().entrySet().stream()
                        .map(entry ->
                        {
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
            new Class<?>[] { Function.class, RenderingFunctionInterface.class },
            (functionProxy, functionMethod, functionArgs) ->
            {
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
            new Class<?>[] { Function.class },
            (functionProxy, functionMethod, functionArgs) ->
            {
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
