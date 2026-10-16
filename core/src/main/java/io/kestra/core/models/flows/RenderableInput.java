package io.kestra.core.models.flows;

import java.util.function.Function;

import io.kestra.core.utils.PebbleUtil;

import jakarta.validation.constraints.NotNull;

/**
 * Represents an {@link Input} having properties that can be rendered.
 */
public interface RenderableInput {

    /**
     * Renders the {@link Input}.
     *
     * @param renderer The function to be used for rendering expression.
     * @return the rendered input.
     */
    Input<?> render(@NotNull Function<String, Object> renderer);

    /**
     * Static helper method that will render an input only if it implements
     * the {@link RenderableInput} interface.
     *
     * @param input The input.
     * @param renderer The function to be used for rendering expression.
     * @return the rendered input.
     */
    static Input<?> mayRenderInput(
        @NotNull final Input<?> input,
        @NotNull final Function<String, Object> renderer) {
        Input<?> rendered = input instanceof RenderableInput renderableInput ? renderableInput.render(renderer) : input;
        if (rendered.getDescription() != null && PebbleUtil.containsOpeningBlockDelimiter(rendered.getDescription())) {
            Object description = renderer.apply(rendered.getDescription());
            return rendered.withDescription(description == null ? "" : description.toString());
        }
        return rendered;
    }

}
