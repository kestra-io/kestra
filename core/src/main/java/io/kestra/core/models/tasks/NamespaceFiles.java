package io.kestra.core.models.tasks;

import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import javax.validation.Valid;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Introspected
public class NamespaceFiles {
    @Valid
    private List<String> include;

    @Valid
    private List<String> exclude;
}
