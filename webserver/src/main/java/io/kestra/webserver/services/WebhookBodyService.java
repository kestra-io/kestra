package io.kestra.webserver.services;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.io.FilenameUtils;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest.MultipartFormDataRequestBody;
import io.kestra.core.http.HttpRequest.RequestBody;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.plugin.core.trigger.AbstractWebhookTrigger;
import io.kestra.plugin.core.trigger.AbstractWebhookTrigger.FetchType;
import io.kestra.plugin.core.trigger.Webhook;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.server.multipart.MultipartBody;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Turns the body of a webhook request into what the trigger exposes to the flow.
 * <p>
 * The content of a file part is streamed into Kestra's internal storage rather than carried in memory, so that a
 * file of any size reaches the flow without travelling through the execution. The body of a request that is not
 * {@code multipart/form-data} is read according to the {@link FetchType} of the trigger it reaches.
 */
@Slf4j
@Singleton
public class WebhookBodyService {
    private static final String PARTS_DIRECTORY = "webhook";

    private static final String BODY_FILE = "body";

    private final StorageInterface storageInterface;

    @Inject
    public WebhookBodyService(StorageInterface storageInterface) {
        this.storageInterface = Objects.requireNonNull(storageInterface);
    }

    /**
     * Read the body of a webhook request that is not {@code multipart/form-data}, as the trigger asks for it.
     * <p>
     * The body is read off the connection in every case, so that the caller always gets its response back; what
     * the fetch type decides is where those bytes go - into the flow, into the internal storage, or nowhere.
     *
     * @param request     the incoming request
     * @param flow        the flow the webhook trigger belongs to
     * @param executionId the identifier the execution created from the request will be given
     * @param fetchType   what the trigger does with the body
     * @return the body of the request
     * @throws IOException if the body cannot be read, or cannot be stored
     */
    public Body read(io.micronaut.http.HttpRequest<?> request, Flow flow, String executionId, FetchType fetchType) throws IOException {
        ByteBody byteBody = request instanceof ServerHttpRequest<?> serverRequest ? serverRequest.byteBody() : null;

        if (byteBody == null) {
            // Not a request being served, so its body is only what was bound to it: reachable from a test, or from
            // an edition calling in with a request of its own making.
            return new Body(MicronautHttpService.from(request).getBody(), null);
        }

        try (InputStream content = byteBody.toInputStream()) {
            return read(request, flow, executionId, fetchType, content);
        }
    }

    /**
     * Verify configured signatures over the original bytes before returning a body for execution creation.
     * Stored content is deleted if verification or ingestion fails.
     */
    public Body read(io.micronaut.http.HttpRequest<?> request, Flow flow, String executionId,
        AbstractWebhookTrigger trigger, RunContext runContext) throws IOException, IllegalVariableEvaluationException {
        if (!(trigger instanceof Webhook webhook) || webhook.getSignature() == null) {
            return read(request, flow, executionId, trigger.getFetchType());
        }

        Webhook.Signature signature = webhook.getSignature();
        var headers = request.getHeaders().getAll(signature.getHeader());
        if (headers.size() != 1 || !(request instanceof ServerHttpRequest<?> serverRequest)) {
            throw invalidSignature();
        }
        String value = headers.getFirst();
        String prefix = signature.getPrefix();
        if (prefix != null) {
            if (!value.startsWith(prefix)) {
                throw invalidSignature();
            }
            value = value.substring(prefix.length());
        }

        Mac mac;
        try {
            mac = Mac.getInstance(signature.getAlgorithm().getJcaName());
            String secret = runContext.render(signature.getSecret()).as(String.class).orElseThrow();
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), mac.getAlgorithm()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to initialize webhook signature verification.", e);
        }
        if (value.length() != mac.getMacLength() * 2) {
            throw invalidSignature();
        }
        byte[] expected;
        try {
            expected = HexFormat.of().parseHex(value);
        } catch (IllegalArgumentException e) {
            throw invalidSignature();
        }

