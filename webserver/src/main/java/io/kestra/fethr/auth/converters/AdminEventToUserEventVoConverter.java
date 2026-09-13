package io.kestra.fethr.auth.converters;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.keycloak.representations.idm.AdminEventRepresentation;

import io.kestra.fethr.auth.AuditAction;
import io.kestra.fethr.auth.UserEventVo;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import jakarta.inject.Singleton;

/**
 * Converts a Keycloak admin event into the audit {@link UserEventVo}, empty when the event is not one of the
 * surfaced actions (server-managed sign-out -> {@link AuditAction#LOGOUT}, admin-driven password reset ->
 * {@link AuditAction#PASSWORD_CHANGED}). Registered as a Micronaut {@link TypeConverter} bean so the audit
 * service converts through the conversion service instead of mapping by hand.
 *
 * <p>
 * Both the subject and the action are derived from the resource path because Keycloak does not expose them
 * any other way: {@link AdminEventRepresentation#getAuthDetails()} carries the ACTOR's user id (who performed
 * the action), not the target, and Keycloak records both sign-out and password reset under the same operation
 * with no distinct event type. The resource path ({@code users/{id}/logout}, {@code users/{id}/reset-password})
 * is the only place the target id and the action are available. The username is set to the user id here and
 * finalised by the audit service from the realm user map.
 * </p>
 */
@Singleton
public class AdminEventToUserEventVoConverter implements TypeConverter<AdminEventRepresentation, UserEventVo> {
    private static final String ID_FORMAT = "%s-%d-%s";

    @Override
    public Optional<UserEventVo> convert(AdminEventRepresentation event, Class<UserEventVo> targetType, ConversionContext context) {
        return adminEventAction(event.getResourcePath()).map(action ->
        {
            String userId = userIdFromResourcePath(event.getResourcePath());
            String ip = event.getAuthDetails() != null
                ? StringUtils.defaultString(event.getAuthDetails().getIpAddress())
                : "";
            String id = StringUtils.isBlank(event.getId())
                ? String.format(ID_FORMAT, action.getCode(), event.getTime(), userId)
                : event.getId();
            return new UserEventVo(
                id,
                action.getCategory().getCode(),
                action.getCode(),
                action.getShortDescription(),
                action.getLongDescription(),
                event.getTime(), userId, userId, ip
            );
        });
    }

    private Optional<AuditAction> adminEventAction(String resourcePath) {
        if (resourcePath == null) {
            return Optional.empty();
        }
        if (resourcePath.endsWith("/logout")) {
            return Optional.of(AuditAction.LOGOUT);
        }
        if (resourcePath.contains("reset-password")) {
            return Optional.of(AuditAction.PASSWORD_CHANGED);
        }
        return Optional.empty();
    }

    private String userIdFromResourcePath(String resourcePath) {
        if (resourcePath == null) {
            return "";
        }
        String[] parts = resourcePath.split("/");
        return parts.length >= 2 ? parts[1] : "";
    }
}
