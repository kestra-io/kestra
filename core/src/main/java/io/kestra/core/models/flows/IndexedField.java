package io.kestra.core.models.flows;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Definition of a flow's indexed field.
 * <p>
 * An indexed field is a key/value pair computed from a Pebble expression when the execution ends.
 * The value is coerced to a plain string and stored in a dedicated, searchable table so executions can be
 * looked up by these fields without indexing the full (and potentially large) task or flow outputs.
 */
@SuperBuilder
@Getter
@NoArgsConstructor
public class IndexedField implements Data {
    /**
     * The indexed field's unique key (name).
     */
    @NotNull
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][.a-zA-Z0-9_-]*")
    String id;

    /**
     * Short description of the indexed field.
     */
    String description;

    /**
     * The Pebble expression used to compute the indexed field value. The result is coerced to a plain string.
     */
    @NotNull
    @Schema(title = "The Pebble expression used to compute the indexed field value. The result is coerced to a plain string.")
    String value;

    String displayName;

    @Override
    public Type getType() {
        return Type.STRING;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }
}
