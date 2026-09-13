package io.kestra.fethr.table.validation;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Deployment-wide settings for user-defined tables.
 *
 * <p>
 * The default keeps {@link TableNameValidationStrategy#STRICT}; a deployment that needs
 * digit-leading names opts in with {@code kestra.tables.name-validation: RELAXED}.
 */
@ConfigurationProperties("kestra.tables")
@Getter
@Setter
public class TablesConfig {
    private TableNameValidationStrategy nameValidation = TableNameValidationStrategy.STRICT;
}
