package io.kestra.worker.fetchers;

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.kestra.controller.grpc.OpaqueData;
import io.kestra.controller.grpc.WorkerControllerServiceGrpc.WorkerControllerServiceStub;
import io.kestra.controller.messages.MessageFormat;
import io.kestra.controller.messages.MessageFormats;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.WorkerLoop;
import io.kestra.controller.messages.FetchWorkerJobMessage;
import io.kestra.controller.messages.RequestOrResponseHeaderFactory;
import io.kestra.controller.messages.WorkerJobBatchMessage;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Component responsible for fetching worker jobs.
 */
@Prototype
@Slf4j
public class WorkerJobFetcher extends WorkerLoop {

    private final WorkerControllerServiceStub workerControllerServiceStub;
    private final WorkerQueueRegistry workerQueueRegistry;
    private WorkerQueue<WorkerJob> workerJobQueue;

    private final AtomicReference<ClientCallStreamObserver<OpaqueData>> currentStreamObserver = new AtomicReference<>();
    private WorkerContext workerContext;

    /**
     * Creates a new {@code WorkerJobFetcher} instance.
     *
     * @param workerControllerServiceStub the gRPC worker controller service stub.
     * @param workerQueueRegistry         the worker queue registry.
     */
    @Inject
    public WorkerJobFetcher(final WorkerControllerServiceStub workerControllerServiceStub, final WorkerQueueRegistry workerQueueRegistry) {
        super(WorkerJobFetcher.class.getSimpleName());
        this.workerQueueRegistry = workerQueueRegistry;
        this.workerControllerServiceStub = workerControllerServiceStub;
    }

    /**
     * Initialize the fetcher.
     *
     * @param workerContext the worker context.
     */
    public synchronized void init(final WorkerContext workerContext) {
        this.workerJobQueue = workerQueueRegistry.getOrCreate(workerContext, WorkerJob.class);
        this.workerContext = workerContext;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected void doOnLoop() throws Exception {

        OpaqueData request = OpaqueData.newBuilder()
            .setHeader(RequestOrResponseHeaderFactory.create(workerContext))
            .setMessage(MessageFormats.JSON.toByteString(new FetchWorkerJobMessage(workerContext.workerId(), workerContext.workerGroup())))
            .build();
        CountDownLatch completed = new CountDownLatch(1);

        // Start the streaming call
        ClientResponseObserver<OpaqueData, OpaqueData> streamCompleted = new ClientResponseObserver<>() {

            @Override
            public void beforeStart(ClientCallStreamObserver<OpaqueData> requestStream) {
                currentStreamObserver.set(requestStream);
            }

            @Override
            public void onNext(OpaqueData response) {
                log.trace("Received WorkerJob: {}", response);
                String messageFormat = response.getHeader().getMessageFormat();
                WorkerJobBatchMessage workerJobBatch = MessageFormat
                    .resolve(messageFormat)
                    .fromByteString(response.getMessage(), WorkerJobBatchMessage.class);

                if (workerJobBatch != null && !workerJobBatch.jobs().isEmpty()) {
                    workerJobBatch.jobs().forEach(workerJobQueue::put);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Stream error: {}", t.getMessage(), t);
                completed.countDown();
            }

            @Override
            public void onCompleted() {
                log.error("Stream completed");
                completed.countDown();
            }
        };
        workerControllerServiceStub.fetchWorkerJobsStream(request, streamCompleted);
        completed.await(); // Block until the stream ends
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void cleanup() {
        ClientCallStreamObserver<OpaqueData> active = currentStreamObserver.getAndSet(null);
        if (active != null) {
            active.cancel("Worker stopping", null);
        }
    }
}
