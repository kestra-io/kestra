package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractApiCommand;
import io.kestra.cli.AbstractValidateCommand;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.netty.DefaultHttpClient;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "delete",
    description = "Delete a single flow",
    mixinStandardHelpOptions = true
)
@Slf4j
public class FlowDeleteCommand extends AbstractApiCommand {

    @CommandLine.Parameters(index = "0", description = "The namespace of the flow")
    public String namespace;

    @CommandLine.Parameters(index = "1", description = "The ID of the flow")
    public String id;

    @SuppressWarnings("deprecation")
    @Override
    public Integer call() throws Exception {
        super.call();

        try(DefaultHttpClient client = client()) {
            MutableHttpRequest<String> request = HttpRequest
                .DELETE(apiUri("/flows/" + namespace + "/" + id ));

            client.toBlocking().exchange(
                this.requestOptions(request)
            );

            stdOut("Flow successfully deleted !");
        } catch (HttpClientResponseException e){
            AbstractValidateCommand.handleHttpException(e, "flow");
            return 1;
        }

        return 0;
    }
}
