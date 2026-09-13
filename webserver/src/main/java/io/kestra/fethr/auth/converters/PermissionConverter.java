package io.kestra.fethr.auth.converters;

import java.util.Optional;

import io.kestra.fethr.auth.Permission;
import io.kestra.fethr.auth.PermissionVo;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

/**
 * Converts a {@link Permission} enum constant into its {@link PermissionVo} catalogue row (key, resource,
 * impact, order). Registered as a Micronaut {@link TypeConverter} bean so the permission-catalogue endpoint
 * converts through the conversion service instead of mapping by hand.
 */
@Prototype
public class PermissionConverter implements TypeConverter<Permission, PermissionVo> {
    @Override
    public Optional<PermissionVo> convert(Permission permission, Class<PermissionVo> targetType, ConversionContext context) {
        return Optional.of(
            new PermissionVo(
                permission.getValue(),
                permission.getResource(),
                permission.getAction(),
                permission.getOrder()
            )
        );
    }
}
