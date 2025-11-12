package io.kestra.core.repositories;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.lock.Lock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public abstract class AbstractLockRepositoryTest {
    @Inject
    private LockRepositoryInterface lockRepository;

    @Test
    void findById() {
        var lock = Lock.builder().category("test").id("findById").owner("me").build();
        boolean created = lockRepository.create(lock);
        assertThat(created).isTrue();

        var existing = lockRepository.findById("test", "findById");
        assertThat(existing).isPresent();
        assertThat(existing.get().getCategory()).isEqualTo("test");
        assertThat(existing.get().getOwner()).isEqualTo("me");
        assertThat(existing.get().getId()).isEqualTo("findById");

        lockRepository.delete(lock);
    }

    @Test
    void create() {
        var lock = Lock.builder().category("test").id("create").owner("me").build();
        boolean created = lockRepository.create(lock);
        assertThat(created).isTrue();

        boolean ignored = lockRepository.create(lock);
        assertThat(ignored).isFalse();

        lockRepository.delete(lock);
    }

    @Test
    void delete() {
        var lock = Lock.builder().category("test").id("delete").owner("me").build();
        boolean created = lockRepository.create(lock);
        assertThat(created).isTrue();

        lockRepository.delete(lock);

        var existing = lockRepository.findById("test", "delete");
        assertThat(existing).isEmpty();
    }

    @Test
    void deleteById() {
        var lock = Lock.builder().category("test").id("deleteById").owner("me").build();
        boolean created = lockRepository.create(lock);
        assertThat(created).isTrue();

        lockRepository.deleteById("test", "deleteById");

        var existing = lockRepository.findById("test", "deleteById");
        assertThat(existing).isEmpty();
    }

    @RetryingTest(2) // In H2 only it fails the first time and succeed the second
    void deleteByOwner() throws InterruptedException {
        var lock = Lock.builder().category("test").id("deleteByOwner").owner("me").build();
        boolean created = lockRepository.create(lock);
        assertThat(created).isTrue();

        // In Elasticsearch, as we search then delete we need to wait for the refresh period
        // This would not be an issue in real-case scenario as when we delete by owner the service would be down for a certain amount of time
        Thread.sleep(1000);

        List<Lock> deleted = lockRepository.deleteByOwner("me");
        assertThat(deleted).hasSize(1);
        assertThat(deleted.getFirst().getOwner()).isEqualTo("me");
        assertThat(deleted.getFirst().getId()).isEqualTo("deleteByOwner");
        assertThat(deleted.getFirst().getCategory()).isEqualTo("test");

        var existing = lockRepository.findById("test", "deleteByOwner");
        assertThat(existing).isEmpty();
    }
}
