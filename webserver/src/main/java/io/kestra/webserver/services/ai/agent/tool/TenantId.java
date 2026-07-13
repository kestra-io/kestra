package io.kestra.webserver.services.ai.agent.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a tool method's tenant-targeting parameter. The parameter is always present in the Java
 * signature (so a {@code @Replaces} subclass can override the same method to validate it), but it is
 * <em>hidden from the model-facing tool spec</em> by the default {@link AiToolSpecFactory}. A
 * single-tenant surface (OSS) never shows or sets it, so the value resolves to the caller's own
 * tenant; an edition with multiple tenants exposes it (via its own {@link AiToolSpecFactory}) so a
 * caller with multi-tenant access can target another tenant per call, validating that tenant first.
 *
 * @see AiToolSpecifications#toolSpecificationFrom(java.lang.reflect.Method, boolean)
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantId {
}
