package io.kestra.fethr.auth.converters;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.keycloak.representations.idm.EventRepresentation;

import io.kestra.fethr.auth.AuditAction;
import io.kestra.fethr.auth.UserEventVo;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import jakarta.inject.Singleton;

/**
 * Converts a Keycloak user event ({@code LOGIN}) into the audit {@link UserEventVo} for the {@link
 * AuditAction#LOGIN} action. Registered as a Micronaut {@link TypeConverter} bean so the audit service converts
 * through the conversion service instead of mapping by hand. The subject user id and the username come straight
 * off the event (the username is in the event details); the audit service finalises the username from the realm
 * user map.
 */
@Singleton
public class EventToUserEventVoConverter implements TypeConverter<EventRepresentation, UserEventVo> {
    private static final String ID_FORMAT = "%s-%d-%s";

    @Override
    public Optional<UserEventVo> convert(EventRepresentation event, Class<UserEventVo> targetType, ConversionContext context) {
        String userId = StringUtils.defaultString(event.getUserId());
        String username = Optional.ofNullable(event.getDetails())
            .map(details -> details.get("username"))
            .filter(StringUtils::isNotBlank)
            .orElse(userId);
        String id = StringUtils.isBlank(event.getId())
            ? String.format(ID_FORMAT, AuditAction.LOGIN.getCode(), event.getTime(), userId)
            : event.getId();
        return Optional.of(
            new UserEventVo(
                id,
                AuditAction.LOGIN.getCategory().getCode(),
                AuditAction.LOGIN.getCode(),
                AuditAction.LOGIN.getShortDescription(),
                AuditAction.LOGIN.getLongDescription(),
                event.getTime(), userId, username,
                StringUtils.defaultString(event.getIpAddress())
            )
        );
    }
}
