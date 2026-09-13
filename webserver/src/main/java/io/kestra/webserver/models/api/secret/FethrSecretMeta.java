package io.kestra.webserver.models.api.secret;

import java.time.Instant;
import java.util.List;

import io.kestra.fethr.secret.Secret;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * The metadata 2.0's secrets table renders: on top of {@link ApiSecretMeta}'s key, the namespace,
 * description and tags columns, and the last-updated timestamp it sorts by.
 *
 * <p>
 * The secret's value is deliberately absent. It is served only by the dedicated reveal endpoint, so
 * a plain listing can never spill plaintext.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class FethrSecretMeta extends ApiSecretMeta {

    @Parameter(name = "namespace", description = "The namespace holding the secret.")
    private final String namespace;

    @Parameter(name = "description", description = "The description of the secret.")
    private final String description;

    @Parameter(name = "tags", description = "The tags attached to the secret.")
    private final List<Secret.Tag> tags;

    @Parameter(name = "updated", description = "When the secret was last written.")
    private final Instant updated;

    public FethrSecretMeta(String key, String namespace, String description, List<Secret.Tag> tags, Instant updated) {
        super(key);
        this.namespace = namespace;
        this.description = description;
        this.tags = tags == null ? List.of() : tags;
        this.updated = updated;
    }

    public static FethrSecretMeta of(Secret secret) {
        return new FethrSecretMeta(
            secret.getKey(),
            secret.getNamespace(),
            secret.getDescription(),
            secret.getTags(),
            secret.getUpdated()
        );
    }
}
