package io.kestra.core.annotations;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Type;
import io.micronaut.retry.annotation.DefaultRetryPredicate;
import io.micronaut.retry.annotation.RetryPredicate;
import io.micronaut.retry.intercept.OverrideRetryInterceptor;

import java.lang.annotation.*;

import jakarta.validation.constraints.Digits;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Inherited
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Around
@Type(OverrideRetryInterceptor.class)
public @interface Retryable {
    int MAX_INTEGRAL_DIGITS = 4;

    /**
     * @return The exception types to include (defaults to all)
     */
    Class<? extends Throwable>[] value() default {};

    /**
     * @return The exception types to include (defaults to all)
     */
    @AliasFor(member = "value")
    Class<? extends Throwable>[] includes() default {};

    /**
     * @return The exception types to exclude (defaults to none)
     */
    Class<? extends Throwable>[] excludes() default {};

    /**
     * @return The maximum number of retry attempts
     */
    @Digits(integer = MAX_INTEGRAL_DIGITS, fraction = 0)
    String attempts() default "${kestra.retries.attempts:5}";

    /**
     * @return The delay between retry attempts
     */
    String delay() default "${kestra.retries.delay:1s}";

    /**
     * @return The maximum overall delay
     */
    String maxDelay() default "${kestra.retries.max-delay:}";

    /**
     * @return The multiplier to use to calculate the delay
     */
    @Digits(integer = 2, fraction = 2)
    String multiplier() default "${kestra.retries.multiplier:2.0}";

    /**
     * @return The retry predicate class to use instead of {@link io.micronaut.retry.annotation.Retryable#includes} and {@link io.micronaut.retry.annotation.Retryable#excludes}
     * (defaults to none)
     */
    Class<? extends RetryPredicate> predicate() default DefaultRetryPredicate.class;
}