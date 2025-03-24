package io.kestra.core.http.client.apache;

import io.kestra.core.http.HttpService;
import jakarta.annotation.Nullable;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntityContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

public abstract class AbstractLoggingInterceptor {
    protected static String buildHeadersEntry(String type, Header[] headers) {
        return type + " headers:" +
            (headers.length == 0 ?
                " null" :
                "\n    " + Arrays.stream(headers)
                    .map(header -> header.getName() + ": " + header.getValue())
                    .collect(Collectors.joining("\n    "))
            );
    }

    protected static String buildEntityEntry(String type, @Nullable HttpEntityContainer httpEntityContainer) throws IOException {
        if (httpEntityContainer != null && httpEntityContainer.getEntity() != null) {
            HttpService.HttpEntityCopy copy = HttpService.copy(httpEntityContainer.getEntity());
            httpEntityContainer.setEntity(copy);

            return type + " payload:" +
                "\n    " + new String(copy.getBody(), StandardCharsets.UTF_8);
        } else {
            return type + " payload: null";
        }
    }
}
