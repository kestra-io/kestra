package io.kestra.worker.stores;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.worker.Controller;
import io.kestra.core.worker.models.WorkerInfo;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@FlakyTest(description = "Multiple subclasses share a Micronaut context with the same ${random.port} value; when one class stops the controller and another restarts it, the OS port may still be in TIME_WAIT causing bind failures")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractGrpcMetaStoreTest {

    @Inject
    ApplicationContext applicationContext;

    private Controller controller;

    @MockBean(WorkerInfo.class)
    WorkerInfo workerInfo() {
        WorkerInfo mock = mock(WorkerInfo.class);
        when(mock.getWorkerId()).thenReturn("controller-worker");
        return mock;
    }

    @BeforeAll
    void startController() {
        controller = applicationContext.createBean(Controller.class);
        controller.start();
        initClientStore();
    }

    @AfterAll
    void stopController() {
        if (controller != null) {
            controller.close();
        }
    }

    /**
     * Called after the gRPC server is started. Subclasses should create
     * their client-side gRPC store using the injected stub.
     */
    protected abstract void initClientStore();

    /**
     * Creates a mock {@link WorkerInfo} for the client-side gRPC store.
     */
    protected static WorkerInfo clientWorkerInfo() {
        WorkerInfo mock = mock(WorkerInfo.class);
        when(mock.getWorkerId()).thenReturn("test-worker");
        return mock;
    }
}
