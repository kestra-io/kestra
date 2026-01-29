package io.kestra.cli;

import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.serializers.YamlParser;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.netty.DefaultHttpClient;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwConsumer;

public abstract class AbstractValidateCommand extends AbstractApiCommand {
    @CommandLine.Parameters(index = "0", description = "The directory containing files to check")
    protected Path directory;

    @CommandLine.Option(names = {"--local"}, description = "Whether validation should be done locally or using a remote server", defaultValue = "false")
    protected boolean local;

    @Inject
    private TenantIdSelectorService tenantService;

    /**
     * {@inheritDoc}
     **/
    @Override
    protected boolean loadExternalPlugins() {
        return local;
    }

    public static void handleException(ConstraintViolationException e, String resource) {
        stdErr("\t@|fg(red) Unable to parse {0} due to the following error(s):|@", resource);
        e.getConstraintViolations()
            .forEach(constraintViolation -> {
                stdErr(
                    "\t- @|bold,yellow {0} : {1} |@",
                    constraintViolation.getMessage().replace("\n", " - "),
                    constraintViolation.getPropertyPath()
                );
            });
    }

    public static void handleHttpException(HttpClientResponseException e, String resource) {
        stdErr("\t@|fg(red) Unable to parse {0}s due to the following error:|@", resource);
        stdErr(
            "\t- @|bold,yellow {0}|@",
            e.getMessage()
        );
    }

    public static void handleValidateConstraintViolation(ValidateConstraintViolation validateConstraintViolation, String resource) {
        stdErr("\t@|fg(red) Unable to parse {0}s due to the following error:|@", resource);
        stdErr(
            "\t- @|bold,yellow {0}|@",
            validateConstraintViolation.getConstraints()
        );
    }

    // bug in micronaut, we can't inject ModelValidator, so we inject from implementation
    public Integer call(
        Class<?> cls,
        ModelValidator modelValidator,
        Function<Object, String> identity,
        Function<Object, List<String>> warningsFunction,
        Function<Object, List<String>> infosFunction
    ) throws Exception {
        super.call();

        AtomicInteger returnCode = new AtomicInteger(0);
        String clsName = cls.getSimpleName().toLowerCase();

        try (Stream<Path> files = Files.walk(directory)) {
            List<Path> flows = files
                .filter(Files::isRegularFile)
                .filter(YamlParser::isValidExtension)
                .toList();

            // At least one flow file is expected for update
            if (flows.isEmpty()) {
                stdErr("No flow found in ''{0}''!", directory.toFile().getAbsolutePath());
                return 1;
            }

            if (this.local) {
                // Perform local validation
                flows.forEach(flow -> {
                    try {
                        Object parse = YamlParser.parse(flow.toFile(), cls);
                        modelValidator.validate(parse);

                        stdOut("@|green \u2713|@ - {0}", identity.apply(parse));

                        List<String> warnings = warningsFunction.apply(parse);
                        warnings.forEach(warning -> stdOut("@|bold,yellow \u26A0|@ - {0}", warning));

                        List<String> infos = infosFunction.apply(parse);
                        infos.forEach(info -> stdOut("@|bold,blue \u2139|@ - {0}", info));
                    } catch (ConstraintViolationException e) {
                        stdErr("@|red \u2718|@ - {0}", flow);
                        AbstractValidateCommand.handleException(e, clsName);
                        returnCode.set(1);
                    }
                });
            } else {
                // Build multipart body with all available flow files
                MultipartBody.Builder bodyBuilder = MultipartBody.builder();
                flows.forEach(flow -> bodyBuilder.addPart("flows", flow.toFile().getName(), MediaType.APPLICATION_YAML_TYPE, flow.toFile()));

                // Call validate API
                try (DefaultHttpClient client = client()) {
                    MutableHttpRequest<MultipartBody> request = HttpRequest.POST(
                        apiUri("/flows/validate", tenantService.getTenantIdAndAllowEETenants(tenantId)),
                        bodyBuilder.build()
                    ).contentType(MediaType.MULTIPART_FORM_DATA);

                    List<ValidateConstraintViolation> validations = client.toBlocking().retrieve(
                        this.requestOptions(request),
                        Argument.listOf(ValidateConstraintViolation.class)
                    );

                    validations.forEach(throwConsumer(validation -> {
                        if (validation.getConstraints() == null) {
                            stdOut("@|green \u2713|@ - {0}", validation.getIdentity());
                        } else {
                            stdErr("@|red \u2718|@ - {0}", validation.getIdentity());
                            AbstractValidateCommand.handleValidateConstraintViolation(validation, clsName);
                            returnCode.set(1);
                        }
                    }));
                } catch (HttpClientResponseException e) {
                    AbstractValidateCommand.handleHttpException(e, clsName);
                    return 1;
                }
            }
        }

        return returnCode.get();
    }
}
