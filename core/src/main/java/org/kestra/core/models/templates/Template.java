package org.kestra.core.models.templates;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Getter;
import org.kestra.core.models.DeletedInterface;
import org.kestra.core.models.tasks.Task;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.List;

@Builder
@Introspected
@Getter
public class Template {

    @NotNull
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]+")
    private String id;

    @Valid
    @NotEmpty
    private List<Task> tasks;

}
