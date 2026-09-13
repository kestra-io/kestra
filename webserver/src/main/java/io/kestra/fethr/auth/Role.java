package io.kestra.fethr.auth;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The four composite roles defined in the Keycloak realm.
 *
 * <p>
 * The realm is the source of truth; this is the typed catalogue of its names so call sites and DTOs
 * do not hand-roll strings. It (de)serializes as the realm role name, so it can be a DTO field
 * directly.
 *
 * <p>
 * Each composite aggregates some set of the atomic {@link Permission} roles. Which ones is decided
 * in Keycloak, not here.
 */
public enum Role {
    OWNER(Names.OWNER),
    ADMIN(Names.ADMIN),
    MEMBER(Names.MEMBER),
    VIEWER(Names.VIEWER);

    private final String roleName;

    Role(String roleName) {
        this.roleName = roleName;
    }

    @JsonValue
    public String roleName() {
        return roleName;
    }

    public static Optional<Role> fromRoleName(String roleName) {
        for (Role role : values()) {
            if (role.roleName.equals(roleName)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }

    /**
     * The same names as compile-time constants, because {@code @Secured} cannot call a method.
     * The enum constructors read these, so each name is written once.
     */
    public interface Names {
        String OWNER = "owner";
        String ADMIN = "admin";
        String MEMBER = "member";
        String VIEWER = "viewer";
    }
}
