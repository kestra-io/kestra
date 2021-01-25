package org.kestra.cli;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.netty.DefaultHttpClient;
import picocli.CommandLine;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class AbstractApiCommand extends AbstractCommand {
    @CommandLine.Option(names = {"--server"}, description = "Also write core tasks plugins")
    protected URL server;

    @CommandLine.Option(names = {"--headers"}, description = "Also write core tasks plugins")
    protected Map<CharSequence, CharSequence> headers;

    @CommandLine.Option(names = {"--user"}, description = "<user:password> Server user and password")
    protected String user;

    public AbstractApiCommand(boolean withServer) {
        super(withServer);
    }

    protected DefaultHttpClient client() {
        return new DefaultHttpClient(server);
    }

    protected <T> HttpRequest<T> requestOptions(MutableHttpRequest<T> request) {
        if (this.headers != null) {
            request.headers(this.headers);
        }

        if (this.user != null) {
            List<String> split = Arrays.asList(this.user.split(":"));
            String user = split.get(0);
            String password = String.join(":", split.subList(1, split.size()));

            request.basicAuth(user, password);
        }

        return request;
    }
}
