package io.kestra.webserver.controllers;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.utils.flow.FlowUtilsTest;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.rxjava2.http.client.RxHttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static io.micronaut.http.HttpRequest.POST;
import static org.hamcrest.MatcherAssert.assertThat;

@MicronautTest
public class EditorControllerTest {
    @Inject
    @Client("/")
    RxHttpClient client;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void distinctNamespaces() throws IOException {
        Flow flow = FlowUtilsTest.generateFlow();
        client.toBlocking().retrieve(POST("/api/v1/flows", flow), FlowWithSource.class);

        String prefix = "/" + IdUtils.create();
        storageInterface.createDirectory(null, toNamespacedStorageUri("io.namespace", URI.create(prefix)));
        MultipartBody body = MultipartBody.builder()
            .addPart("fileContent", "test.txt", "Hello".getBytes())
            .build();
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/namespaces/io.namespace/files?path=" + prefix + "/test.txt", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        );

        List<String> namespaces = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/files/distinct-namespaces"), List.class);

        assertThat(namespaces.size(), Matchers.greaterThan(3));
    }

    private URI toNamespacedStorageUri(String namespace, @Nullable URI relativePath) {
        return URI.create(storageInterface.namespaceFilePrefix(namespace) + Optional.ofNullable(relativePath).map(URI::getPath).orElse(""));
    }

}
