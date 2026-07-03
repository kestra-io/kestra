package io.kestra.core.models.flows.input;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@link io.kestra.core.models.flows.Input} subtype as an Enterprise Edition feature. Such inputs are
 * registered in the polymorphic type system (so EE flows parse) but are excluded from the open-source flow JSON
 * schema, so the open-source editor neither proposes nor validates them. The EE schema generator includes them.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EeOnly {
}
