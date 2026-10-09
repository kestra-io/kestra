package io.kestra.core.models.annotations;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Tells the Cases ticketing setup how to use a property of a task implementing {@code TicketingTaskInterface}.
 */
@Documented
@Retention(RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
public @interface TicketingField {
    enum Role {
        NONE,
        /** Input receiving the case id. */
        CASE_ID,
        /** Input receiving the case title. */
        CASE_TITLE,
        /** Input receiving the case description. */
        CASE_DESCRIPTION,
        /** Input receiving the case description, for an HTML body: the text is escaped and line breaks become {@code <br>}. */
        CASE_DESCRIPTION_HTML,
        /** Input receiving the link to the case. */
        CASE_URL,
        /** Input receiving the case severity, translated through {@link #valueMap()} when the task uses its own vocabulary. */
        CASE_SEVERITY,
        /** Output holding the key of the created ticket. */
        TICKET_KEY,
        /** Output holding the link to the created ticket. */
        TICKET_URL
    }

    /**
     * @return what the property carries: an input receiving a field of the case, or an output holding the created ticket's key or URL.
     */
    Role role() default Role.NONE;

    /**
     * @return {@code CASE_VALUE=TASK_VALUE} pairs translating a case value into the task's own, for example {@code CRITICAL=urgent}; a value without a pair is passed unchanged.
     */
    String[] valueMap() default {};

    /**
     * @return the value pre-filled when a Cases ticketing flow is generated; it does not change the task's own default.
     */
    String defaultValue() default "";
}
