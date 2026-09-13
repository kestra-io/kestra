package io.kestra.fethr.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a plugin property that applies only for certain values of a sibling discriminator -- an
 * {@code action} or {@code mode} the task branches on.
 *
 * <p>
 * A UI hint and nothing more: the no-code form shows the property only while the sibling holds one
 * of {@link #values()}, and marks it required while the sibling holds one of {@link #requiredFor()}.
 * It changes neither bean validation nor deserialization nor {@code run()}, so a task still has to
 * enforce its own per-value rules -- see {@code TableRowsValidation} for the other half.
 *
 * <p>
 * Reaches the generated schema as {@code $appliesWhen}, following the same convention as
 * {@code $dynamic} and {@code $group}.
 */
@Target({ ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AppliesWhen {

    /** The sibling property whose value decides whether this one applies. */
    String property();

    /** The values of {@link #property()} for which this property is shown. */
    String[] values();

    /** The subset of {@link #values()} for which it is also required. */
    String[] requiredFor() default {};
}
