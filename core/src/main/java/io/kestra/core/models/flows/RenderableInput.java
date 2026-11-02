package io.kestra.core.models.flows;

import java.util.function.Function;

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
     * Whether a value outside the rendered list is accepted. When it is, the list is not rendered
     * for an execution that already provides a value.
     */
    Boolean getAllowCustomValue();

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
        return input instanceof RenderableInput renderableInput ? renderableInput.render(renderer) : input;
    }

}
