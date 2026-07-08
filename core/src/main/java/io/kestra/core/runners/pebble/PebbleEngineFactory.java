package io.kestra.core.runners.pebble;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.runners.configuration.VariableConfiguration;
import io.kestra.core.runners.pebble.functions.DayOfMonthFunction;
import io.kestra.core.runners.pebble.functions.DayOfWeekFunction;
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
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
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
     * <p>
     * This is intentionally an <em>allowlist</em>, not a denylist. Any function not listed here
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
     * <li>{@code secret(...)} → {@code [secret: KEY]} without invoking the real service.</li>
     * <li>{@code env(...)} → kept raw (see issue #16874: env vars are not resolved for display).</li>
     * <li>Functions in {@link #SAFE_DISPLAY_FUNCTIONS} (pure parsing / calendar helpers) work normally.</li>
     * <li>Every other function is removed from the engine, so any expression that calls one fails
     * to evaluate and the caller keeps the raw expression.</li>
     * </ul>
     */
    public PebbleEngine createRestricted() {
        PebbleEngine.Builder builder = newPebbleEngineBuilder();

        // Decorate each extension with a DisplayExtensionCustomizer so only safe functions survive.
        // registerExtensionCustomizer only wraps Pebble's built-in extensions, not those added here,
        // so the display filtering has to be applied explicitly to each bean extension.
        this.applicationContext.getBeansOfType(Extension.class).stream()
            .map(DisplayExtensionCustomizer::new)
            .forEach(builder::extension);

        return builder.build();
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

    private Extension extensionWithMaskedFunctions(VariableRenderer renderer, Extension initial, List<String> maskedFunctions) {
        return wrapExtension(initial, entry ->
        {
            if (maskedFunctions.contains(entry.getKey())) {
                return Map.entry(entry.getKey(), new MaskingFunction(entry.getValue(), "******"));
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
    // Function / extension wrappers
    // -------------------------------------------------------------------------

    /** Computes the replacement value for an intercepted {@link Function#execute} call. */
    @FunctionalInterface
    private interface ExecuteInterceptor {
        Object intercept(Map<String, Object> args);
    }

    /**
     * A {@link Function} that keeps the {@code delegate}'s argument metadata (so call-site argument
     * binding is unchanged) but replaces its {@code execute} with {@code handler}. Used at
     * display/render time to mask or block a function's real invocation.
     */
    private record InterceptingFunction(Function delegate, ExecuteInterceptor handler) implements Function {
        @Override
        public List<String> getArgumentNames() {
            return delegate.getArgumentNames();
        }

        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
            return handler.intercept(args);
        }
    }

    /**
     * A {@link Function} that still invokes the {@code delegate} — so its validation and errors are
     * preserved (e.g. {@code secret()} for a missing key still throws) — but replaces a
     * <em>successful</em> result with {@code mask}. Used by the secure renderer to mask secret values
     * without hiding evaluation failures.
     */
    private record MaskingFunction(Function delegate, Object mask) implements Function {
        @Override
        public List<String> getArgumentNames() {
            return delegate.getArgumentNames();
        }

        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
            delegate.execute(args, self, context, lineNumber);
            return mask;
        }
    }

    /**
     * A Pebble {@link io.pebbletemplates.pebble.extension.ExtensionCustomizer} for the display engine:
     * it keeps only the {@link #SAFE_DISPLAY_FUNCTIONS} (pure parsing / calendar helpers), masks
     * {@code secret()} as {@code [secret: KEY]} without touching the real secret service, and drops every
     * other function. A dropped function is unknown to Pebble, so any expression that calls one fails to
     * evaluate and the caller keeps the raw expression. Every other extension method delegates to the
     * wrapped extension unchanged.
     */
    static final class DisplayExtensionCustomizer extends io.pebbletemplates.pebble.extension.ExtensionCustomizer {
        DisplayExtensionCustomizer(Extension ext) {
            super(ext);
        }

        @Override
        public Map<String, Function> getFunctions() {
            Map<String, Function> functions = super.getFunctions();
            if (functions == null) {
                return null;
            }

            Map<String, Function> result = new HashMap<>();
            functions.forEach((name, function) ->
            {
                if (SAFE_DISPLAY_FUNCTIONS.contains(name)) {
                    result.put(name, function);
                } else if (name.equals(SecretFunction.NAME)) {
                    result.put(
                        name, new InterceptingFunction(
                            function,
                            args -> "[secret: " + args.getOrDefault("key", "?") + "]"
                        )
                    );
                }
                // Everything else (env(), kv(), now(), uuid(), read(), …) is dropped.
            });
            return result;
        }
    }

    /** Maps each {@link Function} entry in an {@link Extension}'s function map; returning {@code null} removes it. */
    @FunctionalInterface
    private interface FunctionEntryMapper {
        Map.Entry<String, Function> apply(Map.Entry<String, Function> entry);
    }

    /**
     * Returns a proxy for {@code initial} that intercepts {@link Extension#getFunctions()} and
     * passes each entry through {@code entryMapper} (a {@code null} result drops the function).
     * All other {@link Extension} methods delegate to the original.
     */
    private static Extension wrapExtension(Extension initial, FunctionEntryMapper entryMapper) {
        return (Extension) Proxy.newProxyInstance(
            initial.getClass().getClassLoader(),
            new Class<?>[] { Extension.class },
            (proxy, method, methodArgs) ->
            {
                if (method.getName().equals("getFunctions")) {
                    return initial.getFunctions().entrySet().stream()
                        .map(entryMapper::apply)
                        .filter(Objects::nonNull)
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
