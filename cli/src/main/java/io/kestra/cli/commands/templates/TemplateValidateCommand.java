package io.kestra.cli.commands.templates;

import io.kestra.cli.AbstractValidateCommand;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.templates.TemplateEnabled;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlParser;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.Collections;

@CommandLine.Command(
    name = "validate",
    description = "Validate a template"
)
@TemplateEnabled
public class TemplateValidateCommand extends AbstractValidateCommand {
    @Inject
    private YamlParser yamlParser;

    @Inject
    private ModelValidator modelValidator;

    @Override
    public Integer call() throws Exception {
        return this.call(
            Template.class,
            yamlParser,
            modelValidator,
            (Object object) -> {
                Template template = (Template) object;
                return template.getNamespace() + " / " + template.getId();
            },
            (Object object) -> Collections.emptyList(),
            (Object object) -> Collections.emptyList()
        );
    }
}
