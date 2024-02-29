package io.kestra.jdbc.repository;

import io.kestra.core.runners.ServerInstance;
import io.kestra.core.runners.WorkerInstance;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

@MicronautTest(transactional = false)
public abstract class AbstractJdbcWorkerInstanceRepositoryTest {
    @Inject
    protected AbstractJdbcWorkerInstanceRepository workerInstanceRepository;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    protected JooqDSLContextWrapper dslContextWrapper;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Test
    protected void save() {
        WorkerInstance workerInstance = createWorkerInstance(UUID.randomUUID().toString());
        workerInstanceRepository.save(workerInstance);

        Optional<WorkerInstance> find = workerInstanceRepository.findByWorkerUuid(workerInstance.getWorkerUuid().toString());
        assertThat(find.isPresent(), is(true));
        assertThat(find.get().getWorkerUuid(), is(workerInstance.getWorkerUuid()));
    }

    @Test
    protected void delete() {
        WorkerInstance workerInstance = createWorkerInstance(UUID.randomUUID().toString());
        workerInstanceRepository.save(workerInstance);

        dslContextWrapper.transaction(configuration -> {
            DSLContext context = DSL.using(configuration);

            Optional<WorkerInstance> find = workerInstanceRepository.findByWorkerUuid(workerInstance.getWorkerUuid().toString());
            assertThat(find.isPresent(), is(true));
            assertThat(find.get().getWorkerUuid(), is(workerInstance.getWorkerUuid()));

            workerInstanceRepository.delete(context, workerInstance);
            find = workerInstanceRepository.findByWorkerUuid(workerInstance.getWorkerUuid().toString());
            assertThat(find.isPresent(), is(false));
        });
    }

    @Test
    protected void findAll() {
        WorkerInstance workerInstance = createWorkerInstance(UUID.randomUUID().toString());
        WorkerInstance workerInstanceAlive = createWorkerInstance(UUID.randomUUID().toString());
        WorkerInstance workerInstanceDead = createWorkerInstance(UUID.randomUUID().toString(), false);

        workerInstanceRepository.save(workerInstance);
        workerInstanceRepository.save(workerInstanceAlive);
        workerInstanceRepository.save(workerInstanceDead);

        dslContextWrapper.transaction(configuration -> {
            DSLContext context = DSL.using(configuration);

            List<WorkerInstance> finds = workerInstanceRepository.findAll(context);
            assertThat(finds.size(), is(3));

            finds = workerInstanceRepository.findAllToDelete(context);
            assertThat(finds.size(), is(1));

            finds = workerInstanceRepository.findAllAlive(context);
            assertThat(finds.size(), is(2));
        });
    }

    @Test
    protected void find() {
        WorkerInstance workerInstance = createWorkerInstance(UUID.randomUUID().toString());
        workerInstanceRepository.save(workerInstance);

        Optional<WorkerInstance> find = workerInstanceRepository.findByWorkerUuid(workerInstance.getWorkerUuid().toString());
        assertThat(find.isPresent(), is(true));
        assertThat(find.get().getWorkerUuid(), is(workerInstance.getWorkerUuid()));
    }

    @Test
    protected void heartbeatCheckup() throws InterruptedException {
        WorkerInstance workerInstance = createWorkerInstance(UUID.randomUUID().toString());
        workerInstanceRepository.save(workerInstance);
        CountDownLatch queueCount = new CountDownLatch(1);

        queueCount.await(15, TimeUnit.SECONDS);
        Optional<WorkerInstance> updatedWorkerInstance = workerInstanceRepository.heartbeatCheckUp(workerInstance.getWorkerUuid().toString());
        assertThat(updatedWorkerInstance.isPresent(), is(true));
        assertThat(updatedWorkerInstance.get().getHeartbeatDate(), greaterThan(workerInstance.getHeartbeatDate()));
    }

    @Test
    protected void heartbeatsStatusUpdate() {
        WorkerInstance workerInstance = createWorkerInstance(UUID.randomUUID().toString());
        workerInstance.setHeartbeatDate(Instant.now().minusSeconds(3600));
        workerInstanceRepository.save(workerInstance);

        dslContextWrapper.transaction(configuration -> {
            DSLContext context = DSL.using(configuration);

            workerInstanceRepository.heartbeatsStatusUpdate(context);
            Optional<WorkerInstance> find = workerInstanceRepository.findByWorkerUuid(workerInstance.getWorkerUuid().toString());
            assertThat(find.isPresent(), is(true));
            assertThat(find.get().getStatus(), is(WorkerInstance.Status.DEAD));
        });
    }

    private WorkerInstance createWorkerInstance(String workerUuid, Boolean alive) {
        return WorkerInstance.builder()
            .workerUuid(UUID.fromString(workerUuid))
            .workerGroup(null)
            .managementPort(0)
            .hostname("kestra.io")
            .partitions(null)
            .port(0)
            .status(alive ? WorkerInstance.Status.UP : WorkerInstance.Status.DEAD)
            .heartbeatDate(alive ?  Instant.now() : Instant.now().minusSeconds(3600))
            .server(new ServerInstance(UUID.randomUUID())) // simulate worker is running on different server
            .build();
    }

    private WorkerInstance createWorkerInstance(String workerUuid) {
        return createWorkerInstance(workerUuid, true);
    }
}