package io.kestra.core.models.tasks.logs;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.runners.RunContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Flux;

@Plugin
@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
public abstract class LogExporter<T extends Output>  implements io.kestra.core.models.Plugin {
    @NotNull
    @NotBlank
    @Pattern(regexp="^[a-zA-Z0-9][a-zA-Z0-9_-]*")
    protected String id;

    @NotBlank
    @Pattern(regexp="\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*")
    protected String type;

    public abstract T sendLogs(RunContext runContext, Flux<LogRecord> logRecord) throws Exception;

}