        try (InputStream content = new FilterInputStream(serverRequest.byteBody().toInputStream()) {
            @Override
            public int read() throws IOException {
                int value = in.read();
                if (value != -1) {
                    mac.update((byte) value);
                }
                return value;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                int count = in.read(bytes, offset, length);
                if (count > 0) {
                    mac.update(bytes, offset, count);
                }
                return count;
            }
        }) {
            Body body = read(request, flow, executionId, trigger.getFetchType(), content);
            if (!MessageDigest.isEqual(mac.doFinal(), expected)) {
                throw invalidSignature();
            }
            return body;
        } catch (IOException | RuntimeException e) {
            if (FetchType.STORE == trigger.getFetchType()) {
                deleteStored(flow, executionId);
            }
            throw e;
        }
    }

    /** Reject signed multipart requests whose raw bytes have already been consumed by the multipart parser. */
    public void validateMultipartSignature(AbstractWebhookTrigger trigger) {
        if (trigger instanceof Webhook webhook && webhook.getSignature() != null) {
            throw invalidSignature();
        }
    }

    /**
     * Collect the parts of a {@code multipart/form-data} webhook request, storing the content of the file parts
     * under the execution the request will create.
     * <p>
     * The parts are collected whatever the {@link FetchType} of the trigger: a file part is stored rather than
     * read into the flow in any case, so the fetch type has nothing to choose between here.
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

    /**
     * Delete what a webhook call stored, for a call that ends without creating an execution. Nothing else would
     * ever purge it, as the execution the files are scoped to will not exist.
     * <p>
     * A call that creates no execution because its conditions are not met, or because its inputs cannot be
     * rendered, is cleaned up by {@code WebhookService#newExecution} instead, which knows of that outcome.
     *
     * @param flow        the flow the webhook trigger belongs to
     * @param executionId the identifier the execution would have been given
     */
    public void deleteStored(Flow flow, String executionId) {
        URI prefix = URI.create("%s/%s".formatted(executionUri(flow, executionId), PARTS_DIRECTORY));

        try {
            storageInterface.deleteByPrefix(flow.getTenantId(), flow.getNamespace(), prefix);
        } catch (IOException e) {
            log.warn("Unable to delete the files stored for the webhook execution {}", executionId, e);
        }
    }

    private static HttpStatusException invalidSignature() {
        return new HttpStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature.");
    }

    private Body read(io.micronaut.http.HttpRequest<?> request, Flow flow, String executionId,
        FetchType fetchType, InputStream content) throws IOException {
        return switch (fetchType) {
            case NONE -> {
                content.transferTo(OutputStream.nullOutputStream());
                yield Body.EMPTY;
            }
            case STORE -> store(content, flow, executionId);
            case FETCH -> new Body(
                MicronautHttpService.byteArrayRequestBody(request.getContentType().orElse(null), content.readAllBytes()),
                null
            );
        };
    }

    /**
     * Stream the body into the internal storage, under the execution the request will create.
     */
    private Body store(InputStream input, Flow flow, String executionId) throws IOException {
        try (PushbackInputStream content = new PushbackInputStream(input)) {
            int first = content.read();
            if (-1 == first) {
                // A request without a body has nothing to store, and must not leave an empty file behind.
                return Body.EMPTY;
            }
            content.unread(first);

            return new Body(
                null,
                storageInterface.put(flow.getTenantId(), flow.getNamespace(), bodyStorageUri(flow, executionId), content)
            );
        }
    }

    private MultipartFormDataRequestBody.Part toPart(CompletedPart part, Flow flow, String executionId, int index) throws IOException {
        String contentType = Optional.ofNullable(part.getMetadata().mediaType()).map(MediaType::getName).orElse(null);

        if (part instanceof CompletedFileUpload fileUpload && fileUpload.getFilename() != null && !fileUpload.getFilename().isBlank()) {
            // Read before the content, as the part is released once its stream is handed over.
            String filename = fileUpload.getFilename();
            long size = fileUpload.getSize();

            try (InputStream content = fileUpload.getInputStream()) {
                URI uri = storageInterface.put(
                    flow.getTenantId(),
                    flow.getNamespace(),
                    partStorageUri(flow, executionId, index, filename),
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
    private static URI partStorageUri(Flow flow, String executionId, int index, String filename) {
        // Only the file name is kept: the caller could otherwise reach outside the execution directory.
        return URI.create("%s/%s/%d/%s".formatted(executionUri(flow, executionId), PARTS_DIRECTORY, index, FilenameUtils.getName(filename)));
    }

    /**
     * Build the storage URI the body is stored under. A request carries a single body, and it has no name of its
     * own, so it needs neither a number nor anything the caller chose.
     */
    private static URI bodyStorageUri(Flow flow, String executionId) {
        return URI.create("%s/%s/%s".formatted(executionUri(flow, executionId), PARTS_DIRECTORY, BODY_FILE));
    }

    private static URI executionUri(Flow flow, String executionId) {
        return StorageContext
            .forExecution(flow.getTenantId(), flow.getNamespace(), flow.getId(), executionId)
            .getContextStorageURI();
    }

    /**
     * The body of a webhook request, as the trigger will see it.
     *
     * @param requestBody the body to attach to the request, {@code null} if the request has none, or if it was
     *                    stored rather than read into the flow
     * @param storedUri   the URI the body was stored under, {@code null} unless the trigger fetches it as
     *                    {@link FetchType#STORE}
     */
    public record Body(RequestBody requestBody, URI storedUri) {
        private static final Body EMPTY = new Body(null, null);
    }
}
