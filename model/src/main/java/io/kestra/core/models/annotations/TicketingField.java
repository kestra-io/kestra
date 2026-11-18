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
     * @return what the property carries: an input receiving a field of the case, or an output holding the created ticket's key or URL. Use each role once per task, except {@link Role#CASE_SEVERITY}, which may fill several fields.
     */
    Role role() default Role.NONE;

    @Documented
    @Retention(RUNTIME)
    @Target({})
    @interface Mapping {
        /** @return the case value, for example {@code CRITICAL}. */
        String from();

        /** @return the task's own value it is translated to. */
        String to();
    }

    /**
     * Only used for {@link Role#CASE_SEVERITY}: translates a case severity into the task's own vocabulary.
     * A severity without a mapping is passed unchanged, so on a property that is not a string the mappings must cover every severity.
     *
     * @return the mappings, in order
     */
    Mapping[] valueMap() default {};

    /**
     * @return the value suggested when a Cases ticketing flow is generated; it does not change the task's own default. Always a string, so it suits scalar properties.
     */
    String defaultValue() default "";
}
