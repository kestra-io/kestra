package io.kestra.core.models.annotations;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Tells the Cases ticketing setup how to use a property of a task implementing {@code TicketingTaskInterface}.
 */
@Documented
@Inherited
@Retention(RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
public @interface TicketingField {
    enum Role {
        NONE,
        CASE_ID,
        CASE_TITLE,
        CASE_DESCRIPTION,
        CASE_URL,
        TICKET_KEY,
        TICKET_URL
    }

    /**
     * @return what the property carries: an input receiving a field of the case, or an output holding the created ticket's key or URL.
     */
    Role role() default Role.NONE;

    /**
     * @return the value pre-filled when a Cases ticketing flow is generated; it does not change the task's own default.
     */
    String defaultValue() default "";
}
