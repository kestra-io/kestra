package io.kestra.cli.commands.templates;

import io.kestra.cli.AbstractValidateCommand;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.templates.TemplateEnabled;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlFlowParser;
import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(
    name = "validate",
    description = "validate a template"
)
@TemplateEnabled
public class TemplateValidateCommand extends AbstractValidateCommand {
    @Inject
    private YamlFlowParser yamlFlowParser;

    @Inject
    private ModelValidator modelValidator;

    @Override
    public Integer call() throws Exception {
        return this.call(
            Template.class,
            yamlFlowParser,
            modelValidator,
            (Object object) -> {
                Template template = (Template) object;
                return template.getNamespace() + " / " + template.getId();
            }
        );
    }
}
