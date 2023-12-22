package io.micronaut.retry.intercept;

import io.kestra.core.annotations.Retryable;
import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.retry.RetryState;
import io.micronaut.retry.annotation.DefaultRetryPredicate;
import io.micronaut.retry.event.RetryEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Replace {@link DefaultRetryInterceptor} only to catch Throwable
 */
@Singleton
public class OverrideRetryInterceptor implements MethodInterceptor<Object, Object> {
    private static final Logger LOG = LoggerFactory.getLogger(OverrideRetryInterceptor.class);
    private final ApplicationEventPublisher<RetryEvent> eventPublisher;

    public OverrideRetryInterceptor(ApplicationEventPublisher<RetryEvent> eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public int getOrder() {
        return InterceptPhase.RETRY.getPosition();
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Optional<AnnotationValue<Retryable>> opt = context.findAnnotation(Retryable.class);
        if (opt.isEmpty()) {
            return context.proceed();
        }

        AnnotationValue<Retryable> retry = opt.get();

        MutableRetryState retryState = new SimpleRetry(
            retry.get("attempts", Integer.class).orElse(5),
            retry.get("multiplier", Double.class).orElse(2D),
            retry.get("delay", Duration.class).orElse(Duration.ofSeconds(1)),
            retry.get("maxDelay", Duration.class).orElse(null),
            new DefaultRetryPredicate(resolveIncludes(retry, "includes"), resolveIncludes(retry, "excludes")),
            Throwable.class
        );

        MutableConvertibleValues<Object> attrs = context.getAttributes();
        attrs.put(RetryState.class.getName(), retry);

        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, ConversionService.SHARED);
        try {
            retryState.open();

            Object result = retrySync(context, retryState, interceptedMethod);
            switch (interceptedMethod.resultType()) {
                case SYNCHRONOUS:
                    retryState.close(null);
                    return result;
                default:
                    return interceptedMethod.unsupported();
            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Class<? extends Throwable>> resolveIncludes(AnnotationValue<Retryable> retry, String includes) {
        Class<?>[] values = retry.classValues(includes);
        return (List) Collections.unmodifiableList(Arrays.asList(values));
    }

    private Object retrySync(MethodInvocationContext<Object, Object> context, MutableRetryState retryState, InterceptedMethod interceptedMethod) {
        boolean firstCall = true;
        while (true) {
            try {
                if (firstCall) {
                    firstCall = false;
                    return interceptedMethod.interceptResult();
                }
                return interceptedMethod.interceptResult(this);
            } catch (Throwable e) {
                if (!retryState.canRetry(e)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Cannot retry anymore. Rethrowing original exception for method: {}", context);
                    }
                    retryState.close(e);
                    throw e;
                } else {
                    long delayMillis = retryState.nextDelay();
                    try {
                        if (eventPublisher != null) {
                            try {
                                eventPublisher.publishEvent(new RetryEvent(context, retryState, e));
                            } catch (Exception e1) {
                                LOG.error("Error occurred publishing RetryEvent: " + e1.getMessage(), e1);
                            }
                        }
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Retrying execution for method [{}] after delay of {}ms for exception: {}", context, delayMillis, e.getMessage());
                        }
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException e1) {
                        throw e;
                    }
                }
            }
        }
    }
}
