package io.kestra.fethr.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

/**
 * Validates that a role name is present and is one of the realm composites (owner/admin/member/viewer).
 * The custom validator owns the unknown-role case so it surfaces as a clean 422; Jackson never has to know
 * about the role enum. Owner passes this constraint; restricting who may assign it (only an owner can) is
 * enforced in the controller, not here.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RoleValidator.class)
@Target({ FIELD, PARAMETER, TYPE_USE })
public @interface RoleCheck {
    String message() default "must be a valid role: owner, admin, member or viewer";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
