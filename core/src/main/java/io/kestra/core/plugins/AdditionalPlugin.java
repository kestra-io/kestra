package io.kestra.core.plugins;

import io.kestra.core.models.Plugin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static io.kestra.core.utils.RegexPatterns.JAVA_IDENTIFIER_REGEX;

/**
 * Base class for a plugin extension point, i.e. a pluggable property of a task or trigger whose implementation
 * is itself a plugin, selected by its {@code type}.
 * <p>
 * A plugin declares an extension point by subclassing this, annotating that subclass with
 * {@code @JsonDeserialize(using = PluginDeserializer.class)}, and annotating each concrete implementation with a
 * bare {@code @JsonDeserialize()} so the deserializer is not re-invoked on itself.
 * <p>
 * An extension point <b>must be abstract</b>: that is how both mappers tell a base type apart from an
 * implementation, and a concrete one would recurse until the stack runs out.
 *
 * @see io.kestra.core.plugins.serdes.PluginDeserializer
 */
@io.kestra.core.models.annotations.Plugin
@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
public abstract class AdditionalPlugin implements Plugin {
    @NotNull
    @NotBlank
    @Pattern(regexp = JAVA_IDENTIFIER_REGEX)
    protected String type;
}
