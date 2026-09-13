package io.kestra.fethr.auth.converters;

import java.util.Optional;

import io.kestra.fethr.auth.AuditAction;
import io.kestra.fethr.auth.AuditActionVo;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

/**
 * Converts an {@link AuditAction} enum constant into its {@link AuditActionVo} inventory row (category, code,
 * short / long description). Registered as a Micronaut {@link TypeConverter} bean so the audit-action inventory
 * endpoint converts through the conversion service instead of mapping by hand.
 */
@Prototype
public class AuditActionConverter implements TypeConverter<AuditAction, AuditActionVo> {
    @Override
    public Optional<AuditActionVo> convert(AuditAction action, Class<AuditActionVo> targetType, ConversionContext context) {
        return Optional.of(
            new AuditActionVo(
                action.getCategory().getCode(),
                action.getCode(),
                action.getShortDescription(),
                action.getLongDescription()
            )
        );
    }
}
