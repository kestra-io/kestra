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
     *   <li>{@code env(...)} → kept raw (see issue #16874: env vars are not resolved for display).</li>
     *   <li>Functions in {@link #SAFE_DISPLAY_FUNCTIONS} (pure parsing / calendar helpers) work normally.</li>
     *   <li>Every other function throws {@link DisplayUnrenderableException} before invocation,
     *       so the caller keeps the raw segment.</li>
     * </ul>
     */
    public PebbleEngine createRestricted() {
        PebbleEngine.Builder builder = newPebbleEngineBuilder();

        this.applicationContext.getBeansOfType(Extension.class).stream()
            .map(this::extensionForDisplay)
            .forEach(builder::extension);

        return builder.build();
    }

    private Extension extensionForDisplay(Extension initial) {
        // Any function that is not in the safe allowlist must be wrapped (masked or raw-signalled).
        boolean needsProxy = initial.getFunctions().keySet().stream()
            .anyMatch(name -> !SAFE_DISPLAY_FUNCTIONS.contains(name));

        if (!needsProxy) {
            return initial;
        }

        return wrapExtension(initial, entry -> {
            String name = entry.getKey();
            if (SAFE_DISPLAY_FUNCTIONS.contains(name)) {
                return entry;
            } else if (name.equals(SecretFunction.NAME)) {
                // Returns [secret: KEY] without touching the real secret service.
                return Map.entry(name, interceptExecute(entry.getValue(),
                    args -> "[secret: " + args.getOrDefault("key", "?") + "]"));
            } else if (name.equals(EnvFunction.NAME)) {
                // env() is kept raw per the display taxonomy (issue #16874).
                return Map.entry(name, interceptExecute(entry.getValue(),
                    args -> { throw new DisplayUnrenderableException(); }));
            }
            // Everything else signals that the segment should remain raw — never invoked.
            return Map.entry(name, interceptExecute(entry.getValue(),
                args -> { throw new DisplayUnrenderableException(); }));
        });
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

    private Extension extensionWithMaskedFunctions(VariableRenderer renderer, Extension initial, List<String> maskedFunctions) {
        return wrapExtension(initial, entry -> {
            if (maskedFunctions.contains(entry.getKey())) {
                return Map.entry(entry.getKey(), interceptExecute(entry.getValue(), args -> "******"));
            } else if (RenderingFunctionInterface.class.isAssignableFrom(entry.getValue().getClass())) {
                return Map.entry(entry.getKey(), variableRendererProxy(renderer, entry.getValue()));
            }
            return entry;
        });
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

    // -------------------------------------------------------------------------
    // Proxy helpers
    // -------------------------------------------------------------------------

    /** Intercepts a single {@link Function#execute} call; all other methods delegate to {@code initial}. */
    @FunctionalInterface
    private interface ExecuteInterceptor {
        Object intercept(Map<String, Object> args) throws Throwable;
    }

    /**
     * Returns a proxy for {@code initial} that replaces its {@code execute} method with {@code handler}.
     * All other {@link Function} methods delegate to the original.
     */
    private static Function interceptExecute(Function initial, ExecuteInterceptor handler) {
        return (Function) Proxy.newProxyInstance(
            initial.getClass().getClassLoader(),
            new Class<?>[] {Function.class},
            (proxy, method, methodArgs) -> {
                if (method.getName().equals("execute")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args = (Map<String, Object>) methodArgs[0];
                    return handler.intercept(args);
                }
                try {
                    return method.invoke(initial, methodArgs);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        );
    }

    /** Maps each {@link Function} entry in an {@link Extension}'s function map. */
    @FunctionalInterface
    private interface FunctionEntryMapper {
        Map.Entry<String, Function> apply(Map.Entry<String, Function> entry);
    }

    /**
     * Returns a proxy for {@code initial} that intercepts {@link Extension#getFunctions()} and
     * passes each entry through {@code entryMapper}. All other {@link Extension} methods delegate
     * to the original.
     */
    private static Extension wrapExtension(Extension initial, FunctionEntryMapper entryMapper) {
        return (Extension) Proxy.newProxyInstance(
            initial.getClass().getClassLoader(),
            new Class<?>[] {Extension.class},
            (proxy, method, methodArgs) -> {
                if (method.getName().equals("getFunctions")) {
                    return initial.getFunctions().entrySet().stream()
                        .map(entryMapper::apply)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                }
                return method.invoke(initial, methodArgs);
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
}
