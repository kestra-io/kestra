package io.kestra.core.exceptions;

import io.kestra.core.models.flows.Data;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Output;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exception that can be thrown when Inputs/Outputs have validation problems.
 */
public class InputOutputValidationException extends KestraRuntimeException {
    public InputOutputValidationException(String message) {
        super(message);
    }
    public static InputOutputValidationException of( String message, Input<?> input){
        String inputMessage = "Invalid value for input" + " `" + input.getId() + "`. Cause: " + message;
        return new InputOutputValidationException(inputMessage);
    }
    public static InputOutputValidationException of( String message, Output output){
        String outputMessage = "Invalid value for output" + " `" + output.getId() + "`. Cause: " + message;
        return new InputOutputValidationException(outputMessage);
    }
    public static InputOutputValidationException of(String message){
        return new InputOutputValidationException(message);
    }

    public static InputOutputValidationException merge(Set<InputOutputValidationException> exceptions){
        String combinedMessage = exceptions.stream()
                .map(InputOutputValidationException::getMessage)
                .collect(Collectors.joining(System.lineSeparator()));
        throw new InputOutputValidationException(combinedMessage);
    }

}
