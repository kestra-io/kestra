package io.kestra.webserver.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FilenameUtils;

import io.kestra.core.http.HttpRequest.MultipartFormDataRequestBody;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;

import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.server.multipart.MultipartBody;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Turns the {@code multipart/form-data} body of a webhook request into a {@link MultipartFormDataRequestBody}.
 * <p>
 * The content of a file part is streamed into Kestra's internal storage rather than carried in memory, so that a
 * file of any size reaches the flow without travelling through the execution.
 */
@Singleton
public class WebhookMultipartService {
    private static final String PARTS_DIRECTORY = "webhook";

    private final StorageInterface storageInterface;

    @Inject
    public WebhookMultipartService(StorageInterface storageInterface) {
        this.storageInterface = Objects.requireNonNull(storageInterface);
    }

    /**
     * Collect the parts of a {@code multipart/form-data} webhook request, storing the content of the file parts
     * under the execution the request will create.
     *
     * @param parts       the parts of the request, {@code null} if it has none
     * @param flow        the flow the webhook trigger belongs to
     * @param executionId the identifier the execution created from the request will be given
     * @param contentType the content type of the request
     * @return the collected body
     */
    public Mono<MultipartFormDataRequestBody> collect(MultipartBody parts, Flow flow, String executionId, String contentType) {
        AtomicInteger index = new AtomicInteger();

        // Reading a part is blocking, so it happens off the event loop thread that emits it, as file inputs are
        // read in FlowInputOutput#readData. Note that both CompletedPart#getBytes and CompletedFileUpload#getInputStream
        // already release the part, so it must not be discarded here: once the read no longer runs inside onNext,
        // that would release it twice.
        return (parts == null ? Flux.<CompletedPart> empty() : Flux.from(parts))
            .publishOn(Schedulers.boundedElastic())
            .<MultipartFormDataRequestBody.Part> handle((part, sink) ->
            {
                try {
                    sink.next(toPart(part, flow, executionId, index.getAndIncrement()));
                } catch (IOException e) {
                    sink.error(e);
                }
            })
            .collectList()
            .map(collected -> MultipartFormDataRequestBody.builder()
                .contentType(contentType)
                .charset(StandardCharsets.UTF_8)
                .content(collected)
                .build());
    }

    private MultipartFormDataRequestBody.Part toPart(CompletedPart part, Flow flow, String executionId, int index) throws IOException {
        String contentType = part.getContentType().map(MediaType::getName).orElse(null);

        if (part instanceof CompletedFileUpload fileUpload && fileUpload.getFilename() != null && !fileUpload.getFilename().isBlank()) {
            // Read before the content, as the part is released once its stream is handed over.
            String filename = fileUpload.getFilename();
            long size = fileUpload.getSize();

            try (InputStream content = fileUpload.getInputStream()) {
                URI uri = storageInterface.put(
                    flow.getTenantId(),
                    flow.getNamespace(),
                    storageUri(flow, executionId, index, filename),
                    content
                );

                return new MultipartFormDataRequestBody.FilePart(part.getName(), filename, contentType, size, uri);
            }
        }

        return new MultipartFormDataRequestBody.FormFieldPart(part.getName(), contentType, part.getBytes());
    }

    /**
     * Build the storage URI a file part is stored under. Parts are numbered because their filenames are chosen by
     * the caller, and two parts of the same request may well carry the same one.
     */
    private static URI storageUri(Flow flow, String executionId, int index, String filename) {
        URI executionUri = StorageContext
            .forExecution(flow.getTenantId(), flow.getNamespace(), flow.getId(), executionId)
            .getContextStorageURI();

        // Only the file name is kept: the caller could otherwise reach outside the execution directory.
        return URI.create("%s/%s/%d/%s".formatted(executionUri, PARTS_DIRECTORY, index, FilenameUtils.getName(filename)));
    }
}
