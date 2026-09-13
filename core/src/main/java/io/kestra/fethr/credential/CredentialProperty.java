package io.kestra.fethr.credential;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a plugin task property whose value is the <em>name</em> of a saved credential.
 *
 * <p>
 * The property itself is a plain {@code Property<String>} -- the backend contract is just the
 * credential name, and everything else is resolved off the stored credential at run time. This
 * annotation is a UI hint: the no-code task form renders such a property as a credential picker, a
 * searchable dropdown of saved credentials of the declared {@link #type()} scoped to the flow's
 * namespace, instead of the plain text input the schema-driven form would otherwise produce.
 * Plain-text entry stays available as a fallback.
 *
 * <p>
 * Surfaced into the generated JSON schema as {@code $credentialType}, following the same
 * {@code $}-prefixed convention as {@code $dynamic} and {@code $group}.
 */
@Target({ ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CredentialProperty {
    /** The credential type the picker filters by. */
    CredentialType type();
}
