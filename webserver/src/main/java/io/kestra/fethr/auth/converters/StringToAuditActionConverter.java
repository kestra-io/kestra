package io.kestra.fethr.auth.converters;

import java.util.Optional;

import io.kestra.fethr.auth.AuditAction;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

/**
 * Resolves an {@link AuditAction} from its stable machine {@code code} (e.g. {@code login}) for request binding,
 * so the audit controller takes the {@code actions} filter as the {@link AuditAction} enum directly: Micronaut
 * binds each query value through this converter (registered in the shared conversion service). An unknown code
 * converts to empty.
 */
@Prototype
public class StringToAuditActionConverter implements TypeConverter<String, AuditAction> {
    @Override
    public Optional<AuditAction> convert(String code, Class<AuditAction> targetType, ConversionContext context) {
        return AuditAction.fromCode(code);
    }
}
